package com.xiyuan.ontology.objectfact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.xiyuan.ontology.objectfact.api.FactException;
import com.xiyuan.ontology.objectfact.storage.FactStorage;
import static org.junit.jupiter.api.Assertions.*;

class FactStorageTests {
	@TempDir Path directory;

	@Test void emptyWorkspaceRestartsWithoutMistakingItsViewForUncommittedData() {
		FactStorage first = new FactStorage(directory); assertEquals(0, first.read(state -> state.path("revision").asInt()).intValue()); first.close();
		FactStorage second = new FactStorage(directory); assertEquals(0, second.read(state -> state.path("instances").size()).intValue()); second.close();
	}

	@Test void factsAndLedgerSurviveRestartAsOneCommit() throws Exception {
		FactStorage storage = new FactStorage(directory);
		storage.write(null, state -> { state.withArray("records").addObject().put("recordKind", "IMPORT"); state.put("testValue", "committed"); return null; }); storage.close();
		FactStorage restarted = new FactStorage(directory);
		assertEquals("committed", restarted.read(state -> state.path("testValue").asString())); assertEquals(1, restarted.read(state -> state.path("records").size()).intValue());
		assertTrue(Files.readString(directory.resolve("fact-batches.jsonl")).contains("IMPORT")); restarted.close();
	}

	@Test void failedAtomicCommitChangesNeitherMemoryNorDiskNorLedger() {
		FaultyStorage storage = new FaultyStorage(directory); storage.fail = true;
		FactException error = assertThrows(FactException.class, () -> storage.write(null, state -> { state.withArray("records").addObject().put("recordKind", "IMPORT"); return null; }));
		assertEquals("OF_STORAGE_FAILED", error.code()); assertEquals(0, storage.read(state -> state.path("records").size()).intValue()); storage.close();
		FactStorage restarted = new FactStorage(directory); assertEquals(0, restarted.read(state -> state.path("records").size()).intValue()); restarted.close();
	}

	@Test void rejectedWriteCallbackDoesNotPublishAnything() {
		FactStorage storage = new FactStorage(directory);
		assertThrows(IllegalArgumentException.class, () -> storage.write(null, state -> { state.put("uncommitted", true); throw new IllegalArgumentException("validation rejected"); }));
		assertFalse(storage.read(state -> state.has("uncommitted")).booleanValue()); storage.close();
	}

	@Test void staleRevisionRejectsBeforeMutationAndReadSnapshotsCannotWrite() {
		FactStorage storage = new FactStorage(directory);
		storage.read(state -> { state.put("uncommitted", true); return null; }); assertFalse(storage.read(state -> state.has("uncommitted")).booleanValue());
		storage.write(0L, state -> null); assertEquals("OF_REVISION_CONFLICT", assertThrows(FactException.class, () -> storage.write(0L, state -> null)).code()); storage.close();
	}

	@Test void viewFailureCannotTurnACommittedOperationIntoAReportedFailure() throws Exception {
		FactStorage storage = new FactStorage(directory);
		Files.delete(directory.resolve("object-facts.json")); Files.createDirectory(directory.resolve("object-facts.json"));
		storage.write(null, state -> { state.put("committed", true); return null; }); assertTrue(storage.read(state -> state.path("committed").asBoolean()).booleanValue()); storage.close();
		Files.delete(directory.resolve("object-facts.json")); FactStorage restarted = new FactStorage(directory);
		assertTrue(restarted.read(state -> state.path("committed").asBoolean()).booleanValue()); assertTrue(Files.isRegularFile(directory.resolve("object-facts.json"))); restarted.close();
	}

	@Test void corruptAuthorityAndDuplicateProcessDisableOnlyThisStore() throws Exception {
		Files.createDirectories(directory.resolve(".facts-store")); Files.writeString(directory.resolve(".facts-store/current.json"), "broken");
		FactStorage broken = new FactStorage(directory); assertEquals(503, assertThrows(FactException.class, () -> broken.read(state -> state)).status()); broken.close();
		Path other = directory.resolve("other"); FactStorage first = new FactStorage(other), second = new FactStorage(other);
		assertEquals("OF_MODULE_DISABLED", assertThrows(FactException.class, () -> second.read(state -> state)).code()); assertDoesNotThrow(() -> first.read(state -> state)); second.close(); first.close();
	}

	private static class FaultyStorage extends FactStorage {
		boolean fail;
		FaultyStorage(Path directory) { super(directory); }
		@Override protected void atomicWrite(Path target, byte[] bytes) throws IOException {
			if (fail && target.getFileName().toString().equals("current.json")) throw new IOException("injected before publication");
			super.atomicWrite(target, bytes);
		}
	}
}
