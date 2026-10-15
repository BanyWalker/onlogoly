package com.xiyuan.ontology.objectfact.storage;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xiyuan.ontology.objectfact.api.FactException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** One atomic authority for current facts and the append-only operation ledger. */
@Component
public class FactStorage {
	private static final Logger log = LoggerFactory.getLogger(FactStorage.class);
	private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
	private final Path workspace;
	private final Path authority;
	private FileChannel channel;
	private FileLock lock;
	private ObjectNode state;
	private String disabledReason;

	@Autowired
	public FactStorage(@Value("${xiyuan.workspace.dir}") String workspace) { this(Path.of(workspace)); }

	public FactStorage(Path workspace) {
		this.workspace = workspace.toAbsolutePath().normalize();
		this.authority = this.workspace.resolve(".facts-store/current.json");
		try {
			Files.createDirectories(authority.getParent());
			channel = FileChannel.open(authority.resolveSibling("writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			lock = channel.tryLock();
			if (lock == null) throw new IllegalStateException("对象事实工作空间已被另一个进程占用");
			if (Files.exists(authority)) {
				state = (ObjectNode) mapper.readTree(Files.readString(authority, StandardCharsets.UTF_8));
				verify(state);
			} else {
				if (Files.exists(this.workspace.resolve("object-facts.json"))) throw new IllegalStateException("存在无权威提交的实例文件，请显式迁移");
				state = empty();
			}
			project();
		} catch (Exception error) {
			disabledReason = error.getMessage();
			log.error("对象事实模块自检失败；其他模块继续运行", error);
		}
	}

	private ObjectNode empty() {
		ObjectNode result = mapper.createObjectNode();
		result.put("schemaVersion", "1.0");
		result.put("kind", "object-facts");
		result.put("revision", 0);
		ObjectNode sequence = result.putObject("meta").putObject("idSequence");
		for (String key : new String[] { "instance", "relation", "operation", "batch" }) sequence.put(key, 0);
		result.putArray("instances");
		result.putArray("relationInstances");
		result.putArray("records");
		return result;
	}

	private void verify(ObjectNode document) {
		if (!"1.0".equals(document.path("schemaVersion").asString()) || !"object-facts".equals(document.path("kind").asString())
				|| !document.path("instances").isArray() || !document.path("relationInstances").isArray()
				|| !document.path("records").isArray() || !document.path("meta").path("idSequence").isObject())
			throw new IllegalStateException("实例事实存储结构损坏");
		var ids = new java.util.HashSet<String>();
		long maxInstance = 0, maxRelation = 0;
		for (var instance : document.path("instances")) {
			String id = instance.path("instanceId").asString();
			if (!id.matches("INST-\\d{6,}") || !ids.add(id)) throw new IllegalStateException("实例稳定标识损坏或重复");
			maxInstance = Math.max(maxInstance, Long.parseLong(id.substring(5)));
		}
		var relationIds = new java.util.HashSet<String>();
		for (var relation : document.path("relationInstances")) {
			String id = relation.path("relationId").asString();
			if (!id.matches("RELI-\\d{6,}") || !relationIds.add(id)
					|| !ids.contains(relation.path("sourceInstanceId").asString()) || !ids.contains(relation.path("targetInstanceId").asString()))
				throw new IllegalStateException("关系标识损坏、重复或两端实例缺失");
			maxRelation = Math.max(maxRelation, Long.parseLong(id.substring(5)));
		}
		if (document.path("meta").path("idSequence").path("instance").asLong() < maxInstance
				|| document.path("meta").path("idSequence").path("relation").asLong() < maxRelation)
			throw new IllegalStateException("实例事实标识序列损坏");
	}

	public synchronized <T> T read(Function<ObjectNode, T> action) {
		available();
		return action.apply(state.deepCopy());
	}

	public synchronized <T> T write(Long expectedRevision, Function<ObjectNode, T> action) {
		available();
		if (expectedRevision != null && expectedRevision != state.path("revision").asLong())
			throw new FactException(409, "OF_REVISION_CONFLICT", "实例数据已更新，请刷新后重试");
		ObjectNode candidate = state.deepCopy();
		T result = action.apply(candidate);
		candidate.put("revision", state.path("revision").asLong() + 1);
		verify(candidate);
		try { atomicWrite(authority, mapper.writeValueAsBytes(candidate)); }
		catch (Exception error) { throw new FactException(500, "OF_STORAGE_FAILED", "事实提交失败，既有数据未修改"); }
		state = candidate;
		project();
		return result;
	}

	private void project() {
		try {
			ObjectNode facts = state.deepCopy();
			facts.remove("records");
			atomicWrite(workspace.resolve("object-facts.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(facts));
			StringBuilder ledger = new StringBuilder();
			for (var record : state.path("records")) ledger.append(mapper.writeValueAsString(record)).append('\n');
			atomicWrite(workspace.resolve("fact-batches.jsonl"), ledger.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Exception error) { log.warn("实例查看副本刷新失败，权威提交正常；重启或下次提交将重建副本", error); }
	}

	protected void atomicWrite(Path target, byte[] bytes) throws java.io.IOException {
		Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try {
			try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				var buffer = java.nio.ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining()) file.write(buffer);
				file.force(true);
			}
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally { Files.deleteIfExists(temporary); }
	}

	private void available() {
		if (disabledReason != null) throw new FactException(503, "OF_MODULE_DISABLED", "对象事实模块不可用：" + disabledReason);
	}

	@PreDestroy
	public synchronized void close() {
		try { if (lock != null && lock.isValid()) lock.release(); if (channel != null) channel.close(); }
		catch (Exception error) { log.warn("实例文件锁释放失败", error); }
	}
}
