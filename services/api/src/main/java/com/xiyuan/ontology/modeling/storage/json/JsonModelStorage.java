package com.xiyuan.ontology.modeling.storage.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.xiyuan.ontology.modeling.model.ModelIntegrity;
import com.xiyuan.ontology.modeling.model.ElementIdentity;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;
import com.xiyuan.ontology.modeling.model.RevisionRecord;
import com.xiyuan.ontology.modeling.model.WorkspaceMetadata;
import com.xiyuan.ontology.modeling.storage.ModelReadSession;
import com.xiyuan.ontology.modeling.storage.ModelStorage;
import com.xiyuan.ontology.modeling.storage.ModelWriteSession;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "xiyuan.storage.backend", havingValue = "json", matchIfMissing = true)
public class JsonModelStorage implements ModelStorage, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(JsonModelStorage.class);

	private final Path workspaceDir;

	private final String workspaceId;

	private final ResourceLoader resourceLoader;

	private final JsonWorkspaceCodec codec = new JsonWorkspaceCodec();

	private final ReentrantLock lock = new ReentrantLock();

	private final ThreadLocal<Boolean> sessionActive = ThreadLocal.withInitial(() -> false);

	private FileChannel lockChannel;

	private FileLock writerLock;

	private JsonWorkspaceState state;

	private String commitId;

	public JsonModelStorage(@Value("${xiyuan.workspace.dir}") String workspaceDir,
			@Value("${xiyuan.workspace.id:ENV-OM-001}") String workspaceId, ResourceLoader resourceLoader) {
		this.workspaceDir = Path.of(workspaceDir).toAbsolutePath().normalize();
		this.workspaceId = workspaceId;
		this.resourceLoader = resourceLoader;
	}

	@PostConstruct
	public void initialize() {
		this.lock.lock();
		try {
			Files.createDirectories(storeDir().resolve("commits"));
			this.lockChannel = FileChannel.open(storeDir().resolve("writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			this.writerLock = this.lockChannel.tryLock();
			if (this.writerLock == null) {
				throw new IllegalStateException("Workspace is already open by another writer: " + this.workspaceDir);
			}
			Path pointerPath = storeDir().resolve("current.json");
			if (Files.exists(pointerPath)) {
				ObjectNode pointer = this.codec.readObject(pointerPath);
				if (pointer.path("storageSchemaVersion").asInt() != JsonWorkspaceCodec.SCHEMA_VERSION) {
					throw new IllegalStateException("Unsupported storage schema version");
				}
				this.commitId = validCommitId(pointer.path("commitId").asString());
				List<Path> chain = committedChain(this.commitId, pointer.path("commitVersion").asLong());
				this.state = this.codec.load(commitDirectory(this.commitId), this.workspaceId, pointer.path("commitVersion").asLong());
				for (Path directory : chain) {
					this.state.revisions.addAll(this.codec.revisions(directory.resolve("revisions.jsonl")));
					this.state.events.addAll(this.codec.events(directory.resolve("events.jsonl")));
				}
			}
			else {
				Path main = this.workspaceDir.resolve("object-model.json");
				if (Files.notExists(main)) {
					if (Files.exists(this.workspaceDir.resolve("layout.json")) || Files.exists(this.workspaceDir.resolve("events.jsonl"))) {
						throw new IllegalStateException("Incomplete workspace; refusing to replace data with a seed");
					}
					var seed = this.resourceLoader.getResource("classpath:seed/object-model.json");
					writeAtomic(main, seed.getContentAsByteArray());
				}
				this.state = this.codec.load(this.workspaceDir, this.workspaceId, 0);
				this.codec.loadLegacyHistory(this.state, this.workspaceDir);
				validate(this.state);
				commit(this.state, 0, 0);
			}
			validate(this.state);
			refreshViews();
			log.info("JSON workspace loaded: {} (commit {}, objects {}, relations {})", this.workspaceDir,
					this.state.metadata.commitVersion(), this.state.definitions.get(Kind.OBJECT).size(),
					this.state.definitions.get(Kind.RELATION).size());
		}
		catch (IOException | RuntimeException exception) {
			close();
			throw new IllegalStateException("Workspace initialization failed: " + this.workspaceDir, exception);
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public <T> T read(String workspaceId, Function<ModelReadSession, T> action) {
		return execute(workspaceId, false, action::apply);
	}

	@Override
	public <T> T write(String workspaceId, Function<ModelWriteSession, T> action) {
		return execute(workspaceId, true, action);
	}

	private <T> T execute(String requestedWorkspace, boolean writable, Function<ModelWriteSession, T> action) {
		if (this.sessionActive.get()) {
			throw new IllegalStateException("Nested storage sessions are not supported");
		}
		this.lock.lock();
		try {
			if (!this.workspaceId.equals(requestedWorkspace) || this.state == null || this.writerLock == null) {
				throw new IllegalStateException("Workspace is unavailable: " + requestedWorkspace);
			}
			this.sessionActive.set(true);
			JsonWorkspaceState working = writable ? this.state.copy() : this.state;
			try (JsonModelSession session = new JsonModelSession(working, writable)) {
				T result = action.apply(session);
				if (writable) {
					validate(working);
					commit(working, this.state.revisions.size(), this.state.events.size());
				}
				return result;
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Workspace commit failed: " + this.workspaceDir, exception);
		}
		finally {
			this.sessionActive.remove();
			this.lock.unlock();
		}
	}

	private void commit(JsonWorkspaceState working, int revisionOffset, int eventOffset) throws IOException {
		long nextVersion = working.metadata.commitVersion() + 1;
		working.metadata = new WorkspaceMetadata(this.workspaceId, nextVersion, JsonWorkspaceCodec.SCHEMA_VERSION, working.metadata.properties());
		String nextCommit = UUID.randomUUID().toString();
		Path staging = storeDir().resolve("staging-" + nextCommit);
		Files.createDirectory(staging);
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put("object-model.json", this.codec.bytes(this.codec.document(working)));
		files.put("layout.json", this.codec.bytes(this.codec.layout(working)));
		files.put("elements.json", this.codec.bytes(this.codec.registry(working)));
		files.put("review-flags.json", this.codec.bytes(this.codec.flags(working)));
		files.put("revisions.jsonl", this.codec.jsonLines(working.revisions.subList(revisionOffset, working.revisions.size())));
		files.put("events.jsonl", this.codec.jsonLines(working.events.subList(eventOffset, working.events.size())));
		ObjectNode manifest = JsonNodeFactory.instance.objectNode();
		manifest.put("storageSchemaVersion", JsonWorkspaceCodec.SCHEMA_VERSION);
		manifest.put("workspaceId", this.workspaceId);
		manifest.put("commitId", nextCommit);
		manifest.put("commitVersion", nextVersion);
		if (this.commitId == null) {
			manifest.putNull("parentCommitId");
		}
		else {
			manifest.put("parentCommitId", this.commitId);
		}
		ObjectNode hashes = manifest.putObject("sha256");
		for (Map.Entry<String, byte[]> entry : files.entrySet()) {
			writeDurable(staging.resolve(entry.getKey()), entry.getValue());
			hashes.put(entry.getKey(), sha256(entry.getValue()));
		}
		writeDurable(staging.resolve("manifest.json"), this.codec.bytes(manifest));
		Files.move(staging, commitDirectory(nextCommit), StandardCopyOption.ATOMIC_MOVE);
		ObjectNode pointer = JsonNodeFactory.instance.objectNode();
		pointer.put("storageSchemaVersion", JsonWorkspaceCodec.SCHEMA_VERSION);
		pointer.put("commitId", nextCommit);
		pointer.put("commitVersion", nextVersion);
		beforePublish(commitDirectory(nextCommit));
		writeAtomic(storeDir().resolve("current.json"), this.codec.bytes(pointer));
		this.commitId = nextCommit;
		this.state = working;
		refreshViews();
	}

	protected void beforePublish(Path commitDirectory) throws IOException {
	}

	private List<Path> committedChain(String currentId, long currentVersion) throws IOException {
		List<Path> chain = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		String cursor = currentId;
		long expectedVersion = currentVersion;
		while (cursor != null) {
			if (!visited.add(cursor)) {
				throw new IllegalStateException("Cycle in workspace commit history");
			}
			Path directory = commitDirectory(cursor);
			ObjectNode manifest = this.codec.readObject(directory.resolve("manifest.json"));
			if (!cursor.equals(manifest.path("commitId").asString())
					|| !this.workspaceId.equals(manifest.path("workspaceId").asString())
					|| manifest.path("storageSchemaVersion").asInt() != JsonWorkspaceCodec.SCHEMA_VERSION
					|| manifest.path("commitVersion").asLong() != expectedVersion || expectedVersion < 1) {
				throw new IllegalStateException("Invalid workspace commit manifest: " + directory);
			}
			for (String file : List.of("object-model.json", "layout.json", "elements.json", "review-flags.json", "revisions.jsonl", "events.jsonl")) {
				if (!sha256(Files.readAllBytes(directory.resolve(file))).equals(manifest.path("sha256").path(file).asString())) {
					throw new IllegalStateException("Workspace checksum mismatch: " + directory.resolve(file));
				}
			}
			chain.add(directory);
			cursor = manifest.path("parentCommitId").isNull() ? null : validCommitId(manifest.path("parentCommitId").asString());
			expectedVersion--;
		}
		if (expectedVersion != 0) {
			throw new IllegalStateException("Incomplete workspace commit history");
		}
		Collections.reverse(chain);
		return chain;
	}

	private void refreshViews() {
		try {
			writeAtomic(this.workspaceDir.resolve("object-model.json"), this.codec.bytes(this.codec.document(this.state)));
			writeAtomic(this.workspaceDir.resolve("layout.json"), this.codec.bytes(this.codec.layout(this.state)));
			writeAtomic(this.workspaceDir.resolve("elements.json"), this.codec.bytes(this.codec.registry(this.state)));
			writeAtomic(this.workspaceDir.resolve("review-flags.json"), this.codec.bytes(this.codec.flags(this.state)));
			writeAtomic(this.workspaceDir.resolve("events.jsonl"), this.codec.jsonLines(this.state.events));
			Path revisions = this.workspaceDir.resolve("revisions");
			Files.createDirectories(revisions);
			Map<UUID, List<RevisionRecord>> grouped = new LinkedHashMap<>();
			this.state.revisions.forEach(record -> grouped.computeIfAbsent(record.elementKey(), key -> new ArrayList<>()).add(record));
			for (Map.Entry<UUID, List<RevisionRecord>> entry : grouped.entrySet()) {
				writeAtomic(revisions.resolve(entry.getKey() + ".jsonl"), this.codec.jsonLines(entry.getValue()));
			}
			Set<String> expectedFiles = new HashSet<>();
			grouped.keySet().forEach(key -> expectedFiles.add(key + ".jsonl"));
			try (var files = Files.list(revisions)) {
				for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList()) {
					if (!expectedFiles.contains(file.getFileName().toString())) {
						Files.delete(file);
					}
				}
			}
		}
		catch (IOException | RuntimeException exception) {
			log.warn("Commit {} is durable; readable JSON views need refresh: {}", this.commitId, this.workspaceDir, exception);
		}
	}

	private static void validate(JsonWorkspaceState state) {
		ModelIntegrity.validate(state.definitions.values().stream().flatMap(definitions -> definitions.values().stream()).toList(), state.layout);
		for (ElementIdentity identity : state.identities.values()) {
			var definition = state.definitions.get(identity.kind()).get(identity.elementKey());
			if (identity.deleted() ? definition != null : definition == null || !identity.id().equals(definition.id())) {
				throw new IllegalStateException("Element registry does not match definitions: " + identity.elementKey());
			}
		}
		for (RevisionRecord record : state.revisions) {
			if (!state.identities.containsKey(record.elementKey())) {
				throw new IllegalStateException("Revision references an unknown element key: " + record.elementKey());
			}
		}
	}

	private Path storeDir() {
		return this.workspaceDir.resolve(".store");
	}

	private Path commitDirectory(String id) {
		return storeDir().resolve("commits").resolve(validCommitId(id));
	}

	private static String validCommitId(String id) {
		if (id == null || !UUID.fromString(id).toString().equals(id)) {
			throw new IllegalStateException("Invalid commit identity");
		}
		return id;
	}

	private static void writeAtomic(Path target, byte[] content) throws IOException {
		Path temp = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
		writeDurable(temp, content);
		Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void writeDurable(Path target, byte[] content) throws IOException {
		try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			ByteBuffer buffer = ByteBuffer.wrap(content);
			while (buffer.hasRemaining()) {
				channel.write(buffer);
			}
			channel.force(true);
		}
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Override
	@PreDestroy
	public void close() {
		this.lock.lock();
		try {
			if (this.writerLock != null) {
				this.writerLock.release();
				this.writerLock = null;
			}
			if (this.lockChannel != null) {
				this.lockChannel.close();
				this.lockChannel = null;
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		finally {
			this.lock.unlock();
		}
	}

}
