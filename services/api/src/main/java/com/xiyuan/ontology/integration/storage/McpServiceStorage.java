package com.xiyuan.ontology.integration.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 服务的 JSON 文件存储与受控调用实现。
 *
 * <p>数据按工作空间隔离，定义、快照、映射和调用记录先保存在一个 JSON 文件中；
 * 后续替换为数据库时，控制器契约与状态校验保持不变。</p>
 */
@Service
public class McpServiceStorage {

	private static final String PROTOCOL_VERSION = "2025-06-18";
	private static final List<String> FAILURE_CATEGORIES = List.of("NETWORK_UNREACHABLE", "DNS_FAILURE", "TIMEOUT", "AUTH_FAILED", "CLIENT_ERROR", "SERVER_ERROR", "MCP_PROTOCOL_ERROR", "MCP_PROTOCOL_INCOMPATIBLE");

	private final Path documentPath;
	private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final java.util.concurrent.ConcurrentMap<String, String> runtimeAuthSecrets = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentMap<String, String> runtimeSessionIds = new java.util.concurrent.ConcurrentHashMap<>();
	private ObjectNode document;

	public McpServiceStorage(@Value("${xiyuan.workspace.dir}") String workspaceDir) {
		this.documentPath = Path.of(workspaceDir).resolve("mcp-services.json");
		this.document = load();
		if (normalizeStates()) persist();
		boolean sanitized = false;
		for (JsonNode node : servers()) {
			if (node.path("auth").has("secret")) {
				captureRuntimeAuth((ObjectNode) node);
				sanitized = true;
			}
		}
		if (sanitized) persist();
	}

	public synchronized List<ObjectNode> list(String query, String status, String domainId) {
		String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<ObjectNode> result = new ArrayList<>();
		for (JsonNode node : servers()) {
			if (!keyword.isEmpty() && !contains(node, "id", keyword) && !contains(node, "name", keyword) && !contains(node, "definition", keyword)) continue;
			if (status != null && !status.isBlank() && !status.equals(node.path("status").asText())) continue;
			if (domainId != null && !domainId.isBlank() && !domainId.equals(node.path("domainId").asText())) continue;
			result.add(summary((ObjectNode) node));
		}
		return result;
	}

	public synchronized ObjectNode get(String id) { return findServer(id).deepCopy(); }

	public synchronized ObjectNode create(ObjectNode body) {
		String id = required(body, "id");
		if (findServerOrNull(id) != null) throw IntegrationStorageException.conflict("SI_ID_NOT_UNIQUE", "MCP 服务标识已存在: " + id);
		validateServerDraft(body);
		ObjectNode server = body.deepCopy();
		setDefaults(server, id);
		server.put("status", "DRAFT");
		server.put("enabled", false);
		captureRuntimeAuth(server);
		servers().add(server);
		persist();
		return server.deepCopy();
	}

	public synchronized ObjectNode update(String id, ObjectNode body) {
		ObjectNode server = findServer(id);
		checkRevision(server, body);
		validateRequestedState(server, body);
		for (String field : body.propertyNames()) {
			if (!List.of("id", "createdAt", "createdBy", "tools", "snapshots", "status", "enabled").contains(field)) server.set(field, body.path(field).deepCopy());
		}
		if (body.has("status")) server.put("status", body.path("status").asText());
		if (body.has("enabled")) server.put("enabled", body.path("enabled").asBoolean(false));
		if ("PUBLISHED".equals(server.path("status").asText())) validateServerComplete(server);
		else validateServerDraft(server);
		captureRuntimeAuth(server);
		server.put("revision", server.path("revision").asInt(1) + 1);
		server.put("updatedAt", Instant.now().toString());
		persist();
		return server.deepCopy();
	}

	public synchronized ObjectNode publish(String id, ObjectNode body) {
		ObjectNode server = findServer(id);
		checkRevision(server, body);
		if (!"DRAFT".equals(server.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有草稿服务可以发布");
		validateServerComplete(server);
		server.put("status", "PUBLISHED");
		server.put("enabled", false);
		return touch(server);
	}

	public synchronized ObjectNode unpublish(String id, ObjectNode body) {
		ObjectNode server = findServer(id);
		checkRevision(server, body);
		if (!"PUBLISHED".equals(server.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有已发布服务可以下架");
		server.put("status", "DRAFT"); server.put("enabled", false); return touch(server);
	}

	public synchronized ObjectNode enable(String id, ObjectNode body) {
		ObjectNode server = findServer(id);
		checkRevision(server, body);
		if (!"PUBLISHED".equals(server.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有已发布服务可以启用");
		server.put("enabled", true); return touch(server);
	}

	public synchronized ObjectNode disable(String id, ObjectNode body) {
		ObjectNode server = findServer(id);
		checkRevision(server, body);
		if (!"PUBLISHED".equals(server.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有已发布服务可以禁用");
		server.put("enabled", false); return touch(server);
	}

	public synchronized ObjectNode delete(String id, ObjectNode body) {
		ObjectNode server = findServer(id);
		checkRevision(server, body);
		if (!"DRAFT".equals(server.path("status").asText())) throw IntegrationStorageException.conflict("SI_DELETE_REQUIRES_DRAFT", "已发布服务请先下架再删除");
		List<ObjectNode> refs = mappingsForServer(id);
		if (!refs.isEmpty()) throw IntegrationStorageException.conflict("SI_DELETE_PROTECTED", "MCP 服务存在未处理映射引用: " + refs.stream().map(item -> item.path("id").asText()).toList());
		for (int index = 0; index < servers().size(); index++) if (servers().get(index) == server) { servers().remove(index); break; }
		runtimeAuthSecrets.remove(id);
		runtimeSessionIds.remove(id);
		persist();
		return server.deepCopy();
	}

	public synchronized ObjectNode discover(String id) {
		ObjectNode server = findServer(id);
		ObjectNode result = handshake(server);
		server.set("lastTest", result.deepCopy());
		if (result.path("outcome").asText().equals("SUCCESS")) {
			server.put("protocolVersion", result.path("protocolVersion").asText(PROTOCOL_VERSION));
			server.set("tools", result.path("tools").deepCopy());
			appendSnapshot(server, result.path("tools"));
		}
		server.put("updatedAt", Instant.now().toString());
		persist();
		return result;
	}

	public synchronized ObjectNode refresh(String id) {
		ObjectNode server = findServer(id);
		ObjectNode result = handshake(server);
		if (!result.path("outcome").asText().equals("SUCCESS")) {
			appendRefreshRecord(server, result, null, null);
			server.set("lastTest", result.deepCopy());
			server.put("updatedAt", Instant.now().toString());
			persist();
			return result;
		}
		ArrayNode oldTools = server.withArray("tools");
		ArrayNode incoming = (ArrayNode) result.path("tools");
		ObjectNode diff = diff(oldTools, incoming);
		server.put("protocolVersion", result.path("protocolVersion").asText(PROTOCOL_VERSION));
		applyToolDiff(server, incoming);
		int snapshotId = appendSnapshot(server, incoming);
		server.set("lastTest", result.deepCopy());
		server.put("revision", server.path("revision").asInt(1) + 1);
		server.put("updatedAt", Instant.now().toString());
		result.set("diff", diff);
		result.put("snapshotId", snapshotId);
		appendRefreshRecord(server, result, snapshotId, diff);
		persist();
		return result;
	}

	public synchronized List<ObjectNode> tools(String id) { return findServer(id).withArray("tools").valueStream().map(node -> ((ObjectNode) node).deepCopy()).toList(); }

	public synchronized ObjectNode updateTool(String id, String toolKey, ObjectNode body) {
		ObjectNode server = findServer(id);
		ObjectNode tool = findTool(server, toolKey);
		if ("REMOVED".equals(tool.path("changeStatus").asText())) throw IntegrationStorageException.conflict("SI_TOOL_REMOVED", "服务端已移除的工具不可启用: " + toolKey);
		if (body.has("enabled")) tool.put("enabled", body.path("enabled").asBoolean());
		if (body.has("operationNature")) {
			String nature = body.path("operationNature").asText();
			if (!List.of("READ", "WRITE").contains(nature)) throw IntegrationStorageException.badRequest("SI_INVALID_OPERATION_NATURE", "工具操作性质必须为 READ 或 WRITE");
			tool.put("operationNature", nature);
		}
		if (body.has("timeoutMs")) tool.put("timeoutMs", body.path("timeoutMs").asInt(server.path("timeoutMs").asInt(10000)));
		if (tool.path("pendingReview").asBoolean(false) && tool.path("enabled").asBoolean(false)) throw IntegrationStorageException.conflict("SI_TOOL_REVIEW_REQUIRED", "输入模式变化的工具需先完成复核");
		server.put("revision", server.path("revision").asInt(1) + 1);
		persist();
		return tool.deepCopy();
	}

	public synchronized ObjectNode reviewTool(String id, String toolKey) {
		ObjectNode tool = findTool(findServer(id), toolKey);
		tool.put("pendingReview", false);
		tool.put("revision", tool.path("revision").asInt(1) + 1);
		persist();
		return tool.deepCopy();
	}

	public synchronized ObjectNode invoke(String id, String toolKey, ObjectNode body) {
		ObjectNode server = findServer(id);
		ObjectNode tool = findTool(server, toolKey);
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		long started = System.nanoTime();
		String status = "SUCCESS";
		String error = null;
		if (!"PUBLISHED".equals(server.path("status").asText()) || !server.path("enabled").asBoolean(false) || !tool.path("enabled").asBoolean(false)) { status = "REJECTED"; error = "SI_TARGET_DISABLED"; }
		else if ("REMOVED".equals(tool.path("changeStatus").asText())) { status = "REJECTED"; error = "SI_TOOL_REMOVED"; }
		else if ("WRITE".equals(tool.path("operationNature").asText()) && !body.path("confirm").asBoolean(false)) { status = "REJECTED"; error = "SI_WRITE_CONFIRM_REQUIRED"; }
		else {
			JsonNode arguments = body.path("arguments").isObject() ? body.path("arguments") : body;
			String validation = validateArguments(tool.path("inputSchema"), arguments);
			if (validation != null) { status = "REJECTED"; error = "SI_INVOKE_VALIDATION_FAILED"; result.put("validationMessage", validation); }
			else {
				ObjectNode outbound = JsonNodeFactory.instance.objectNode().put("jsonrpc", "2.0").put("id", UUID.randomUUID().toString()).put("method", "tools/call");
				ObjectNode params = outbound.putObject("params").put("name", tool.path("name").asText());
				params.set("arguments", arguments.deepCopy());
				ObjectNode remote = send(server, outbound);
				if (remote.has("error")) { status = "FAILED"; error = "MCP_PROTOCOL_ERROR"; result.set("response", remote.path("error").deepCopy()); }
				else { result.set("response", remote.path("result").deepCopy()); result.put("externalContent", true); result.put("responseSource", "REMOTE_MCP"); }
			}
		}
		long latency = (System.nanoTime() - started) / 1_000_000;
		result.put("resultStatus", status);
		if (error == null) result.putNull("normalizedError"); else result.put("normalizedError", error);
		result.put("latencyMs", latency);
		result.put("pendingReview", tool.path("pendingReview").asBoolean(false));
		if (!result.has("response")) result.putObject("response").put("message", error == null ? "调用完成" : error);
		appendCallLog(server, tool, result, body.path("arguments"));
		persist();
		return result;
	}

	public synchronized List<ObjectNode> logs(String id, String resultStatus) {
		return logs().valueStream().filter(node -> (id == null || id.isBlank() || id.equals(node.path("serverId").asText())) && (resultStatus == null || resultStatus.isBlank() || resultStatus.equals(node.path("resultStatus").asText()))).map(node -> ((ObjectNode) node).deepCopy()).toList();
	}

	public synchronized List<ObjectNode> snapshots(String id) { return findServer(id).withArray("snapshots").valueStream().map(node -> ((ObjectNode) node).deepCopy()).toList(); }

	public synchronized List<ObjectNode> mappings(String serverId, String targetRef) {
		return mappings().valueStream().filter(node -> (serverId == null || serverId.isBlank() || serverId.equals(node.path("serverId").asText())) && (targetRef == null || targetRef.isBlank() || targetRef.equals(node.path("targetRef").asText()))).map(node -> ((ObjectNode) node).deepCopy()).toList();
	}

	public synchronized ObjectNode createMapping(ObjectNode body) {
		String id = required(body, "id");
		if (findMappingOrNull(id) != null) throw IntegrationStorageException.conflict("SI_ID_NOT_UNIQUE", "映射标识已存在: " + id);
		String serverId = required(body, "serverId");
		ObjectNode server = findServer(serverId);
		String toolKey = required(body, "toolKey");
		findTool(server, toolKey);
		String targetRef = required(body, "targetRef");
		if (!targetRef.matches("^(OBJ|REL|DOMAIN)-[A-Za-z0-9_-]+(?:[.#/][A-Za-z0-9_-]+)?$") || !targetExists(targetRef)) throw IntegrationStorageException.badRequest("SI_MAPPING_TARGET_MISSING", "映射目标不存在或格式不正确: " + targetRef);
		ObjectNode mapping = body.deepCopy();
		mapping.put("status", "ACTIVE");
		mapping.put("createdAt", Instant.now().toString());
		mappings().add(mapping);
		persist();
		return mapping.deepCopy();
	}

	public synchronized ObjectNode updateMapping(String id, ObjectNode body) {
		ObjectNode mapping = findMapping(id);
		for (String field : List.of("toolKey", "targetRef", "mappingKind", "description")) if (body.has(field)) mapping.set(field, body.path(field).deepCopy());
		if (!mapping.path("targetRef").asText().matches("^(OBJ|REL|DOMAIN)-[A-Za-z0-9_-]+(?:[.#/][A-Za-z0-9_-]+)?$") || !targetExists(mapping.path("targetRef").asText())) throw IntegrationStorageException.badRequest("SI_MAPPING_TARGET_MISSING", "映射目标不存在或格式不正确");
		mapping.put("status", "ACTIVE");
		persist();
		return mapping.deepCopy();
	}

	public synchronized ObjectNode deleteMapping(String id) { ObjectNode mapping = findMapping(id); for (int index = 0; index < mappings().size(); index++) { if (mappings().get(index) == mapping) { mappings().remove(index); break; } } persist(); return mapping.deepCopy(); }

	private ObjectNode handshake(ObjectNode server) {
		long started = System.nanoTime();
		ObjectNode request = JsonNodeFactory.instance.objectNode().put("jsonrpc", "2.0").put("id", UUID.randomUUID().toString()).put("method", "initialize");
		ObjectNode params = request.putObject("params").put("protocolVersion", PROTOCOL_VERSION);
		params.putObject("capabilities");
		params.putObject("clientInfo").put("name", "xiyuan-ontology-platform").put("version", "0.1.0");
		ObjectNode response = send(server, request);
		ObjectNode responseFailure = validateRemoteResponse(response, "initialize", started);
		if (responseFailure != null) return responseFailure;
		JsonNode initializeResult = response.path("result");
		if (!initializeResult.isObject()) return failure("MCP_PROTOCOL_ERROR", "initialize 响应缺少 result 对象", started);
		String negotiated = initializeResult.path("protocolVersion").asText("");
		if (negotiated.isBlank()) return failure("MCP_PROTOCOL_ERROR", "initialize 响应缺少 protocolVersion", started);
		if (!PROTOCOL_VERSION.equals(negotiated) && !negotiated.startsWith("2025-")) return failure("MCP_PROTOCOL_INCOMPATIBLE", "对端协议版本不兼容: " + negotiated, started);
		ObjectNode initialized = JsonNodeFactory.instance.objectNode().put("jsonrpc", "2.0").put("method", "notifications/initialized");
		ObjectNode notificationFailure = sendNotification(server, initialized, negotiated);
		if (notificationFailure != null) return notificationFailure;
		ObjectNode listRequest = JsonNodeFactory.instance.objectNode().put("jsonrpc", "2.0").put("id", UUID.randomUUID().toString()).put("method", "tools/list");
		ObjectNode toolsResponse = send(server, listRequest, negotiated);
		responseFailure = validateRemoteResponse(toolsResponse, "tools/list", started);
		if (responseFailure != null) return responseFailure;
		JsonNode toolsNode = toolsResponse.path("result").path("tools");
		if (!toolsResponse.path("result").isObject() || !toolsNode.isArray()) return failure("MCP_PROTOCOL_ERROR", "tools/list 响应缺少 tools 数组", started);
		ArrayNode tools = (ArrayNode) toolsNode;
		ObjectNode result = JsonNodeFactory.instance.objectNode().put("outcome", "SUCCESS").put("protocolVersion", negotiated).put("latencyMs", (System.nanoTime() - started) / 1_000_000);
		result.set("tools", normalizeTools(tools));
		return result;
	}

	private ObjectNode validateRemoteResponse(ObjectNode response, String operation, long started) {
		if (response.has("normalizedError")) return response;
		if (!response.has("error")) return null;
		JsonNode remoteError = response.path("error");
		String message = remoteError.path("message").asText("远端返回 JSON-RPC 错误");
		return failure("MCP_PROTOCOL_ERROR", "MCP " + operation + " 失败: " + message, started);
	}

	private ObjectNode send(ObjectNode server, ObjectNode request) {
		return send(server, request, null);
	}

	private ObjectNode send(ObjectNode server, ObjectNode request, String protocolVersion) {
		long timeout = Math.min(60000, Math.max(1000, server.path("timeoutMs").asLong(10000)));
		try {
			HttpRequest.Builder builder = requestBuilder(server, request, protocolVersion, timeout);
			HttpResponse<String> response = httpClient.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if ("initialize".equals(request.path("method").asText())) {
				response.headers().firstValue("Mcp-Session-Id").ifPresentOrElse(sessionId -> runtimeSessionIds.put(server.path("id").asText(), sessionId), () -> runtimeSessionIds.remove(server.path("id").asText()));
			}
			if (response.statusCode() == 401 || response.statusCode() == 403) return failure("AUTH_FAILED", "对端拒绝认证", System.nanoTime());
			if (response.statusCode() >= 400) return failure(response.statusCode() >= 500 ? "SERVER_ERROR" : "CLIENT_ERROR", "对端返回 HTTP " + response.statusCode(), System.nanoTime());
			JsonNode payload = parseRemotePayload(response.body());
			return payload instanceof ObjectNode object ? object : failure("MCP_PROTOCOL_ERROR", "对端响应不是 JSON-RPC 对象", System.nanoTime());
		}
		catch (java.net.http.HttpTimeoutException exception) { return failure("TIMEOUT", "MCP 请求超时", System.nanoTime()); }
		catch (java.net.UnknownHostException exception) { return failure("DNS_FAILURE", "域名解析失败", System.nanoTime()); }
		catch (Exception exception) { return failure("NETWORK_UNREACHABLE", "无法连接 MCP 服务", System.nanoTime()); }
	}

	private ObjectNode sendNotification(ObjectNode server, ObjectNode request, String protocolVersion) {
		long timeout = Math.min(60000, Math.max(1000, server.path("timeoutMs").asLong(10000)));
		try {
			HttpRequest.Builder builder = requestBuilder(server, request, protocolVersion, timeout);
			HttpResponse<String> response = httpClient.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() == 401 || response.statusCode() == 403) return failure("AUTH_FAILED", "对端拒绝认证", System.nanoTime());
			if (response.statusCode() >= 400) return failure(response.statusCode() >= 500 ? "SERVER_ERROR" : "CLIENT_ERROR", "对端返回 HTTP " + response.statusCode(), System.nanoTime());
			return null;
		}
		catch (java.net.http.HttpTimeoutException exception) { return failure("TIMEOUT", "MCP 请求超时", System.nanoTime()); }
		catch (java.net.UnknownHostException exception) { return failure("DNS_FAILURE", "域名解析失败", System.nanoTime()); }
		catch (Exception exception) { return failure("NETWORK_UNREACHABLE", "无法连接 MCP 服务", System.nanoTime()); }
	}

	private HttpRequest.Builder requestBuilder(ObjectNode server, ObjectNode request, String protocolVersion, long timeout) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.path("endpointUrl").asText())).timeout(Duration.ofMillis(timeout)).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream");
		String method = request.path("method").asText();
		if (!"initialize".equals(method)) {
			String negotiated = protocolVersion == null || protocolVersion.isBlank() ? server.path("protocolVersion").asText("") : protocolVersion;
			if (!negotiated.isBlank()) builder.header("MCP-Protocol-Version", negotiated);
			String sessionId = runtimeSessionIds.get(server.path("id").asText());
			if (sessionId != null && !sessionId.isBlank()) builder.header("Mcp-Session-Id", sessionId);
		}
		JsonNode auth = server.path("auth");
		String secret = runtimeAuthSecrets.get(server.path("id").asText());
		if (auth.path("authType").asText().equals("BEARER") && auth.path("configured").asBoolean(false) && secret != null && !secret.isBlank()) builder.header("Authorization", "Bearer " + secret);
		if (auth.path("authType").asText().equals("CUSTOM_HEADER") && auth.path("configured").asBoolean(false) && secret != null && !secret.isBlank()) builder.header(auth.path("headerName").asText("X-MCP-Token"), secret);
		return builder;
	}

	private JsonNode parseRemotePayload(String body) throws IOException {
		String content = body == null ? "" : body.trim();
		try {
			return mapper.readTree(content);
		}
		catch (Exception ignored) {
			String[] lines = content.split("\\R");
			for (int index = lines.length - 1; index >= 0; index--) {
				String line = lines[index].trim();
				if (line.startsWith("data:")) {
					String data = line.substring(5).trim();
					if (!data.isBlank() && !"[DONE]".equals(data)) return mapper.readTree(data);
				}
			}
			throw new IOException("MCP 响应不是 JSON 或 Streamable HTTP 事件");
		}
	}

	private ObjectNode failure(String category, String message, long started) { return JsonNodeFactory.instance.objectNode().put("outcome", "FAILED").put("normalizedError", category).put("message", message).put("latencyMs", Math.max(0, (System.nanoTime() - started) / 1_000_000)); }

	private ArrayNode normalizeTools(ArrayNode source) {
		ArrayNode tools = JsonNodeFactory.instance.arrayNode();
		for (JsonNode node : source) {
			if (!node.path("name").isTextual()) continue;
			ObjectNode tool = (ObjectNode) node.deepCopy();
			tool.put("toolKey", tool.path("name").asText());
			tool.putIfAbsent("description", JsonNodeFactory.instance.textNode(""));
			if (!tool.path("inputSchema").isObject()) tool.set("inputSchema", JsonNodeFactory.instance.objectNode().put("type", "object"));
			tool.putIfAbsent("enabled", JsonNodeFactory.instance.booleanNode(false));
			tool.putIfAbsent("operationNature", JsonNodeFactory.instance.textNode(tool.path("annotations").path("readOnlyHint").asBoolean(true) ? "READ" : "WRITE"));
			tool.putIfAbsent("changeStatus", JsonNodeFactory.instance.textNode("UNCHANGED"));
			tool.putIfAbsent("pendingReview", JsonNodeFactory.instance.booleanNode(false));
			tool.putIfAbsent("revision", JsonNodeFactory.instance.numberNode(1));
			tools.add(tool);
		}
		return tools;
	}

	private ObjectNode diff(ArrayNode oldTools, ArrayNode newTools) {
		int added = 0, removed = 0, changed = 0;
		for (JsonNode current : newTools) {
			JsonNode previous = findToolByName(oldTools, current.path("name").asText());
			if (previous == null) added++;
			else if (!canonical(previous.path("inputSchema")).equals(canonical(current.path("inputSchema")))) changed++;
		}
		for (JsonNode previous : oldTools) if (findToolByName(newTools, previous.path("name").asText()) == null) removed++;
		return JsonNodeFactory.instance.objectNode().put("added", added).put("removed", removed).put("schemaChanged", changed);
	}

	private void applyToolDiff(ObjectNode server, ArrayNode incoming) {
		ArrayNode existing = server.withArray("tools");
		for (JsonNode raw : incoming) {
			ObjectNode current = findToolOrNull(server, raw.path("name").asText());
			if (current == null) { existing.add(normalizeTools(JsonNodeFactory.instance.arrayNode().add(raw)).path(0)); continue; }
			boolean schemaChanged = !canonical(current.path("inputSchema")).equals(canonical(raw.path("inputSchema")));
			boolean wasRemoved = "REMOVED".equals(current.path("changeStatus").asText());
			for (String field : raw.propertyNames()) current.set(field, raw.path(field).deepCopy());
			if (schemaChanged) { current.put("changeStatus", "SCHEMA_CHANGED"); current.put("pendingReview", true); current.put("enabled", false); }
			else if (wasRemoved) { current.put("changeStatus", "ADDED"); current.put("enabled", false); }
		}
		for (JsonNode current : existing) if (findToolByName(incoming, current.path("name").asText()) == null) { ((ObjectNode) current).put("changeStatus", "REMOVED"); ((ObjectNode) current).put("enabled", false); }
	}

	private int appendSnapshot(ObjectNode server, JsonNode tools) { ObjectNode snapshot = JsonNodeFactory.instance.objectNode().put("snapshotId", server.withArray("snapshots").size() + 1).put("discoveredAt", Instant.now().toString()).put("protocolVersion", server.path("protocolVersion").asText(PROTOCOL_VERSION)); snapshot.set("tools", tools.deepCopy()); server.withArray("snapshots").add(snapshot); server.put("currentSnapshotId", snapshot.path("snapshotId").asInt()); return snapshot.path("snapshotId").asInt(); }

	private void appendRefreshRecord(ObjectNode server, ObjectNode result, Integer snapshotId, ObjectNode diff) {
		ObjectNode record = JsonNodeFactory.instance.objectNode()
			.put("recordId", "REFRESH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
			.put("occurredAt", Instant.now().toString())
			.put("outcome", result.path("outcome").asText("FAILED"));
		for (String field : List.of("normalizedError", "message", "protocolVersion", "latencyMs")) if (result.has(field)) record.set(field, result.path(field).deepCopy());
		if (snapshotId != null) record.put("snapshotId", snapshotId);
		if (diff != null) record.set("diff", diff.deepCopy());
		server.withArray("refreshRecords").add(record);
	}

	private void appendCallLog(ObjectNode server, ObjectNode tool, ObjectNode result, JsonNode arguments) { ObjectNode log = result.deepCopy(); log.put("callId", "CALL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)); log.put("serverId", server.path("id").asText()); log.put("toolKey", tool.path("toolKey").asText()); log.put("occurredAt", Instant.now().toString()); log.set("paramsDigest", redact(arguments)); logs().add(log); }

	private JsonNode redact(JsonNode source) { if (source == null || source.isMissingNode()) return JsonNodeFactory.instance.objectNode(); if (source.isObject()) { ObjectNode result = JsonNodeFactory.instance.objectNode(); source.propertyNames().forEach(name -> result.set(name, name.toLowerCase(Locale.ROOT).matches(".*(key|token|secret|password|authorization).*") ? JsonNodeFactory.instance.textNode("***") : redact(source.path(name)))); return result; } if (source.isArray()) { ArrayNode result = JsonNodeFactory.instance.arrayNode(); source.forEach(item -> result.add(redact(item))); return result; } return source.deepCopy(); }

	private String validateArguments(JsonNode schema, JsonNode arguments) { if (!schema.isObject() || !arguments.isObject()) return "入参必须为 JSON 对象"; for (JsonNode required : schema.path("required")) if (!arguments.has(required.asText())) return "缺少必填参数: " + required.asText(); return null; }

	private JsonNode canonical(JsonNode value) { if (value == null || value.isMissingNode()) return JsonNodeFactory.instance.nullNode(); if (value.isObject()) { ObjectNode output = JsonNodeFactory.instance.objectNode(); value.propertyNames().stream().sorted().forEach(name -> output.set(name, canonical(value.path(name)))); return output; } if (value.isArray()) { ArrayNode output = JsonNodeFactory.instance.arrayNode(); value.forEach(item -> output.add(canonical(item))); return output; } return value; }

	private ObjectNode load() {
		try {
			Files.createDirectories(documentPath.getParent());
			if (Files.exists(documentPath)) { JsonNode node = mapper.readTree(Files.readString(documentPath)); if (node instanceof ObjectNode object && object.path("kind").asText().equals("mcp-service-integration")) return object; }
			ObjectNode root = JsonNodeFactory.instance.objectNode().put("schemaVersion", "1.0").put("kind", "mcp-service-integration").put("workspace", "ENV-OM-001");
			root.putObject("meta").put("generatedAt", Instant.now().toString());
			ObjectNode server = root.putArray("mcpServers").addObject().put("id", "MCP-CMDB").put("name", "CMDB 查询服务").put("definition", "提供主机信息查询能力").put("domainId", "DOMAIN-OPS").put("owner", "CMDB 接入责任方").put("endpointUrl", "https://cmdb.example.invalid/mcp").put("transport", "HTTP").put("protocolVersion", PROTOCOL_VERSION).put("timeoutMs", 10000).put("status", "DRAFT").put("revision", 1);
			server.set("auth", JsonNodeFactory.instance.objectNode().put("authType", "CUSTOM_HEADER").put("configured", false).put("masked", "未配置")); server.putArray("tools"); server.putArray("snapshots"); server.putArray("refreshRecords");
			root.putArray("mappings"); root.putArray("callLog"); persistDocument(root); return root;
		}
		catch (IOException exception) { throw new IllegalStateException("无法读取 MCP 服务 JSON 文件", exception); }
	}

	private ArrayNode servers() { return document.withArray("mcpServers"); }
	private ArrayNode mappings() { return document.withArray("mappings"); }
	private ArrayNode logs() { return document.withArray("callLog"); }
	private ObjectNode findServer(String id) { ObjectNode result = findServerOrNull(id); if (result == null) throw IntegrationStorageException.notFound("SI_MCP_NOT_FOUND", "MCP 服务不存在: " + id); return result; }
	private ObjectNode findServerOrNull(String id) { for (JsonNode node : servers()) if (id.equals(node.path("id").asText())) return (ObjectNode) node; return null; }
	private ObjectNode findTool(ObjectNode server, String key) { ObjectNode result = findToolOrNull(server, key); if (result == null) throw IntegrationStorageException.notFound("SI_TOOL_NOT_FOUND", "MCP 工具不存在: " + key); return result; }
	private ObjectNode findToolOrNull(ObjectNode server, String key) { for (JsonNode node : server.withArray("tools")) if (key.equals(node.path("toolKey").asText()) || key.equals(node.path("name").asText())) return (ObjectNode) node; return null; }
	private ObjectNode findMapping(String id) { ObjectNode result = findMappingOrNull(id); if (result == null) throw IntegrationStorageException.notFound("SI_MAPPING_NOT_FOUND", "映射不存在: " + id); return result; }
	private ObjectNode findMappingOrNull(String id) { for (JsonNode node : mappings()) if (id.equals(node.path("id").asText())) return (ObjectNode) node; return null; }
	private List<ObjectNode> mappingsForServer(String id) { return mappings().valueStream().filter(node -> id.equals(node.path("serverId").asText())).map(node -> (ObjectNode) node).toList(); }
	private boolean targetExists(String targetRef) {
		String root = targetRef.split("[.#/]", 2)[0];
		Path modelPath = documentPath.resolveSibling("object-model.json");
		try {
			JsonNode model = Files.exists(modelPath) ? mapper.readTree(Files.readString(modelPath)) : JsonNodeFactory.instance.objectNode();
			for (JsonNode object : model.path("objects")) if (root.equals(object.path("id").asText()) && !"deleted".equalsIgnoreCase(object.path("status").asText())) return true;
			return false;
		}
		catch (IOException exception) { return false; }
	}
	private static JsonNode findToolByName(ArrayNode tools, String name) { for (JsonNode node : tools) if (name.equals(node.path("name").asText())) return node; return null; }
	private static boolean contains(JsonNode node, String field, String query) { return node.path(field).asText().toLowerCase(Locale.ROOT).contains(query); }
	private static String required(ObjectNode body, String field) { if (body.path(field).asText().isBlank()) throw IntegrationStorageException.badRequest("SI_REQUIRED_MISSING", "缺少必填字段: " + field); return body.path(field).asText(); }
	private static void checkRevision(ObjectNode current, ObjectNode body) { if (body != null && body.has("revision") && body.path("revision").asInt() != current.path("revision").asInt()) throw IntegrationStorageException.conflict("REVISION_CONFLICT", "定义已被修订，请刷新后重试"); }
	private static void validateServerDraft(ObjectNode body) { if (!List.of("DRAFT", "PUBLISHED").contains(body.path("status").asText("DRAFT"))) throw IntegrationStorageException.badRequest("SI_INVALID_STATUS", "状态只能是草稿或已发布"); if (!body.path("endpointUrl").asText().isBlank()) validateServerUrl(body); }
	private static void validateServerComplete(ObjectNode body) { for (String field : List.of("name", "definition", "endpointUrl", "domainId", "owner")) if (body.path(field).asText().isBlank()) throw IntegrationStorageException.badRequest("SI_REQUIRED_MISSING", "服务发布前缺少必填字段: " + field); validateServerUrl(body); }
	private static void validateServerUrl(ObjectNode body) { if (!"HTTP".equals(body.path("transport").asText("HTTP"))) throw IntegrationStorageException.badRequest("SI_TRANSPORT_UNSUPPORTED", "MCP 仅支持远程 HTTP 传输"); try { URI endpoint = URI.create(body.path("endpointUrl").asText()); if (!List.of("http", "https").contains(endpoint.getScheme())) throw new IllegalArgumentException(); } catch (IllegalArgumentException exception) { throw IntegrationStorageException.badRequest("SI_INVALID_URL", "MCP Endpoint 必须使用 HTTP/HTTPS"); } }
	private static void setDefaults(ObjectNode server, String id) { server.put("id", id); server.putIfAbsent("transport", JsonNodeFactory.instance.textNode("HTTP")); server.putIfAbsent("timeoutMs", JsonNodeFactory.instance.numberNode(10000)); server.putIfAbsent("status", JsonNodeFactory.instance.textNode("DRAFT")); server.putIfAbsent("revision", JsonNodeFactory.instance.numberNode(1)); server.putIfAbsent("enabled", JsonNodeFactory.instance.booleanNode(false)); server.putIfAbsent("tools", JsonNodeFactory.instance.arrayNode()); server.putIfAbsent("snapshots", JsonNodeFactory.instance.arrayNode()); server.putIfAbsent("refreshRecords", JsonNodeFactory.instance.arrayNode()); server.putIfAbsent("auth", JsonNodeFactory.instance.objectNode().put("authType", "NONE").put("configured", false).put("masked", "未配置")); server.put("createdAt", Instant.now().toString()); }
	private void captureRuntimeAuth(ObjectNode server) { JsonNode authNode = server.path("auth"); if (!authNode.isObject()) return; ObjectNode auth = (ObjectNode) authNode; JsonNode secretNode = auth.get("secret"); if (secretNode != null && secretNode.isTextual() && !secretNode.asText().isBlank()) runtimeAuthSecrets.put(server.path("id").asText(), secretNode.asText()); auth.remove("secret"); if (secretNode != null && secretNode.isTextual() && !secretNode.asText().isBlank()) auth.put("configured", true); if (auth.path("configured").asBoolean(false) && auth.path("masked").asText().isBlank()) auth.put("masked", "已配置"); }
	private static ObjectNode summary(ObjectNode server) { ObjectNode result = server.deepCopy(); result.put("toolCount", server.withArray("tools").size()); result.put("enabledToolCount", (int) server.withArray("tools").valueStream().filter(node -> node.path("enabled").asBoolean(false) && !"REMOVED".equals(node.path("changeStatus").asText())).count()); result.put("pendingReviewCount", (int) server.withArray("tools").valueStream().filter(node -> node.path("pendingReview").asBoolean(false)).count()); result.remove("tools"); result.remove("snapshots"); result.remove("refreshRecords"); return result; }
	private static void validateRequestedState(ObjectNode current, ObjectNode body) {
		if (body.has("status") && !List.of("DRAFT", "PUBLISHED").contains(body.path("status").asText())) throw IntegrationStorageException.badRequest("SI_INVALID_STATUS", "状态只能是草稿或已发布");
		String oldStatus = current.path("status").asText("DRAFT"); String newStatus = body.has("status") ? body.path("status").asText() : oldStatus;
		boolean oldEnabled = current.path("enabled").asBoolean(false); boolean newEnabled = body.has("enabled") ? body.path("enabled").asBoolean(false) : oldEnabled;
		if ("DRAFT".equals(newStatus) && newEnabled) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "草稿服务不能启用");
		if (!oldStatus.equals(newStatus) && !(("DRAFT".equals(oldStatus) && "PUBLISHED".equals(newStatus)) || ("PUBLISHED".equals(oldStatus) && "DRAFT".equals(newStatus)))) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "服务只能在草稿和已发布之间流转");
		if (!oldStatus.equals(newStatus) && body.has("enabled") && newEnabled) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "请先发布服务，再单独启用");
		if (oldStatus.equals("DRAFT") && body.has("enabled") && oldEnabled != newEnabled) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "草稿服务不能启用或禁用");
	}
	private ObjectNode touch(ObjectNode server) { server.put("revision", server.path("revision").asInt(1) + 1); server.put("updatedAt", Instant.now().toString()); persist(); return server.deepCopy(); }
	private boolean normalizeStates() {
		boolean changed = false;
		for (Iterator<JsonNode> iterator = servers().iterator(); iterator.hasNext();) {
			ObjectNode server = (ObjectNode) iterator.next(); String old = server.path("status").asText("DRAFT");
			if ("DEPRECATED".equals(old)) { iterator.remove(); changed = true; continue; }
			String normalized = "ENABLED".equals(old) || "DISABLED".equals(old) ? "PUBLISHED" : "VERIFIED".equals(old) ? "DRAFT" : old;
			boolean enabled = "ENABLED".equals(old) || ("PUBLISHED".equals(normalized) && server.path("enabled").asBoolean(false));
			if (!normalized.equals(old)) { server.put("status", normalized); changed = true; }
			if (server.path("enabled").asBoolean(false) != enabled) { server.put("enabled", enabled); changed = true; }
			if (!server.path("refreshRecords").isArray()) { server.putArray("refreshRecords"); changed = true; }
			for (JsonNode tool : server.withArray("tools")) if (!tool.has("enabled")) { ((ObjectNode) tool).put("enabled", false); changed = true; }
		}
		return changed;
	}
	private void persist() { persistDocument(document); }
	private void persistDocument(ObjectNode value) { try { Path tmp = documentPath.resolveSibling(documentPath.getFileName() + ".tmp"); Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8); Files.move(tmp, documentPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (IOException exception) { throw new IllegalStateException("无法写入 MCP 服务 JSON 文件", exception); } }
}
