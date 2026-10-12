package com.xiyuan.ontology.modeling.storage.json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.xiyuan.ontology.modeling.codec.ModelJsonCodec;
import com.xiyuan.ontology.modeling.model.AuditEvent;
import com.xiyuan.ontology.modeling.model.ElementIdentity;
import com.xiyuan.ontology.modeling.model.LayoutState;
import com.xiyuan.ontology.modeling.model.LayoutState.Position;
import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;
import com.xiyuan.ontology.modeling.model.ModelValues;
import com.xiyuan.ontology.modeling.model.RevisionRecord;
import com.xiyuan.ontology.modeling.model.WorkspaceMetadata;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

final class JsonWorkspaceCodec {

	static final int SCHEMA_VERSION = 1;

	private final JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).findAndAddModules().build();

	private final ModelJsonCodec models = new ModelJsonCodec();

	ObjectNode readObject(Path file) throws IOException {
		JsonNode node = this.mapper.readTree(Files.readAllBytes(file));
		if (!(node instanceof ObjectNode object)) {
			throw new IllegalStateException("Expected a JSON object: " + file);
		}
		return object;
	}

	byte[] bytes(JsonNode node) {
		return this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node).getBytes(StandardCharsets.UTF_8);
	}

	JsonWorkspaceState load(Path directory, String workspaceId, long version) throws IOException {
		ObjectNode document = readObject(directory.resolve("object-model.json"));
		if (!"object-model".equals(document.path("kind").asString())
				|| !workspaceId.equals(document.path("workspace").path("id").asString())) {
			throw new IllegalStateException("Invalid model kind or workspace identity: " + directory);
		}
		JsonWorkspaceState state = new JsonWorkspaceState();
		Map<String, Object> metadata = this.models.properties(document);
		metadata.remove("objects");
		metadata.remove("relations");
		metadata.remove("domains");
		state.metadata = new WorkspaceMetadata(workspaceId, version, SCHEMA_VERSION, metadata);
		Path registryPath = directory.resolve("elements.json");
		if (Files.exists(registryPath)) {
			ObjectNode registry = readObject(registryPath);
			if (registry.path("storageSchemaVersion").asInt() != SCHEMA_VERSION) {
				throw new IllegalStateException("Unsupported storage schema version: " + registryPath);
			}
			for (JsonNode identity : registry.path("elements")) {
				UUID key = UUID.fromString(identity.path("elementKey").asString());
				if (state.identities.put(key, new ElementIdentity(key,
						Kind.valueOf(identity.path("kind").asString()), identity.path("id").asString(),
						identity.path("deleted").asBoolean())) != null) {
					throw new IllegalStateException("Duplicate persisted element key: " + key);
				}
			}
		}
		Map<UUID, Position> positions = new LinkedHashMap<>();
		for (Kind kind : Kind.values()) {
			String field = field(kind);
			if (!document.path(field).isArray()) {
				throw new IllegalStateException("Missing model array: " + field);
			}
			for (JsonNode item : document.path(field)) {
				String id = item.path(kind == Kind.DOMAIN ? "domainId" : "id").asString();
				UUID key = keyFor(state, kind, id, false, Files.exists(registryPath));
				ModelDefinition definition = this.models.definition(key, kind, (ObjectNode) item);
				if (state.definitions.get(kind).put(key, definition) != null) {
					throw new IllegalStateException("Duplicate model id: " + id);
				}
				if (kind == Kind.OBJECT && item.path("graph").isObject()) {
					positions.put(key, position((ObjectNode) item.path("graph")));
				}
			}
		}
		Path layoutPath = directory.resolve("layout.json");
		ObjectNode layout = Files.exists(layoutPath) ? readObject(layoutPath) : JsonNodeFactory.instance.objectNode();
		if (Files.exists(layoutPath) && (!layout.path("positions").isObject() || !layout.path("layoutRevision").isIntegralNumber())) {
			throw new IllegalStateException("Invalid layout: " + layoutPath);
		}
		layout.path("positions").properties().forEach(entry -> {
			UUID key = keyFor(state, Kind.OBJECT, entry.getKey(), false, true);
			positions.put(key, position((ObjectNode) entry.getValue()));
		});
		Map<String, Object> layoutProperties = this.models.properties(layout);
		layoutProperties.remove("positions");
		layoutProperties.remove("layoutRevision");
		state.layout = new LayoutState(layout.path("layoutRevision").asInt(1), positions, layoutProperties);
		Path flags = directory.resolve("review-flags.json");
		state.reviewFlags = Files.exists(flags) ? ModelValues.freezeMap(this.models.properties(readObject(flags)))
				: Map.of("flags", List.of());
		return state;
	}

	void loadLegacyHistory(JsonWorkspaceState state, Path directory) throws IOException {
		Path revisions = directory.resolve("revisions");
		if (Files.isDirectory(revisions)) {
			try (var files = Files.list(revisions)) {
				for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".jsonl")).sorted().toList()) {
					for (ObjectNode record : lines(file)) {
						ObjectNode snapshot = (ObjectNode) record.path("snapshot");
						Kind kind = snapshot.has("sourceType") ? Kind.RELATION : snapshot.has("attributes") ? Kind.OBJECT : Kind.DOMAIN;
						String id = snapshot.path(kind == Kind.DOMAIN ? "domainId" : "id").asString();
						UUID key = keyFor(state, kind, id, true, false);
						state.revisions.add(revision(record, key, id));
					}
				}
			}
		}
		state.events.addAll(events(directory.resolve("events.jsonl")));
	}

	List<RevisionRecord> revisions(Path file) throws IOException {
		List<RevisionRecord> result = new ArrayList<>();
		for (ObjectNode record : lines(file)) {
			result.add(revision(record, UUID.fromString(record.path("elementKey").asString()), record.path("elementId").asString()));
		}
		return result;
	}

	List<AuditEvent> events(Path file) throws IOException {
		List<AuditEvent> result = new ArrayList<>();
		for (ObjectNode record : lines(file)) {
			Map<String, Object> properties = this.models.properties(record);
			for (String field : List.of("eventId", "eventType", "elementId", "occurredAt")) {
				properties.remove(field);
			}
			result.add(new AuditEvent(record.path("eventId").asString(), record.path("eventType").asString(),
					record.path("elementId").asString(), Instant.parse(record.path("occurredAt").asString()), properties));
		}
		return result;
	}

	private RevisionRecord revision(ObjectNode record, UUID key, String id) {
		Map<String, Object> properties = this.models.properties(record);
		for (String field : List.of("elementKey", "elementId", "revision", "changeType", "changeReason", "changedBy", "changedAt", "snapshot")) {
			properties.remove(field);
		}
		return new RevisionRecord(key, id, record.path("revision").asInt(1), record.path("changeType").asString(),
				record.path("changeReason").asString(""), record.path("changedBy").asString(""),
				Instant.parse(record.path("changedAt").asString()), this.models.properties(record.path("snapshot")), properties);
	}

	ObjectNode document(JsonWorkspaceState state) {
		ObjectNode result = this.models.json(state.metadata.properties());
		for (Kind kind : Kind.values()) {
			ArrayNode definitions = result.putArray(field(kind));
			state.definitions.get(kind).values().forEach(definition -> definitions.add(this.models.json(definition)));
		}
		return result;
	}

	ObjectNode layout(JsonWorkspaceState state) {
		ObjectNode result = this.models.json(state.layout.properties());
		result.put("layoutRevision", state.layout.revision());
		ObjectNode positions = result.putObject("positions");
		state.layout.positions().forEach((key, position) -> {
			ModelDefinition object = state.definitions.get(Kind.OBJECT).get(key);
			ObjectNode node = this.models.json(position.properties());
			node.put("x", position.x());
			node.put("y", position.y());
			positions.set(object.id(), node);
		});
		return result;
	}

	ObjectNode registry(JsonWorkspaceState state) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("storageSchemaVersion", SCHEMA_VERSION);
		ArrayNode identities = result.putArray("elements");
		state.identities.values().forEach(identity -> {
			ObjectNode node = identities.addObject();
			node.put("elementKey", identity.elementKey().toString());
			node.put("kind", identity.kind().name());
			node.put("id", identity.id());
			node.put("deleted", identity.deleted());
		});
		return result;
	}

	ObjectNode json(RevisionRecord record) {
		ObjectNode node = this.models.json(record.properties());
		node.put("elementKey", record.elementKey().toString());
		node.put("elementId", record.elementId());
		node.put("revision", record.revision());
		node.put("changeType", record.changeType());
		node.put("changeReason", record.changeReason());
		node.put("changedBy", record.changedBy());
		node.put("changedAt", record.changedAt().toString());
		node.set("snapshot", this.models.json(record.snapshot()));
		return node;
	}

	ObjectNode json(AuditEvent event) {
		ObjectNode node = this.models.json(event.properties());
		node.put("eventId", event.eventId());
		node.put("eventType", event.eventType());
		node.put("elementId", event.elementId());
		node.put("occurredAt", event.occurredAt().toString());
		return node;
	}

	byte[] jsonLines(List<? extends Object> records) {
		StringBuilder content = new StringBuilder();
		for (Object record : records) {
			ObjectNode node = record instanceof RevisionRecord revision ? json(revision) : json((AuditEvent) record);
			content.append(this.mapper.writeValueAsString(node)).append('\n');
		}
		return content.toString().getBytes(StandardCharsets.UTF_8);
	}

	ObjectNode flags(JsonWorkspaceState state) {
		return this.models.json(state.reviewFlags);
	}

	private List<ObjectNode> lines(Path file) throws IOException {
		List<ObjectNode> records = new ArrayList<>();
		if (Files.exists(file)) {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (!line.isBlank()) {
					JsonNode node = this.mapper.readTree(line);
					if (!(node instanceof ObjectNode object)) {
						throw new IllegalStateException("Invalid JSONL record: " + file);
					}
					records.add(object);
				}
			}
		}
		return records;
	}

	private static UUID keyFor(JsonWorkspaceState state, Kind kind, String id, boolean deleted, boolean required) {
		for (ElementIdentity identity : state.identities.values()) {
			if (identity.kind() == kind && identity.id().equals(id) && (deleted || !identity.deleted())) {
				return identity.elementKey();
			}
		}
		if (required) {
			throw new IllegalStateException("Missing persisted element identity: " + kind + ":" + id);
		}
		UUID key = UUID.randomUUID();
		state.identities.put(key, new ElementIdentity(key, kind, id, deleted));
		return key;
	}

	private Position position(ObjectNode node) {
		if (!node.path("x").isNumber() || !node.path("y").isNumber()) {
			throw new IllegalStateException("Invalid layout coordinates");
		}
		Map<String, Object> properties = this.models.properties(node);
		properties.remove("x");
		properties.remove("y");
		return new Position(node.path("x").asDouble(), node.path("y").asDouble(), properties);
	}

	private static String field(Kind kind) {
		return switch (kind) {
			case OBJECT -> "objects";
			case RELATION -> "relations";
			case DOMAIN -> "domains";
		};
	}

}
