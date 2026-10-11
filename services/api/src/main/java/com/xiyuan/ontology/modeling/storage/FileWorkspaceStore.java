package com.xiyuan.ontology.modeling.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.xiyuan.ontology.modeling.api.ApiException;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 本地 JSON 文件存储实现（TDD-OM-002 §10，DEV-4 用户确认：首期唯一持久化载体）。
 *
 * <p>布局：object-model.json 主文档（权威事实源）+ layout.json + revisions/*.jsonl
 * + review-flags.json + events.jsonl；写入一律 temp + 原子 rename；每工作空间一把
 * {@link ReentrantLock}，读-校验-写全程持锁；启动时 Schema 要点校验 + 引用完整性自检，
 * 失败拒绝启动（§10.2）。
 */
@Component
public class FileWorkspaceStore implements WorkspaceStore {

	private static final Logger log = LoggerFactory.getLogger(FileWorkspaceStore.class);

	private final Path workspaceDir;

	private final ResourceLoader resourceLoader;

	private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

	private final ReentrantLock lock = new ReentrantLock();

	private ObjectNode document;

	private int layoutRevision;

	private ObjectNode positions;

	private Path mainDocumentPath;

	private Path layoutPath;

	private Path revisionsDir;

	private Path reviewFlagsPath;

	private Path eventsPath;

	public FileWorkspaceStore(@Value("${xiyuan.workspace.dir:data/workspaces/ENV-OM-001}") String workspaceDir,
			ResourceLoader resourceLoader) {
		this.workspaceDir = Path.of(workspaceDir).toAbsolutePath().normalize();
		this.resourceLoader = resourceLoader;
	}

	@PostConstruct
	void initialize() {
		this.lock.lock();
		try {
			Files.createDirectories(this.workspaceDir);
			this.mainDocumentPath = this.workspaceDir.resolve("object-model.json");
			this.layoutPath = this.workspaceDir.resolve("layout.json");
			this.revisionsDir = this.workspaceDir.resolve("revisions");
			this.reviewFlagsPath = this.workspaceDir.resolve("review-flags.json");
			this.eventsPath = this.workspaceDir.resolve("events.jsonl");
			Files.createDirectories(this.revisionsDir);

			if (Files.notExists(this.mainDocumentPath)) {
				seedMainDocument();
			}
			if (Files.notExists(this.layoutPath)) {
				ObjectNode layout = JsonNodeFactory.instance.objectNode();
				layout.put("layoutRevision", 1);
				layout.set("positions", JsonNodeFactory.instance.objectNode());
				writeAtomic(this.layoutPath, layout);
			}
			if (Files.notExists(this.reviewFlagsPath)) {
				ObjectNode flags = JsonNodeFactory.instance.objectNode();
				flags.set("flags", JsonNodeFactory.instance.arrayNode());
				writeAtomic(this.reviewFlagsPath, flags);
			}
			if (Files.notExists(this.eventsPath)) {
				Files.writeString(this.eventsPath, "", StandardCharsets.UTF_8);
			}

			this.document = (ObjectNode) this.mapper.readTree(Files.readAllBytes(this.mainDocumentPath));
			selfCheck(this.document);
			ObjectNode layout = (ObjectNode) this.mapper.readTree(Files.readAllBytes(this.layoutPath));
			if (!layout.path("positions").isObject() || !layout.path("layoutRevision").isIntegralNumber()) {
				throw new IllegalStateException("layout.json 自检失败：缺少 positions 或 layoutRevision");
			}
			this.layoutRevision = layout.path("layoutRevision").asInt();
			this.positions = (ObjectNode) layout.path("positions");
			log.info("对象建模工作空间已加载: {}（对象 {} 个，关系 {} 条，领域 {} 个）", this.workspaceDir,
					this.document.path("objects").size(), this.document.path("relations").size(),
					this.document.path("domains").size());
		}
		catch (IOException ex) {
			throw new IllegalStateException("工作空间初始化失败: " + this.workspaceDir, ex);
		}
		finally {
			this.lock.unlock();
		}
	}

	private void seedMainDocument() throws IOException {
		Resource seed = this.resourceLoader.getResource("classpath:seed/object-model.json");
		if (!seed.exists()) {
			throw new IllegalStateException("缺少种子数据 classpath:seed/object-model.json");
		}
		writeAtomic(this.mainDocumentPath, seed.getContentAsByteArray());
		log.info("已按种子数据初始化对象模型主文档: {}", this.mainDocumentPath);
	}

	/**
	 * 启动自检（§10.2 简化版：Schema 要点 + 引用存在性），失败拒绝启动并报告定位。
	 */
	private void selfCheck(ObjectNode doc) {
		if (!"object-model".equals(doc.path("kind").asString(""))) {
			throw new IllegalStateException("object-model.json 自检失败：kind 必须为 object-model");
		}
		requireArray(doc, "domains");
		requireArray(doc, "objects");
		requireArray(doc, "relations");

		Set<String> domainIds = collectIds(doc.path("domains"), "domainId");
		Set<String> objectIds = collectIds(doc.path("objects"), "id");
		Set<String> relationIds = collectIds(doc.path("relations"), "id");

		for (JsonNode object : doc.path("objects")) {
			String id = object.path("id").asString("?");
			String domainId = object.path("domainId").asString("");
			if (!domainIds.contains(domainId)) {
				throw new IllegalStateException("自检失败：对象 " + id + " 归属领域不存在: " + domainId);
			}
			Set<String> attrIds = new HashSet<>();
			for (JsonNode attr : object.path("attributes")) {
				attrIds.add(attr.path("id").asString(""));
				for (JsonNode target : attr.path("referenceTarget")) {
					if (!objectIds.contains(target.asString(""))) {
						throw new IllegalStateException(
								"自检失败：属性 " + attr.path("id").asString("?") + " 引用目标对象不存在: " + target.asString(""));
					}
				}
				String relationRef = attr.path("relationRef").asString(null);
				if (relationRef != null && !relationIds.contains(relationRef)) {
					throw new IllegalStateException(
							"自检失败：属性 " + attr.path("id").asString("?") + " 引用关系不存在: " + relationRef);
				}
			}
			for (JsonNode identityAttr : object.path("identitySpec").path("attributes")) {
				if (!attrIds.contains(identityAttr.asString(""))) {
					throw new IllegalStateException(
							"自检失败：对象 " + id + " 业务标识引用属性不存在: " + identityAttr.asString(""));
				}
			}
		}
		for (JsonNode relation : doc.path("relations")) {
			String id = relation.path("id").asString("?");
			if (!objectIds.contains(relation.path("sourceType").asString(""))) {
				throw new IllegalStateException("自检失败：关系 " + id + " 源端点不存在");
			}
			JsonNode targetType = relation.path("targetType");
			if (targetType.isArray()) {
				for (JsonNode t : targetType) {
					if (!objectIds.contains(t.asString(""))) {
						throw new IllegalStateException("自检失败：关系 " + id + " 目标端点不存在: " + t.asString(""));
					}
				}
			}
			else if (!objectIds.contains(targetType.asString(""))) {
				throw new IllegalStateException("自检失败：关系 " + id + " 目标端点不存在");
			}
		}
	}

	private static void requireArray(ObjectNode doc, String field) {
		if (!doc.path(field).isArray()) {
			throw new IllegalStateException("object-model.json 自检失败：缺少数组字段 " + field);
		}
	}

	private static Set<String> collectIds(JsonNode array, String idField) {
		Set<String> ids = new HashSet<>();
		for (JsonNode node : array) {
			String id = node.path(idField).asString("");
			if (!ids.add(id)) {
				throw new IllegalStateException("自检失败：稳定标识重复: " + id);
			}
		}
		return ids;
	}

	@Override
	public <T> T read(Function<ObjectNode, T> reader) {
		this.lock.lock();
		try {
			return reader.apply(snapshot());
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public <T> T mutate(Function<ObjectNode, T> mutator) {
		this.lock.lock();
		try {
			ObjectNode working = snapshot();
			T result = mutator.apply(working);
			selfCheck(working);
			writeAtomic(this.mainDocumentPath, working);
			this.document = working;
			return result;
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public void appendRevision(String elementId, ObjectNode revisionRecord) {
		this.lock.lock();
		try {
			Path file = this.revisionsDir.resolve(elementId + ".jsonl").normalize();
			if (!file.getParent().equals(this.revisionsDir)) {
				throw ApiException.badRequest("OBJ_ID_INVALID", "标识不能包含路径分隔符");
			}
			String line = this.mapper.writeValueAsString(revisionRecord) + System.lineSeparator();
			Files.writeString(file, line, StandardCharsets.UTF_8, Files.exists(file)
					? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("修订记录写入失败: " + elementId, ex);
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public void appendEvent(ObjectNode event) {
		this.lock.lock();
		try {
			String line = this.mapper.writeValueAsString(event) + System.lineSeparator();
			Files.writeString(this.eventsPath, line, StandardCharsets.UTF_8,
					Files.exists(this.eventsPath) ? java.nio.file.StandardOpenOption.APPEND
							: java.nio.file.StandardOpenOption.CREATE);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("事件日志写入失败", ex);
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public int layoutRevision() {
		this.lock.lock();
		try {
			return this.layoutRevision;
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public int saveLayout(int expectedRevision, ObjectNode positions) {
		this.lock.lock();
		try {
			if (this.layoutRevision != expectedRevision) {
				throw ApiException.revisionConflict("layout", expectedRevision, this.layoutRevision);
			}
			ObjectNode merged = this.positions.deepCopy();
			positions.properties().forEach(entry -> {
				String id = entry.getKey();
				JsonNode pos = entry.getValue();
				boolean exists = false;
				for (JsonNode object : this.document.path("objects")) {
					if (id.equals(object.path("id").asString())) exists = true;
				}
				if (!exists || !pos.isObject() || !pos.path("x").isNumber() || !pos.path("y").isNumber()
						|| !Double.isFinite(pos.path("x").asDouble()) || !Double.isFinite(pos.path("y").asDouble())) {
					throw ApiException.badRequest("LAYOUT_INVALID", "布局坐标或对象标识无效: " + id);
				}
				merged.set(id, pos.deepCopy());
			});
			int nextRevision = this.layoutRevision + 1;
			ObjectNode layout = JsonNodeFactory.instance.objectNode();
			layout.put("layoutRevision", nextRevision);
			layout.set("positions", merged);
			writeAtomic(this.layoutPath, layout);
			this.layoutRevision = nextRevision;
			this.positions = merged;
			for (JsonNode object : this.document.path("objects")) {
				JsonNode position = merged.path(object.path("id").asString());
				if (position.isObject()) {
					((ObjectNode) object).set("graph", position.deepCopy());
				}
			}
			return this.layoutRevision;
		}
		finally {
			this.lock.unlock();
		}
	}

	private ObjectNode snapshot() {
		ObjectNode copy = this.document.deepCopy();
		for (JsonNode object : copy.path("objects")) {
			JsonNode pos = this.positions.path(object.path("id").asString());
			if (pos.isObject()) ((ObjectNode) object).set("graph", pos.deepCopy());
		}
		return copy;
	}

	private void writeAtomic(Path target, JsonNode node) {
		writeAtomic(target, toPrettyBytes(node));
	}

	private void writeAtomic(Path target, byte[] content) {
		try {
			Path temp = target.resolveSibling(target.getFileName() + ".tmp");
			Files.write(temp, content);
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException atomicMoveUnsupported) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("文件写入失败: " + target, ex);
		}
	}

	private byte[] toPrettyBytes(JsonNode node) {
		return this.mapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString(node)
			.getBytes(StandardCharsets.UTF_8);
	}

	public ObjectNode newEvent(String eventType, String elementId) {
		ObjectNode event = JsonNodeFactory.instance.objectNode();
		event.put("eventId", "EVT-" + Instant.now().toEpochMilli());
		event.put("eventType", eventType);
		event.put("elementId", elementId);
		event.put("occurredAt", Instant.now().toString());
		return event;
	}

	public ArrayNode arrayOf() {
		return JsonNodeFactory.instance.arrayNode();
	}

}
