package com.xiyuan.ontology.modeling.storage.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.codec.SnapshotJsonCodec;
import com.xiyuan.ontology.modeling.storage.StorageSnapshots;
import com.xiyuan.ontology.modeling.storage.ModelStorage;
import com.xiyuan.ontology.modeling.storage.StorageContractTests;
import com.xiyuan.ontology.modeling.service.ModelingService;

import static org.junit.jupiter.api.Assertions.*;

class JsonStorageContractTests extends StorageContractTests {

	@Override
	protected ModelStorage open(Path directory) {
		JsonModelStorage storage = new JsonModelStorage(directory.toString(), WORKSPACE, new DefaultResourceLoader());
		storage.initialize();
		return storage;
	}

	@Override
	protected void close(ModelStorage storage) {
		((JsonModelStorage) storage).close();
	}

	@Test
	void failedPublishKeepsThePreviousCommitAndIgnoresTheOrphanOnRestart() throws Exception {
		close(this.storage);
		FaultyStorage faulty = new FaultyStorage(this.directory);
		faulty.initialize();
		this.storage = faulty;
		long before = version();
		String pointer = Files.readString(this.directory.resolve(".store/current.json"));
		faulty.fail = true;
		ModelDefinition object = object("OBJ-FAILED-PUBLISH");
		assertThrows(UncheckedIOException.class, () -> this.storage.write(WORKSPACE, session -> {
			session.objects().save(object);
			session.revisions().append(revision(object));
			session.audit().append(event(object.id()));
			return null;
		}));
		assertEquals(pointer, Files.readString(this.directory.resolve(".store/current.json")));
		assertEquals(before, version());
		reopen();
		assertTrue(this.storage.<Boolean>read(WORKSPACE, session -> session.objects().findById(object.id()).isEmpty()));
		assertTrue(this.storage.<Boolean>read(WORKSPACE, session -> session.revisions().findByElementKey(object.elementKey()).isEmpty()));
		assertTrue(this.storage.<Boolean>read(WORKSPACE, session -> session.audit().findAll().isEmpty()));
	}

	@Test
	void readableViewsAreRebuiltFromTheCommittedVersion() throws Exception {
		String original = Files.readString(this.directory.resolve("object-model.json"));
		close(this.storage);
		Files.writeString(this.directory.resolve("object-model.json"), "broken view");
		Files.writeString(this.directory.resolve("events.jsonl"), "uncommitted event");
		this.storage = open(this.directory);
		assertEquals(original, Files.readString(this.directory.resolve("object-model.json")));
		assertEquals("", Files.readString(this.directory.resolve("events.jsonl")));
	}

	@Test
	void aSecondProcessCannotOpenTheSameWorkspaceForWriting() {
		assertThrows(IllegalStateException.class, () -> open(this.directory));
		assertFalse(this.storage.<Boolean>read(WORKSPACE, session -> session.objects().findAll().isEmpty()));
	}

	@Test
	void damagedCommittedDataFailsStartupInsteadOfReseeding() throws Exception {
		close(this.storage);
		var pointer = this.mapper.readTree(Files.readAllBytes(this.directory.resolve(".store/current.json")));
		Path committedModel = this.directory.resolve(".store/commits").resolve(pointer.path("commitId").asString()).resolve("object-model.json");
		Files.writeString(committedModel, "{}");
		assertThrows(IllegalStateException.class, () -> open(this.directory));
	}

	@Test
	void definitionUpdatesPreserveUnknownFieldsNullsAndDecimalPrecision() {
		ModelDefinition object = object("OBJ-EXTENSIONS");
		var fields = new LinkedHashMap<>(object.properties());
		var extension = new LinkedHashMap<String, Object>();
		extension.put("future", null);
		extension.put("nested", List.of("hello", new BigDecimal("1234567890.1234567890123456789")));
		fields.put("futureFeature", extension);
		ModelDefinition extended = new ModelDefinition(object.elementKey(), object.kind(), object.id(), 1, fields);
		this.storage.write(WORKSPACE, session -> {
			session.objects().save(extended);
			return null;
		});
		reopen();
		ModelingService service = new ModelingService(this.storage, WORKSPACE);
		service.updateObjectType(object.id(), this.mapper.readTree("{\"name\":\"updated\"}"), 1);
		reopen();
		assertEquals(extension, this.storage.read(WORKSPACE, session -> session.objects().findById(object.id()).orElseThrow().properties().get("futureFeature")));
	}

	@Test
	void migrationSnapshotPreservesDeletedIdentityHistoryAndLayout() {
		ModelingService service = new ModelingService(this.storage, WORKSPACE);
		service.createRelation(this.mapper.readTree("{\"id\":\"REL-ARCHIVE\",\"name\":\"test\",\"sourceType\":\"OBJ-APPLICATION\",\"targetType\":\"OBJ-SERVICE\"}"));
		UUID key = this.storage.read(WORKSPACE, session -> session.relations().findById("REL-ARCHIVE").orElseThrow().elementKey());
		service.deleteRelation("REL-ARCHIVE");
		var snapshot = StorageSnapshots.capture(this.storage, WORKSPACE);
		var codec = new SnapshotJsonCodec();
		var decoded = codec.decode(codec.encode(snapshot));
		assertEquals(snapshot, decoded);
		assertTrue(decoded.identities().stream().anyMatch(identity -> identity.elementKey().equals(key) && identity.deleted()));
		assertEquals(2, decoded.revisions().stream().filter(record -> record.elementKey().equals(key)).count());
		assertFalse(decoded.layout().positions().isEmpty());
	}

	@Test
	void layoutStaysOutOfTheDefinitionFileButAppearsInApiExports() throws Exception {
		ModelingService service = new ModelingService(this.storage, WORKSPACE);
		int revision = service.graph(null).path("layoutRevision").asInt();
		service.saveLayout(revision, this.mapper.readTree("{\"OBJ-APPLICATION\":{\"x\":999,\"y\":42}}"));
		service.updateObjectType("OBJ-APPLICATION", this.mapper.readTree("{\"name\":\"updated\"}"), 1);
		var document = this.mapper.readTree(Files.readAllBytes(this.directory.resolve("object-model.json")));
		for (var object : document.path("objects")) {
			assertFalse(object.has("graph"));
		}
		assertEquals(999, service.getObjectType("OBJ-APPLICATION").path("graph").path("x").asInt());
		assertTrue(service.exportModel().path("objects").get(0).has("graph"));
	}

	@Test
	void legacyJsonAndHistoryAreImportedOnceWithPersistedKeys() throws Exception {
		Path legacy = this.directory.resolve("legacy-workspace");
		Files.createDirectories(legacy.resolve("revisions"));
		var loader = new DefaultResourceLoader();
		Files.write(legacy.resolve("object-model.json"), loader.getResource("classpath:seed/object-model.json").getContentAsByteArray());
		Files.writeString(legacy.resolve("layout.json"), "{\"layoutRevision\":7,\"positions\":{\"OBJ-APPLICATION\":{\"x\":55,\"y\":66}}}");
		Files.writeString(legacy.resolve("events.jsonl"), "{\"eventId\":\"EVT-LEGACY\",\"eventType\":\"OLD\",\"elementId\":\"OBJ-APPLICATION\",\"occurredAt\":\"2026-10-10T00:00:00Z\",\"extra\":null}\n");
		var snapshot = new ModelingService(this.storage, WORKSPACE).getObjectType("OBJ-APPLICATION");
		var record = this.mapper.createObjectNode();
		record.put("revision", 1);
		record.put("changeType", "CREATE");
		record.put("changeReason", "legacy");
		record.put("changedBy", "legacy");
		record.put("changedAt", "2026-10-10T00:00:00Z");
		record.set("snapshot", snapshot);
		Files.writeString(legacy.resolve("revisions/OBJ-APPLICATION.jsonl"), this.mapper.writeValueAsString(record) + "\n");
		ModelStorage imported = open(legacy);
		UUID key;
		try {
			key = imported.read(WORKSPACE, session -> session.objects().findById("OBJ-APPLICATION").orElseThrow().elementKey());
			assertEquals(7, imported.<Integer>read(WORKSPACE, session -> session.layout().get().revision()).intValue());
			assertEquals(55, new ModelingService(imported, WORKSPACE).getObjectType("OBJ-APPLICATION").path("graph").path("x").asInt());
			assertFalse(Files.exists(legacy.resolve("revisions/OBJ-APPLICATION.jsonl")));
			assertTrue(Files.exists(legacy.resolve("revisions/" + key + ".jsonl")));
		}
		finally {
			close(imported);
		}
		imported = open(legacy);
		try {
			assertEquals(key, imported.read(WORKSPACE, session -> session.objects().findById("OBJ-APPLICATION").orElseThrow().elementKey()));
			assertEquals(1, imported.<Integer>read(WORKSPACE, session -> session.revisions().findByElementKey(key).size()).intValue());
			assertEquals(1, imported.<Integer>read(WORKSPACE, session -> session.audit().findAll().size()).intValue());
		}
		finally {
			close(imported);
		}
	}

	private static final class FaultyStorage extends JsonModelStorage {

		boolean fail;

		FaultyStorage(Path directory) {
			super(directory.toString(), WORKSPACE, new DefaultResourceLoader());
		}

		@Override
		protected void beforePublish(Path commitDirectory) throws IOException {
			if (this.fail) {
				throw new IOException("Injected failure before publishing commit");
			}
		}

	}

}
