package com.xiyuan.ontology.modeling.storage.json;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.xiyuan.ontology.modeling.model.AuditEvent;
import com.xiyuan.ontology.modeling.model.ElementIdentity;
import com.xiyuan.ontology.modeling.model.LayoutState;
import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;
import com.xiyuan.ontology.modeling.model.ModelValues;
import com.xiyuan.ontology.modeling.model.RevisionRecord;
import com.xiyuan.ontology.modeling.model.WorkspaceMetadata;
import com.xiyuan.ontology.modeling.storage.AuditLog;
import com.xiyuan.ontology.modeling.storage.DefinitionRepository;
import com.xiyuan.ontology.modeling.storage.LayoutRepository;
import com.xiyuan.ontology.modeling.storage.ModelWriteSession;
import com.xiyuan.ontology.modeling.storage.RevisionRepository;
import com.xiyuan.ontology.modeling.storage.StorageRevisionConflict;

final class JsonModelSession implements ModelWriteSession, AutoCloseable {

	private final JsonWorkspaceState state;

	private final boolean writable;

	private boolean open = true;

	private final Map<Kind, DefinitionRepository.Writable> repositories = new EnumMap<>(Kind.class);

	JsonModelSession(JsonWorkspaceState state, boolean writable) {
		this.state = state;
		this.writable = writable;
		for (Kind kind : Kind.values()) {
			this.repositories.put(kind, new Definitions(kind));
		}
	}

	@Override
	public WorkspaceMetadata metadata() {
		checkOpen();
		return this.state.metadata;
	}

	@Override
	public List<ElementIdentity> identities() {
		checkOpen();
		return List.copyOf(this.state.identities.values());
	}

	@Override
	public DefinitionRepository.Writable objects() {
		checkOpen();
		return this.repositories.get(Kind.OBJECT);
	}

	@Override
	public DefinitionRepository.Writable relations() {
		checkOpen();
		return this.repositories.get(Kind.RELATION);
	}

	@Override
	public DefinitionRepository.Writable domains() {
		checkOpen();
		return this.repositories.get(Kind.DOMAIN);
	}

	@Override
	public LayoutRepository.Writable layout() {
		checkOpen();
		return new LayoutRepository.Writable() {
			@Override
			public LayoutState get() {
				checkOpen();
				return state.layout;
			}

			@Override
			public void save(LayoutState layout, int expectedRevision) {
				checkWritable();
				if (state.layout.revision() != expectedRevision) {
					throw new StorageRevisionConflict(expectedRevision, state.layout.revision());
				}
				if (layout.revision() != expectedRevision + 1) {
					throw new IllegalArgumentException("Layout revision must increase by one");
				}
				state.layout = layout;
			}
		};
	}

	@Override
	public RevisionRepository.Writable revisions() {
		checkOpen();
		return new RevisionRepository.Writable() {
			@Override
			public List<RevisionRecord> findByElementKey(UUID key) {
				checkOpen();
				return state.revisions.stream().filter(record -> record.elementKey().equals(key)).toList();
			}

			@Override
			public void append(RevisionRecord revision) {
				checkWritable();
				if (!state.identities.containsKey(revision.elementKey())) {
					throw new IllegalArgumentException("Revision references an unknown element key");
				}
				state.revisions.add(revision);
			}
		};
	}

	@Override
	public AuditLog.Writable audit() {
		checkOpen();
		return new AuditLog.Writable() {
			@Override
			public List<AuditEvent> findAll() {
				checkOpen();
				return List.copyOf(state.events);
			}

			@Override
			public void append(AuditEvent event) {
				checkWritable();
				state.events.add(event);
			}
		};
	}

	@Override
	public Map<String, Object> reviewFlags() {
		checkOpen();
		return this.state.reviewFlags;
	}

	@Override
	public void saveReviewFlags(Map<String, Object> flags) {
		checkWritable();
		this.state.reviewFlags = ModelValues.freezeMap(flags);
	}

	@Override
	public void close() {
		this.open = false;
	}

	private void checkOpen() {
		if (!this.open) {
			throw new IllegalStateException("Storage session is closed");
		}
	}

	private void checkWritable() {
		checkOpen();
		if (!this.writable) {
			throw new IllegalStateException("Read session cannot mutate storage");
		}
	}

	private final class Definitions implements DefinitionRepository.Writable {

		private final Kind kind;

		private final Map<String, UUID> byId = new LinkedHashMap<>();

		Definitions(Kind kind) {
			this.kind = kind;
			state.definitions.get(kind).values().forEach(element -> this.byId.put(element.id(), element.elementKey()));
		}

		@Override
		public Optional<ModelDefinition> findById(String id) {
			checkOpen();
			return Optional.ofNullable(state.definitions.get(this.kind).get(this.byId.get(id)));
		}

		@Override
		public List<ModelDefinition> findAll() {
			checkOpen();
			return List.copyOf(state.definitions.get(this.kind).values());
		}

		@Override
		public List<ModelDefinition> findByDomain(String domainId) {
			checkOpen();
			return state.definitions.get(this.kind).values().stream()
				.filter(element -> domainId.equals(element.domainId())).toList();
		}

		@Override
		public void save(ModelDefinition definition) {
			checkWritable();
			if (definition.kind() != this.kind) {
				throw new IllegalArgumentException("Definition kind does not match repository");
			}
			UUID key = definition.elementKey();
			ElementIdentity identity = state.identities.get(key);
			if (identity != null && (identity.kind() != this.kind || identity.deleted())) {
				throw new IllegalArgumentException("Element key cannot change kind or revive a deleted element");
			}
			UUID duplicate = this.byId.get(definition.id());
			if (duplicate != null && !duplicate.equals(key)) {
				throw new IllegalArgumentException("Duplicate definition id: " + definition.id());
			}
			ModelDefinition previous = state.definitions.get(this.kind).put(key, definition);
			if (previous != null) {
				this.byId.remove(previous.id());
			}
			this.byId.put(definition.id(), key);
			state.identities.put(key, new ElementIdentity(key, this.kind, definition.id(), false));
		}

		@Override
		public void delete(UUID key) {
			checkWritable();
			ModelDefinition removed = state.definitions.get(this.kind).remove(key);
			if (removed == null) {
				throw new IllegalArgumentException("Definition does not exist: " + key);
			}
			this.byId.remove(removed.id());
			state.identities.put(key, new ElementIdentity(key, this.kind, removed.id(), true));
		}

	}

}
