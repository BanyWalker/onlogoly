package com.xiyuan.ontology.modeling.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.xiyuan.ontology.modeling.codec.ModelJsonCodec;
import com.xiyuan.ontology.modeling.model.LayoutState;
import com.xiyuan.ontology.modeling.model.LayoutState.Position;
import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;
import com.xiyuan.ontology.modeling.model.RevisionRecord;
import com.xiyuan.ontology.modeling.storage.DefinitionRepository;
import com.xiyuan.ontology.modeling.storage.ModelReadSession;
import com.xiyuan.ontology.modeling.storage.ModelWriteSession;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class ModelDocument {

	private record Entry(UUID key, Kind kind, ModelDefinition original, ObjectNode node) {
	}

	private final ModelReadSession session;

	private final ModelJsonCodec codec;

	private final Map<Kind, LinkedHashMap<UUID, Entry>> cache = new EnumMap<>(Kind.class);

	private final Map<ObjectNode, Entry> entries = new IdentityHashMap<>();

	private final Set<UUID> removed = new LinkedHashSet<>();

	private final List<RevisionRecord> revisions = new ArrayList<>();

	ModelDocument(ModelReadSession session, ModelJsonCodec codec) {
		this.session = session;
		this.codec = codec;
		for (Kind kind : Kind.values()) {
			this.cache.put(kind, new LinkedHashMap<>());
		}
	}

	int layoutRevision() {
		return this.session.layout().get().revision();
	}

	List<JsonNode> objects() {
		return all(Kind.OBJECT);
	}

	List<JsonNode> relations() {
		return all(Kind.RELATION);
	}

	List<JsonNode> domains() {
		return all(Kind.DOMAIN);
	}

	ObjectNode findObject(String id) {
		return find(Kind.OBJECT, id);
	}

	ObjectNode findRelation(String id) {
		return find(Kind.RELATION, id);
	}

	ObjectNode findDomain(String id) {
		return find(Kind.DOMAIN, id);
	}

	void addObject(ObjectNode object) {
		add(Kind.OBJECT, object);
	}

	void addRelation(ObjectNode relation) {
		add(Kind.RELATION, relation);
	}

	void addDomain(ObjectNode domain) {
		add(Kind.DOMAIN, domain);
	}

	void removeRelation(String id) {
		this.removed.add(this.entries.get(findRelation(id)).key());
	}

	void removeDomain(String id) {
		this.removed.add(this.entries.get(findDomain(id)).key());
	}

	ObjectNode export() {
		ObjectNode result = this.codec.json(this.session.metadata().properties());
		for (Kind kind : Kind.values()) {
			ArrayNode array = result.putArray(switch (kind) {
				case OBJECT -> "objects";
				case RELATION -> "relations";
				case DOMAIN -> "domains";
			});
			all(kind).forEach(node -> array.add(node.deepCopy()));
		}
		return result;
	}

	void appendRevision(String id, String changeType, String reason, String actor, ObjectNode snapshot) {
		Entry entry = this.entries.get(snapshot);
		if (entry == null) {
			throw new IllegalStateException("Revision snapshot does not belong to this session");
		}
		ObjectNode definition = snapshot.deepCopy();
		if (entry.kind() == Kind.OBJECT) {
			definition.remove("graph");
		}
		this.revisions.add(new RevisionRecord(entry.key(), id, snapshot.path("revision").asInt(1), changeType,
				reason == null ? "" : reason, actor, Instant.now(), this.codec.properties(definition), Map.of()));
	}

	void flush() {
		ModelWriteSession writer = (ModelWriteSession) this.session;
		LayoutState layout = this.session.layout().get();
		Map<UUID, Position> positions = new LinkedHashMap<>(layout.positions());
		boolean renamedObject = false;
		for (Map<UUID, Entry> elements : this.cache.values()) {
			for (Entry entry : elements.values()) {
				if (this.removed.contains(entry.key())) {
					writable(entry.kind()).delete(entry.key());
					positions.remove(entry.key());
					continue;
				}
				ModelDefinition updated = this.codec.definition(entry.key(), entry.kind(), entry.node());
				if (!updated.equals(entry.original())) {
					writable(entry.kind()).save(updated);
				}
				if (entry.kind() == Kind.OBJECT) {
					renamedObject |= entry.original() != null && !updated.id().equals(entry.original().id());
					JsonNode graph = entry.node().path("graph");
					if (graph.isObject()) {
						Map<String, Object> properties = this.codec.properties(graph);
						properties.remove("x");
						properties.remove("y");
						positions.put(entry.key(), new Position(graph.path("x").asDouble(), graph.path("y").asDouble(), properties));
					}
				}
			}
		}
		if (renamedObject || !positions.equals(layout.positions())) {
			writer.layout().save(new LayoutState(layout.revision() + 1, positions, layout.properties()), layout.revision());
		}
		this.revisions.forEach(writer.revisions()::append);
	}

	private ObjectNode find(Kind kind, String id) {
		for (Entry entry : this.cache.get(kind).values()) {
			if (!this.removed.contains(entry.key()) && id.equals(id(entry))) {
				return entry.node();
			}
		}
		return repository(kind).findById(id).map(definition -> {
			Entry entry = load(definition);
			return !this.removed.contains(entry.key()) && id.equals(id(entry)) ? entry.node() : null;
		}).orElse(null);
	}

	private List<JsonNode> all(Kind kind) {
		repository(kind).findAll().forEach(this::load);
		return this.cache.get(kind).values().stream().filter(entry -> !this.removed.contains(entry.key()))
			.<JsonNode>map(Entry::node).toList();
	}

	private Entry load(ModelDefinition definition) {
		Entry cached = this.cache.get(definition.kind()).get(definition.elementKey());
		if (cached != null) {
			return cached;
		}
		ObjectNode node = this.codec.json(definition);
		if (definition.kind() == Kind.OBJECT) {
			Position position = this.session.layout().get().positions().get(definition.elementKey());
			if (position != null) {
				ObjectNode graph = this.codec.json(position.properties());
				graph.put("x", position.x());
				graph.put("y", position.y());
				node.set("graph", graph);
			}
		}
		Entry entry = new Entry(definition.elementKey(), definition.kind(), definition, node);
		this.cache.get(definition.kind()).put(entry.key(), entry);
		this.entries.put(node, entry);
		return entry;
	}

	private void add(Kind kind, ObjectNode node) {
		Entry entry = new Entry(UUID.randomUUID(), kind, null, node);
		this.cache.get(kind).put(entry.key(), entry);
		this.entries.put(node, entry);
	}

	private String id(Entry entry) {
		return entry.node().path(entry.kind() == Kind.DOMAIN ? "domainId" : "id").asString();
	}

	private DefinitionRepository repository(Kind kind) {
		return switch (kind) {
			case OBJECT -> this.session.objects();
			case RELATION -> this.session.relations();
			case DOMAIN -> this.session.domains();
		};
	}

	private DefinitionRepository.Writable writable(Kind kind) {
		return (DefinitionRepository.Writable) repository(kind);
	}

}
