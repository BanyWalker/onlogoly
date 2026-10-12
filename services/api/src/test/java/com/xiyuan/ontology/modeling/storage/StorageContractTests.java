package com.xiyuan.ontology.modeling.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.xiyuan.ontology.modeling.api.ApiException;
import com.xiyuan.ontology.modeling.model.AuditEvent;
import com.xiyuan.ontology.modeling.model.LayoutState;
import com.xiyuan.ontology.modeling.model.LayoutState.Position;
import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;
import com.xiyuan.ontology.modeling.model.RevisionRecord;
import com.xiyuan.ontology.modeling.service.ModelingService;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

public abstract class StorageContractTests {

	protected static final String WORKSPACE = "ENV-OM-001";

	@TempDir
	protected Path directory;

	protected ModelStorage storage;

	protected final JsonMapper mapper = JsonMapper.builder().build();

	protected abstract ModelStorage open(Path directory);

	protected abstract void close(ModelStorage storage);

	@BeforeEach
	void initializeStorage() {
		this.storage = open(this.directory);
	}

	@AfterEach
	void closeStorage() {
		close(this.storage);
	}

	protected void reopen() {
		close(this.storage);
		this.storage = open(this.directory);
	}

	@Test
	void callbackFailureRollsBackDefinitionsLayoutRevisionsAuditAndReviewFlags() {
		long before = version();
		int layoutRevision = this.storage.read(WORKSPACE, session -> session.layout().get().revision());
		ModelDefinition object = object("OBJ-ROLLBACK");
		assertThrows(IllegalArgumentException.class, () -> this.storage.write(WORKSPACE, session -> {
			session.objects().save(object);
			LayoutState layout = session.layout().get();
			Map<UUID, Position> positions = new LinkedHashMap<>(layout.positions());
			positions.put(object.elementKey(), new Position(91, 23, Map.of()));
			session.layout().save(new LayoutState(layout.revision() + 1, positions, layout.properties()), layout.revision());
			session.revisions().append(revision(object));
			session.audit().append(event(object.id()));
			session.saveReviewFlags(Map.of("flags", List.of("changed")));
			throw new IllegalArgumentException("Rejected command");
		}));
		reopen();
		assertEquals(before, version());
		this.storage.read(WORKSPACE, session -> {
			assertTrue(session.objects().findById(object.id()).isEmpty());
			assertTrue(session.revisions().findByElementKey(object.elementKey()).isEmpty());
			assertTrue(session.audit().findAll().isEmpty());
			assertEquals(layoutRevision, session.layout().get().revision());
			assertEquals(List.of(), session.reviewFlags().get("flags"));
			return null;
		});
	}

	@Test
	void successfulTransactionPersistsAllRepositoriesAndStableKeys() {
		ModelDefinition object = object("OBJ-COMMIT");
		long before = version();
		this.storage.write(WORKSPACE, session -> {
			session.objects().save(object);
			LayoutState layout = session.layout().get();
			Map<UUID, Position> positions = new LinkedHashMap<>(layout.positions());
			positions.put(object.elementKey(), new Position(5.5, 8, Map.of("pinned", true)));
			session.layout().save(new LayoutState(layout.revision() + 1, positions, layout.properties()), layout.revision());
			session.revisions().append(revision(object));
			session.audit().append(event(object.id()));
			session.saveReviewFlags(Map.of("flags", List.of("review")));
			return null;
		});
		reopen();
		assertEquals(before + 1, version());
		this.storage.read(WORKSPACE, session -> {
			assertEquals(object, session.objects().findById(object.id()).orElseThrow());
			assertEquals(5.5, session.layout().get().positions().get(object.elementKey()).x());
			assertEquals(1, session.revisions().findByElementKey(object.elementKey()).size());
			assertEquals(object.id(), session.audit().findAll().getFirst().elementId());
			assertEquals(List.of("review"), session.reviewFlags().get("flags"));
			assertTrue(session.objects().findByDomain("DOMAIN-OPS").contains(object));
			return null;
		});
	}

	@Test
	void integrityFailureCannotLeaveHistoryOrDefinitionsBehind() {
		long before = version();
		ModelDefinition invalid = new ModelDefinition(UUID.randomUUID(), Kind.RELATION, "REL-INVALID", 1,
				Map.of("domainId", "DOMAIN-OPS", "sourceType", "OBJ-MISSING", "targetType", "OBJ-SERVICE"));
		assertThrows(IllegalStateException.class, () -> this.storage.write(WORKSPACE, session -> {
			session.relations().save(invalid);
			session.revisions().append(revision(invalid));
			session.audit().append(event(invalid.id()));
			return null;
		}));
		reopen();
		assertEquals(before, version());
		assertTrue(this.storage.<Boolean>read(WORKSPACE, session -> session.relations().findById(invalid.id()).isEmpty()));
		assertTrue(this.storage.<Boolean>read(WORKSPACE, session -> session.audit().findAll().isEmpty()));
	}

	@Test
	void layoutOptimisticLockRejectsStaleWritesAndDoesNotReviseDefinitions() {
		ModelingService service = new ModelingService(this.storage, WORKSPACE);
		int layoutRevision = service.graph(null).path("layoutRevision").asInt();
		int objectRevision = service.getObjectType("OBJ-APPLICATION").path("revision").asInt();
		service.saveLayout(layoutRevision, this.mapper.readTree("{\"OBJ-APPLICATION\":{\"x\":42,\"y\":23}}"));
		long beforeFailure = version();
		ApiException conflict = assertThrows(ApiException.class,
				() -> service.saveLayout(layoutRevision, this.mapper.readTree("{\"OBJ-APPLICATION\":{\"x\":10,\"y\":10}}")));
		assertEquals("REVISION_CONFLICT", conflict.getCode());
		reopen();
		assertEquals(beforeFailure, version());
		ModelingService reloaded = new ModelingService(this.storage, WORKSPACE);
		assertEquals(objectRevision, reloaded.getObjectType("OBJ-APPLICATION").path("revision").asInt());
		assertEquals(42, reloaded.getObjectType("OBJ-APPLICATION").path("graph").path("x").asInt());
	}

	@Test
	void renameKeepsHistoryAndLayoutAndUpdatesReferencesInTheSameCommit() {
		ModelingService service = new ModelingService(this.storage, WORKSPACE);
		UUID key = this.storage.read(WORKSPACE, session -> session.objects().findById("OBJ-APPLICATION").orElseThrow().elementKey());
		Position position = this.storage.read(WORKSPACE, session -> session.layout().get().positions().get(key));
		int revision = service.getObjectType("OBJ-APPLICATION").path("revision").asInt();
		long before = version();
		service.updateObjectType("OBJ-APPLICATION", this.mapper.readTree("{\"id\":\"OBJ-RENAMED\"}"), revision);
		reopen();
		assertEquals(before + 1, version());
		this.storage.read(WORKSPACE, session -> {
			assertTrue(session.objects().findById("OBJ-APPLICATION").isEmpty());
			assertEquals(key, session.objects().findById("OBJ-RENAMED").orElseThrow().elementKey());
			assertEquals(position, session.layout().get().positions().get(key));
			assertEquals("OBJ-RENAMED", session.revisions().findByElementKey(key).getLast().elementId());
			assertTrue(session.relations().findAll().stream().anyMatch(relation -> "OBJ-RENAMED".equals(relation.properties().get("sourceType"))));
			assertTrue(session.relations().findAll().stream().noneMatch(relation -> "OBJ-APPLICATION".equals(relation.properties().get("sourceType"))));
			return null;
		});
		assertEquals(revision + 1, new ModelingService(this.storage, WORKSPACE).getObjectType("OBJ-RENAMED").path("revision").asInt());
	}

	@Test
	void deleteAndRecreateUsesANewKeyWithoutMergingHistories() {
		ModelingService service = new ModelingService(this.storage, WORKSPACE);
		var body = this.mapper.readTree("{\"id\":\"REL-REUSE\",\"name\":\"test\",\"sourceType\":\"OBJ-APPLICATION\",\"targetType\":\"OBJ-SERVICE\"}");
		service.createRelation(body);
		UUID originalKey = this.storage.read(WORKSPACE, session -> session.relations().findById("REL-REUSE").orElseThrow().elementKey());
		service.deleteRelation("REL-REUSE");
		service.createRelation(body);
		reopen();
		this.storage.read(WORKSPACE, session -> {
			UUID newKey = session.relations().findById("REL-REUSE").orElseThrow().elementKey();
			assertNotEquals(originalKey, newKey);
			assertEquals(2, session.revisions().findByElementKey(originalKey).size());
			assertEquals(1, session.revisions().findByElementKey(newKey).size());
			return null;
		});
	}

	@Test
	void readSessionsAndDefinitionsAreImmutableAndCannotEscapeTheirLifetime() {
		DefinitionRepository repository = this.storage.read(WORKSPACE, session -> {
			ModelDefinition object = session.objects().findById("OBJ-APPLICATION").orElseThrow();
			assertThrows(UnsupportedOperationException.class, () -> object.properties().put("name", "changed"));
			if (session instanceof ModelWriteSession writer) {
				assertThrows(IllegalStateException.class, () -> writer.objects().save(object));
			}
			return session.objects();
		});
		assertThrows(IllegalStateException.class, () -> repository.findAll());
	}

	@Test
	void concurrentCommandsWithTheSameRevisionProduceOneSuccess() throws Exception {
		ModelingService service = new ModelingService(this.storage, WORKSPACE);
		int revision = service.getObjectType("OBJ-APPLICATION").path("revision").asInt();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var command = (java.util.concurrent.Callable<Boolean>) () -> {
				assertTrue(start.await(5, TimeUnit.SECONDS));
				try {
					service.updateObjectType("OBJ-APPLICATION", this.mapper.readTree("{\"name\":\"concurrent\"}"), revision);
					return true;
				}
				catch (ApiException exception) {
					assertEquals("REVISION_CONFLICT", exception.getCode());
					return false;
				}
			};
			var first = executor.submit(command);
			var second = executor.submit(command);
			start.countDown();
			assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		}
		assertEquals(revision + 1, service.getObjectType("OBJ-APPLICATION").path("revision").asInt());
	}

	protected long version() {
		return this.storage.read(WORKSPACE, session -> session.metadata().commitVersion());
	}

	protected ModelDefinition object(String id) {
		return new ModelDefinition(UUID.randomUUID(), Kind.OBJECT, id, 1,
				Map.of("name", "Test", "definition", "Test definition", "domainId", "DOMAIN-OPS", "attributes", List.of()));
	}

	protected RevisionRecord revision(ModelDefinition definition) {
		return new RevisionRecord(definition.elementKey(), definition.id(), definition.revision(), "CREATE", "test", "test",
				Instant.now(), Map.of("id", definition.id()), Map.of());
	}

	protected AuditEvent event(String id) {
		return new AuditEvent("EVT-" + UUID.randomUUID(), "TEST", id, Instant.now(), Map.of());
	}

}
