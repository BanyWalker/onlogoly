package com.xiyuan.ontology;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = { "logging.level.root=WARN", "debug=false" })
class ObjectFactHttpSmokeTests {
	private static final Path WORKSPACE = Path.of("target/test-workspaces/fact-http-" + UUID.randomUUID()).toAbsolutePath();
	@DynamicPropertySource static void workspace(DynamicPropertyRegistry registry) { registry.add("xiyuan.workspace.dir", WORKSPACE::toString); }
	@Value("${local.server.port}") int port;
	private final JsonMapper mapper = JsonMapper.builder().build();
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@Test void defaultModelCatalogCreateEditGraphDeleteRoundTripOverRealHttp() throws Exception {
		JsonNode catalog = request("GET", "/object-types", null, -1, 200);
		JsonNode type = catalog.path("types").get(0); ObjectNode body = mapper.createObjectNode(); body.put("objectTypeRef", type.path("id").asString());
		ObjectNode values = body.putObject("propertyValues"); String editable = null;
		for (JsonNode attribute : type.path("attributes")) {
			String name = attribute.path("name").asString(); String dataType = attribute.path("dataType").asString();
			if ("Reference".equals(dataType)) continue;
			switch (dataType) {
				case "Enumeration" -> values.put(name, attribute.path("allowedValues").get(0).path("code").asString());
				case "Integer", "Decimal" -> values.put(name, 1);
				case "Boolean" -> values.put(name, false);
				case "DateTime" -> values.put(name, "2026-10-15T10:00:00Z");
				default -> { values.put(name, "HTTP-" + name); if (attribute.path("readOnlyNature").asString("").isBlank()) editable = name; }
			}
		}
		JsonNode created = request("POST", "/instances", body, catalog.path("revision").asLong(), 200);
		String id = created.path("instanceId").asString(); assertTrue(id.startsWith("INST-")); assertNotNull(editable);
		ObjectNode edit = mapper.createObjectNode(); edit.putObject("propertyValues").put(editable, "HTTP-UPDATED");
		JsonNode saved = request("PATCH", "/instances/" + id, edit, created.path("revision").asLong(), 200);
		assertEquals("HTTP-UPDATED", request("GET", "/instances/" + id, null, -1, 200).path("propertyValues").path(editable).asString());
		assertEquals(1, request("GET", "/graph", null, -1, 200).path("nodes").size());
		request("DELETE", "/instances/" + id, null, saved.path("revision").asLong(), 200);
		assertEquals(0, request("GET", "/instances", null, -1, 200).path("total").asInt());
	}
	private JsonNode request(String method, String path, ObjectNode body, long revision, int expected) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/facts" + path)).timeout(Duration.ofSeconds(10));
		builder.header("X-Xiyuan-Role", "MODELER"); if (revision >= 0) builder.header("If-Match", Long.toString(revision));
		if (body != null) builder.header("Content-Type", "application/json");
		builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
		var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); assertEquals(expected, response.statusCode(), response.body());
		return mapper.readTree(response.body()).path("data");
	}
}
