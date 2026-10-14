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
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON 文件存储的 API 服务登记实现。定义与调用结果分离，后续可替换为数据库仓储。 */
@Service
public class ApiServiceStorage {

	private final Path documentPath;

	private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

	private ObjectNode document;

	public ApiServiceStorage(@Value("${xiyuan.workspace.dir}") String workspaceDir) {
		this.documentPath = Path.of(workspaceDir).resolve("service-integrations.json");
		this.document = load();
		if (normalizeStates()) persist();
	}

	public synchronized List<ObjectNode> list(String query, String status, String domainId) {
		List<ObjectNode> result = new ArrayList<>();
		for (JsonNode node : services()) {
			String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
			if (!q.isEmpty() && !contains(node.path("id").asText(), q) && !contains(node.path("name").asText(), q)
					&& !contains(node.path("definition").asText(), q)) {
				continue;
			}
			if (status != null && !status.isBlank() && !status.equals(node.path("status").asText())) {
				continue;
			}
			if (domainId != null && !domainId.isBlank() && !domainId.equals(node.path("domainId").asText())) {
				continue;
			}
			result.add(summary((ObjectNode) node));
		}
		return result;
	}

	public synchronized ObjectNode get(String id) {
		return findService(id).deepCopy();
	}

	public synchronized ObjectNode create(ObjectNode body) {
		String id = required(body, "id");
		if (findServiceOrNull(id) != null) {
			throw IntegrationStorageException.conflict("SI_ID_NOT_UNIQUE", "API 服务标识已存在: " + id);
		}
		validateService(body, id, false);
		ObjectNode service = body.deepCopy();
		setIfMissing(service, "revision", JsonNodeFactory.instance.numberNode(1));
		service.put("status", "DRAFT");
		service.put("enabled", false);
		setIfMissing(service, "createdAt", JsonNodeFactory.instance.textNode(Instant.now().toString()));
		setIfMissing(service, "updatedAt", JsonNodeFactory.instance.textNode(Instant.now().toString()));
		setIfMissing(service, "createdBy", JsonNodeFactory.instance.textNode("service-admin-local"));
		setIfMissing(service, "updatedBy", JsonNodeFactory.instance.textNode("service-admin-local"));
		setIfMissing(service, "endpoints", JsonNodeFactory.instance.arrayNode());
		setIfMissing(service, "auth", authDefaults());
		services().add(service);
		persist();
		return service.deepCopy();
	}

	public synchronized ObjectNode update(String id, ObjectNode body) {
		ObjectNode service = findService(id);
		checkRevision(service, body);
		validateRequestedState(service, body);
		for (String field : body.propertyNames()) {
			if (!List.of("id", "createdAt", "createdBy", "endpoints", "status", "enabled").contains(field)) {
				service.set(field, body.path(field).deepCopy());
			}
		}
		if (body.has("status")) service.put("status", body.path("status").asText());
		if (body.has("enabled")) service.put("enabled", body.path("enabled").asBoolean(false));
		validateService(service, id, "PUBLISHED".equals(service.path("status").asText()));
		if ("PUBLISHED".equals(service.path("status").asText())) for (JsonNode endpoint : service.withArray("endpoints")) validateEndpoint((ObjectNode) endpoint, endpoint.path("id").asText(), true);
		service.put("revision", service.path("revision").asInt(1) + 1);
		service.put("updatedAt", Instant.now().toString());
		service.put("updatedBy", "service-admin-local");
		persist();
		return service.deepCopy();
	}

	public synchronized ObjectNode publish(String id, ObjectNode body) {
		ObjectNode service = findService(id);
		checkRevision(service, body);
		if (!"DRAFT".equals(service.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有草稿服务可以发布");
		validateService(service, id, true);
		for (JsonNode endpoint : service.withArray("endpoints")) validateEndpoint((ObjectNode) endpoint, endpoint.path("id").asText(), true);
		service.put("status", "PUBLISHED");
		service.put("enabled", false);
		return touch(service);
	}

	public synchronized ObjectNode unpublish(String id, ObjectNode body) {
		ObjectNode service = findService(id);
		checkRevision(service, body);
		if (!"PUBLISHED".equals(service.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有已发布服务可以下架");
		service.put("status", "DRAFT");
		service.put("enabled", false);
		return touch(service);
	}

	public synchronized ObjectNode enable(String id, ObjectNode body) {
		ObjectNode service = findService(id);
		checkRevision(service, body);
		if (!"PUBLISHED".equals(service.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有已发布服务可以启用");
		service.put("enabled", true);
		return touch(service);
	}

	public synchronized ObjectNode disable(String id, ObjectNode body) {
		ObjectNode service = findService(id);
		checkRevision(service, body);
		if (!"PUBLISHED".equals(service.path("status").asText())) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "只有已发布服务可以禁用");
		service.put("enabled", false);
		return touch(service);
	}

	public synchronized ObjectNode delete(String id, ObjectNode body) {
		ObjectNode service = findService(id);
		checkRevision(service, body);
		if (!"DRAFT".equals(service.path("status").asText())) throw IntegrationStorageException.conflict("SI_DELETE_REQUIRES_DRAFT", "已发布服务请先下架再删除");
		for (int index = 0; index < services().size(); index++) if (services().get(index) == service) { services().remove(index); break; }
		persist();
		return service.deepCopy();
	}

	public synchronized List<ObjectNode> endpoints(String serviceId) {
		ArrayNode endpoints = findService(serviceId).withArray("endpoints");
		return endpoints.valueStream().map(node -> ((ObjectNode) node).deepCopy()).toList();
	}

	public synchronized ObjectNode createEndpoint(String serviceId, ObjectNode body) {
		ObjectNode service = findService(serviceId);
		String id = required(body, "id");
		if (findEndpointOrNull(service, id) != null) {
			throw IntegrationStorageException.conflict("SI_ID_NOT_UNIQUE", "端点标识已存在: " + id);
		}
		ObjectNode endpoint = body.deepCopy();
		setIfMissing(endpoint, "revision", JsonNodeFactory.instance.numberNode(1));
		setIfMissing(endpoint, "status", JsonNodeFactory.instance.textNode("DRAFT"));
		setIfMissing(endpoint, "enabled", JsonNodeFactory.instance.booleanNode(false));
		setIfMissing(endpoint, "operationNature", JsonNodeFactory.instance.textNode("READ"));
		setIfMissing(endpoint, "contract", defaultContract());
		setIfMissing(endpoint, "createdAt", JsonNodeFactory.instance.textNode(Instant.now().toString()));
		setIfMissing(endpoint, "updatedAt", JsonNodeFactory.instance.textNode(Instant.now().toString()));
		setIfMissing(endpoint, "lastTest", JsonNodeFactory.instance.nullNode());
		validateEndpoint(endpoint, id, false);
		service.withArray("endpoints").add(endpoint);
		service.put("revision", service.path("revision").asInt(1) + 1);
		service.put("updatedAt", Instant.now().toString());
		persist();
		return endpoint.deepCopy();
	}

	public synchronized ObjectNode updateEndpoint(String serviceId, String endpointId, ObjectNode body) {
		ObjectNode service = findService(serviceId);
		ObjectNode endpoint = findEndpoint(service, endpointId);
		checkRevision(endpoint, body);
		String requestedStatus = body.has("status") ? body.path("status").asText() : endpoint.path("status").asText("DRAFT");
		if (!List.of("DRAFT", "PUBLISHED").contains(requestedStatus)) throw IntegrationStorageException.badRequest("SI_INVALID_STATUS", "endpoint status must be DRAFT or PUBLISHED");
		boolean requestedEnabled = body.has("enabled") ? body.path("enabled").asBoolean(false) : endpoint.path("enabled").asBoolean(false);
		if ("DRAFT".equals(requestedStatus) && requestedEnabled) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "draft endpoint cannot be enabled");
		for (String field : body.propertyNames()) {
			if (!List.of("id", "createdAt", "createdBy").contains(field)) {
				endpoint.set(field, body.path(field).deepCopy());
			}
		}
		validateEndpoint(endpoint, endpointId, "PUBLISHED".equals(requestedStatus));
		endpoint.put("revision", endpoint.path("revision").asInt(1) + 1);
		endpoint.put("updatedAt", Instant.now().toString());
		service.put("revision", service.path("revision").asInt(1) + 1);
		service.put("updatedAt", Instant.now().toString());
		persist();
		return endpoint.deepCopy();
	}

	public synchronized ObjectNode deleteEndpoint(String serviceId, String endpointId) {
		ObjectNode service = findService(serviceId);
		ObjectNode endpoint = findEndpoint(service, endpointId);
		ArrayNode endpointArray = service.withArray("endpoints");
		for (int index = 0; index < endpointArray.size(); index++) if (endpointArray.get(index) == endpoint) { endpointArray.remove(index); break; }
		endpoint.put("revision", endpoint.path("revision").asInt(1) + 1);
		service.put("revision", service.path("revision").asInt(1) + 1);
		service.put("updatedAt", Instant.now().toString());
		persist();
		return endpoint.deepCopy();
	}

	public synchronized ObjectNode testEndpoint(String serviceId, String endpointId) {
		ObjectNode service = findService(serviceId);
		ObjectNode endpoint = findEndpoint(service, endpointId);
		long started = System.nanoTime();
		String outcome = "FAILED";
		String category = "NETWORK_UNREACHABLE";
		String message = "无法连接到目标服务";
		try {
			String url = service.path("baseUrl").asText().replaceAll("/+$", "") + "/" + endpoint.path("path").asText().replaceFirst("^/", "");
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofMillis(Math.min(60000, Math.max(1, service.path("timeoutMs").asLong(10000)))))
					.method(endpoint.path("method").asText("GET"), HttpRequest.BodyPublishers.noBody())
					.build();
			HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (status >= 200 && status < 300) {
				outcome = "SUCCESS";
				category = "NONE";
				message = "连接测试通过";
			}
			else if (status == 401 || status == 403) {
				category = "AUTHENTICATION_FAILED";
				message = "目标服务拒绝认证";
			}
			else {
				category = status >= 500 ? "SERVER_ERROR" : "CLIENT_ERROR";
				message = "目标服务返回 HTTP " + status;
			}
		}
		catch (java.net.http.HttpTimeoutException exception) {
			category = "TIMEOUT";
			message = "连接测试超时";
		}
		catch (Exception exception) {
			category = "NETWORK_UNREACHABLE";
		}
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("outcome", outcome);
		result.put("category", category);
		result.put("message", message);
		result.put("latencyMs", (System.nanoTime() - started) / 1_000_000);
		result.put("at", Instant.now().toString());
		result.put("by", "service-admin-local");
		endpoint.set("lastTest", result);
		service.put("updatedAt", Instant.now().toString());
		persist();
		return result;
	}

	public synchronized ArrayNode importOpenApi(String serviceId, ObjectNode source) {
		ObjectNode service = findService(serviceId);
		return parseOpenApi(source, service);
	}

	public synchronized ObjectNode importService(ObjectNode source, ObjectNode serviceBody) {
		if (!source.path("openapi").asText().matches("3\\.0\\.\\d+")) {
			throw IntegrationStorageException.badRequest("SI_OPENAPI_VERSION_UNSUPPORTED", "仅支持 OpenAPI 3.0.x 文档");
		}
		ObjectNode info = source.path("info").isObject() ? (ObjectNode) source.path("info") : JsonNodeFactory.instance.objectNode();
		ObjectNode body = serviceBody.deepCopy();
		setIfMissing(body, "id", JsonNodeFactory.instance.textNode(generatedId(info.path("title").asText("IMPORTED"))));
		setIfMissing(body, "name", JsonNodeFactory.instance.textNode(info.path("title").asText("导入 API 服务")));
		setIfMissing(body, "definition", JsonNodeFactory.instance.textNode(info.path("description").asText("从 OpenAPI 文档导入")));
		setIfMissing(body, "version", JsonNodeFactory.instance.textNode(info.path("version").asText("v1")));
		setIfMissing(body, "baseUrl", JsonNodeFactory.instance.textNode(source.path("servers").path(0).path("url").asText("")));
		setIfMissing(body, "environment", JsonNodeFactory.instance.textNode("测试"));
		setIfMissing(body, "domainId", JsonNodeFactory.instance.textNode("DOMAIN-OPS"));
		setIfMissing(body, "owner", JsonNodeFactory.instance.textNode("服务接入管理员"));
		setIfMissing(body, "timeoutMs", JsonNodeFactory.instance.numberNode(10000));
		ObjectNode created = create(body);
		ObjectNode stored = findService(body.path("id").asText());
		ArrayNode drafts = parseOpenApi(source, stored);
		stored.set("endpoints", drafts);
		created.set("endpoints", drafts.deepCopy());
		persist();
		return created.deepCopy();
	}

	private ArrayNode parseOpenApi(ObjectNode source, ObjectNode service) {
		if (!source.path("openapi").asText().matches("3\\.0\\.\\d+")) {
			throw IntegrationStorageException.badRequest("SI_OPENAPI_VERSION_UNSUPPORTED", "仅支持 OpenAPI 3.0.x 文档");
		}
		if (!source.path("paths").isObject()) {
			throw IntegrationStorageException.badRequest("SI_OPENAPI_INVALID", "OpenAPI 文档缺少有效的 paths 节点");
		}
		ArrayNode drafts = JsonNodeFactory.instance.arrayNode();
		for (String path : source.path("paths").propertyNames()) {
			JsonNode pathItem = source.path("paths").path(path);
			for (String method : List.of("get", "post", "put", "patch", "delete", "head", "options", "trace")) {
				JsonNode operation = pathItem.path(method);
				if (!operation.isObject()) continue;
				ObjectNode endpoint = JsonNodeFactory.instance.objectNode();
				endpoint.put("id", "EP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
				endpoint.put("name", operation.path("summary").asText(operation.path("operationId").asText(method.toUpperCase(Locale.ROOT) + " " + path)));
				endpoint.put("definition", operation.path("description").asText(operation.path("summary").asText("来自 OpenAPI 文档的接口定义")));
				endpoint.put("method", method.toUpperCase(Locale.ROOT));
				endpoint.put("path", path);
				endpoint.put("operationNature", Set.of("post", "put", "patch", "delete").contains(method) ? "WRITE" : "READ");
				endpoint.put("timeoutMs", service.path("timeoutMs").asInt(10000));
				endpoint.put("status", "DRAFT");
				endpoint.put("enabled", false);
				ObjectNode contract = endpoint.putObject("contract");
				ArrayNode parameters = JsonNodeFactory.instance.arrayNode();
				if (pathItem.path("parameters").isArray()) pathItem.path("parameters").forEach(parameters::add);
				if (operation.path("parameters").isArray()) operation.path("parameters").forEach(parameters::add);
				contract.set("parameters", parameters);
				contract.set("requestBody", operation.path("requestBody").isObject() ? operation.path("requestBody").deepCopy() : JsonNodeFactory.instance.objectNode());
				contract.set("responses", operation.path("responses").isObject() ? operation.path("responses").deepCopy() : JsonNodeFactory.instance.objectNode());
				endpoint.put("revision", 1);
				endpoint.putNull("lastTest");
				drafts.add(endpoint);
			}
		}
		return drafts;
	}

	private ObjectNode load() {
		try {
			Files.createDirectories(documentPath.getParent());
			if (Files.exists(documentPath)) {
				JsonNode node = mapper.readTree(Files.readString(documentPath));
				if (node instanceof ObjectNode object && object.path("kind").asText().equals("service-integration")) return object;
			}
			ObjectNode seed = JsonNodeFactory.instance.objectNode();
			seed.put("schemaVersion", "1.0");
			seed.put("kind", "service-integration");
			seed.put("workspace", "ENV-OM-001");
			seed.putObject("meta").put("generatedAt", Instant.now().toString());
			ArrayNode services = seed.putArray("apiServices");
			ObjectNode sample = services.addObject();
			sample.put("id", "API-ITSM").put("name", "工单查询服务").put("definition", "提供工单查询与状态读取能力").put("domainId", "DOMAIN-OPS").put("owner", "运维平台团队").put("source", "FIX-SI-001 合成端点").put("version", "v1").put("baseUrl", "http://127.0.0.1:18080").put("environment", "测试").put("timeoutMs", 5000).put("status", "DRAFT").put("revision", 1).put("createdAt", Instant.now().toString());
			sample.set("auth", authDefaults());
			ArrayNode endpoints = sample.putArray("endpoints");
			ObjectNode endpoint = endpoints.addObject();
			endpoint.put("id", "EP-TICKET-LIST").put("name", "查询工单").put("definition", "按服务和状态查询工单").put("method", "GET").put("path", "/api/tickets").put("operationNature", "READ").put("timeoutMs", 5000).put("status", "DRAFT").put("revision", 1).putNull("lastTest");
			ObjectNode contract = endpoint.putObject("contract");
			ObjectNode parameter = contract.putArray("parameters").addObject();
			parameter.put("name", "status").put("in", "query").put("type", "string").put("required", false);
			contract.putObject("requestBody");
			contract.putObject("responses").putObject("200").put("description", "成功");
			persistDocument(seed);
			return seed;
		}
		catch (IOException exception) {
			throw new IllegalStateException("无法读取服务接入 JSON 文件", exception);
		}
	}

	private ArrayNode services() { return document.withArray("apiServices"); }

	private ObjectNode findService(String id) {
		ObjectNode service = findServiceOrNull(id);
		if (service == null) throw IntegrationStorageException.notFound("SI_SERVICE_NOT_FOUND", "API 服务不存在: " + id);
		return service;
	}

	private ObjectNode findServiceOrNull(String id) {
		for (JsonNode node : services()) if (id.equals(node.path("id").asText())) return (ObjectNode) node;
		return null;
	}

	private ObjectNode findEndpoint(ObjectNode service, String id) {
		ObjectNode endpoint = findEndpointOrNull(service, id);
		if (endpoint == null) throw IntegrationStorageException.notFound("SI_ENDPOINT_NOT_FOUND", "端点不存在: " + id);
		return endpoint;
	}

	private ObjectNode findEndpointOrNull(ObjectNode service, String id) {
		for (JsonNode node : service.withArray("endpoints")) if (id.equals(node.path("id").asText())) return (ObjectNode) node;
		return null;
	}

	private void validateService(ObjectNode body, String id, boolean complete) {
		List<String> fields = List.of("name", "definition", "baseUrl", "version", "environment", "domainId", "owner");
		if (complete) {
			List<String> missing = fields.stream().filter(field -> body.path(field).asText().isBlank()).toList();
			if (!missing.isEmpty()) throw IntegrationStorageException.badRequest("SI_REQUIRED_MISSING", "服务发布前缺少必填字段: " + String.join(", ", missing));
		}
		if (!body.path("baseUrl").asText().isBlank() && !body.path("baseUrl").asText().startsWith("http")) throw IntegrationStorageException.badRequest("SI_INVALID_URL", "服务地址必须使用 HTTP/HTTPS");
		if (!List.of("DRAFT", "PUBLISHED").contains(body.path("status").asText("DRAFT"))) throw IntegrationStorageException.badRequest("SI_INVALID_STATUS", "不支持的 API 服务状态");
	}

	private void validateEndpoint(ObjectNode body, String id, boolean complete) {
		if (complete) for (String field : List.of("name", "definition", "method", "path")) if (body.path(field).asText().isBlank()) throw IntegrationStorageException.badRequest("SI_REQUIRED_MISSING", "端点发布前缺少必填字段: " + field);
		if (!body.path("path").asText().isBlank() && !body.path("path").asText().startsWith("/")) throw IntegrationStorageException.badRequest("SI_INVALID_PATH", "端点路径必须以 / 开头");
	}

	private static String required(ObjectNode body, String field) { if (body.path(field).asText().isBlank()) throw IntegrationStorageException.badRequest("SI_REQUIRED_MISSING", "缺少必填字段: " + field); return body.path(field).asText(); }

	private static boolean contains(String value, String query) { return value.toLowerCase(Locale.ROOT).contains(query); }

	private static ObjectNode authDefaults() { return JsonNodeFactory.instance.objectNode().put("authType", "NONE").put("configured", false).put("masked", "未配置"); }

	private static ObjectNode defaultContract() {
		ObjectNode contract = JsonNodeFactory.instance.objectNode();
		contract.putArray("parameters");
		contract.putObject("requestBody");
		contract.putObject("responses").putObject("200").put("description", "成功");
		return contract;
	}

	private static String generatedId(String title) { String normalized = title.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-|-$", ""); return "API-" + (normalized.isBlank() ? "IMPORTED" : normalized); }

	private static void setIfMissing(ObjectNode target, String field, JsonNode value) {
		if (!target.has(field)) target.set(field, value);
	}

	private static void checkRevision(ObjectNode current, ObjectNode body) { if (body != null && body.has("revision") && body.path("revision").asInt() != current.path("revision").asInt()) throw IntegrationStorageException.conflict("REVISION_CONFLICT", "定义已被他人修订，请刷新后重试"); }

	private static void validateRequestedState(ObjectNode current, ObjectNode body) {
		if (body.has("status") && !List.of("DRAFT", "PUBLISHED").contains(body.path("status").asText())) throw IntegrationStorageException.badRequest("SI_INVALID_STATUS", "状态只能是草稿或已发布");
		String oldStatus = current.path("status").asText("DRAFT");
		String newStatus = body.has("status") ? body.path("status").asText() : oldStatus;
		boolean oldEnabled = current.path("enabled").asBoolean(false);
		boolean newEnabled = body.has("enabled") ? body.path("enabled").asBoolean(false) : oldEnabled;
		if ("DRAFT".equals(newStatus) && newEnabled) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "草稿服务不能启用");
		if (!oldStatus.equals(newStatus) && !(("DRAFT".equals(oldStatus) && "PUBLISHED".equals(newStatus)) || ("PUBLISHED".equals(oldStatus) && "DRAFT".equals(newStatus)))) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "服务只能在草稿和已发布之间流转");
		if (!oldStatus.equals(newStatus) && body.has("enabled") && newEnabled) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "请先发布服务，再单独启用");
		if (oldStatus.equals(newStatus) && oldStatus.equals("DRAFT") && body.has("enabled") && oldEnabled != newEnabled) throw IntegrationStorageException.conflict("SI_INVALID_TRANSITION", "草稿服务不能启用或禁用");
	}

	private ObjectNode touch(ObjectNode service) {
		service.put("revision", service.path("revision").asInt(1) + 1);
		service.put("updatedAt", Instant.now().toString());
		service.put("updatedBy", "service-admin-local");
		persist();
		return service.deepCopy();
	}

	private boolean normalizeStates() {
		boolean changed = false;
		for (Iterator<JsonNode> iterator = services().iterator(); iterator.hasNext();) {
			ObjectNode service = (ObjectNode) iterator.next();
			String old = service.path("status").asText("DRAFT");
			if ("DEPRECATED".equals(old)) { iterator.remove(); changed = true; continue; }
			String normalized = "ENABLED".equals(old) || "DISABLED".equals(old) ? "PUBLISHED" : "DRAFT".equals(old) || "VERIFIED".equals(old) ? "DRAFT" : old;
			boolean enabled = "ENABLED".equals(old) || ("PUBLISHED".equals(normalized) && service.path("enabled").asBoolean(false));
			if (!normalized.equals(old)) { service.put("status", normalized); changed = true; }
			if (service.path("enabled").asBoolean(false) != enabled) { service.put("enabled", enabled); changed = true; }
			for (Iterator<JsonNode> endpointIterator = service.withArray("endpoints").iterator(); endpointIterator.hasNext();) {
				ObjectNode endpoint = (ObjectNode) endpointIterator.next();
				String endpointOld = endpoint.path("status").asText("DRAFT");
				if ("DEPRECATED".equals(endpointOld)) { endpointIterator.remove(); changed = true; continue; }
				String endpointNormalized = "ENABLED".equals(endpointOld) || "DISABLED".equals(endpointOld) || "VERIFIED".equals(endpointOld) ? "PUBLISHED" : "DRAFT";
				if (!endpointNormalized.equals(endpointOld)) { endpoint.put("status", endpointNormalized); changed = true; }
				if (!endpoint.has("enabled")) { endpoint.put("enabled", "ENABLED".equals(endpointOld)); changed = true; }
			}
		}
		return changed;
	}

	private static ObjectNode summary(ObjectNode service) { ObjectNode item = service.deepCopy(); ArrayNode endpoints = item.withArray("endpoints"); item.remove("endpoints"); item.put("endpointCount", endpoints.size()); item.put("verifiedEndpointCount", (int) endpoints.valueStream().filter(n -> "SUCCESS".equals(n.path("lastTest").path("outcome").asText())).count()); item.set("endpoints", endpoints); return item; }

	private void persist() { persistDocument(document); }

	private void persistDocument(ObjectNode value) {
		try {
			Path tmp = documentPath.resolveSibling(documentPath.getFileName() + ".tmp");
			Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
			Files.move(tmp, documentPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException exception) { throw new IllegalStateException("无法写入服务接入 JSON 文件", exception); }
	}
}
