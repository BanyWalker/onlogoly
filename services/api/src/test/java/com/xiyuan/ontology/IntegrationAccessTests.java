package com.xiyuan.ontology;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationAccessTests {

	private static final Path WORKSPACE = Path.of("target", "test-workspaces", "integration-access-" + UUID.randomUUID());
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@Value("${local.server.port}")
	int port;

	@Autowired
	ObjectMapper mapper;

	@DynamicPropertySource
	static void workspace(DynamicPropertyRegistry registry) {
		registry.add("xiyuan.workspace.dir", () -> WORKSPACE.toAbsolutePath().toString());
	}

	@ParameterizedTest
	@ValueSource(strings = { "api-services", "mcp-servers" })
	void serviceListsAreAvailableWithoutAuthentication(String resource) throws Exception {
		var response = get("/api/v1/integrations/" + resource);
		assertEquals(200, response.statusCode(), response.body());
		var body = mapper.readTree(response.body());
		assertTrue(body.hasNonNull("requestId"));
		assertTrue(body.path("data").isArray());
	}

	@ParameterizedTest
	@ValueSource(strings = { "api-services", "mcp-servers" })
	void missingServicesKeepTheirNotFoundResponse(String resource) throws Exception {
		var response = get("/api/v1/integrations/" + resource + "/does-not-exist");
		assertEquals(404, response.statusCode(), response.body());
		assertTrue(mapper.readTree(response.body()).hasNonNull("code"));
	}

	@Test
	void missingApiRouteIsNotTurnedIntoAnAuthenticationError() throws Exception {
		var response = get("/api/v1/integrations/does-not-exist");
		assertEquals(404, response.statusCode(), response.body());
		assertEquals(404, mapper.readTree(response.body()).path("status").asInt());
	}

	@Test
	void servicesMustBePublishedBeforeTheyCanBeEnabled() throws Exception {
		var apiPublish = post("/api/v1/integrations/api-services/API-ITSM/publish", "{}");
		assertEquals(200, apiPublish.statusCode(), apiPublish.body());
		var apiEnable = post("/api/v1/integrations/api-services/API-ITSM/enable", "{}");
		assertEquals(200, apiEnable.statusCode(), apiEnable.body());
		assertEquals("PUBLISHED", mapper.readTree(apiEnable.body()).path("data").path("status").asText());
		assertTrue(mapper.readTree(apiEnable.body()).path("data").path("enabled").asBoolean());

		var mcpPublish = post("/api/v1/integrations/mcp-servers/MCP-CMDB/publish", "{}");
		assertEquals(200, mcpPublish.statusCode(), mcpPublish.body());
		var mcpEnable = post("/api/v1/integrations/mcp-servers/MCP-CMDB/enable", "{}");
		assertEquals(200, mcpEnable.statusCode(), mcpEnable.body());
		assertEquals("PUBLISHED", mapper.readTree(mcpEnable.body()).path("data").path("status").asText());
		assertTrue(mapper.readTree(mcpEnable.body()).path("data").path("enabled").asBoolean());
	}

	@Test
	void mcpRefreshReportsRemoteFailureInsteadOfReportingSuccess() throws Exception {
		var before = mapper.readTree(get("/api/v1/integrations/mcp-servers/MCP-CMDB").body()).path("data");
		var response = post("/api/v1/integrations/mcp-servers/MCP-CMDB/refresh", "{}");
		assertEquals(200, response.statusCode(), response.body());
		var result = mapper.readTree(response.body()).path("data");
		assertEquals("FAILED", result.path("outcome").asText());
		assertTrue(result.path("normalizedError").isTextual(), response.body());
		var after = mapper.readTree(get("/api/v1/integrations/mcp-servers/MCP-CMDB").body()).path("data");
		assertEquals(before.path("revision").asInt(), after.path("revision").asInt());
		assertEquals(before.path("tools"), after.path("tools"));
		assertEquals(before.path("snapshots"), after.path("snapshots"));
		assertEquals(before.path("currentSnapshotId"), after.path("currentSnapshotId"));
		assertTrue(after.path("refreshRecords").size() > before.path("refreshRecords").size());
		assertEquals("FAILED", after.path("refreshRecords").get(after.path("refreshRecords").size() - 1).path("outcome").asText());
	}

	@Test
	void draftsMayBeSavedIncompleteButFollowThePublishLifecycle() throws Exception {
		String apiId = "API-DRAFT-" + UUID.randomUUID().toString().substring(0, 8);
		var apiCreate = post("/api/v1/integrations/api-services", "{\"id\":\"" + apiId + "\"}");
		assertEquals(200, apiCreate.statusCode(), apiCreate.body());
		assertEquals("DRAFT", mapper.readTree(apiCreate.body()).path("data").path("status").asText());
		assertEquals(400, post("/api/v1/integrations/api-services/" + apiId + "/publish", "{}").statusCode());
		assertEquals(409, post("/api/v1/integrations/api-services/" + apiId + "/enable", "{}").statusCode());
		assertEquals(200, delete("/api/v1/integrations/api-services/" + apiId).statusCode());

		String mcpId = "MCP-DRAFT-" + UUID.randomUUID().toString().substring(0, 8);
		var mcpCreate = post("/api/v1/integrations/mcp-servers", "{\"id\":\"" + mcpId + "\"}");
		assertEquals(200, mcpCreate.statusCode(), mcpCreate.body());
		assertEquals("DRAFT", mapper.readTree(mcpCreate.body()).path("data").path("status").asText());
		assertEquals(400, post("/api/v1/integrations/mcp-servers/" + mcpId + "/publish", "{}").statusCode());
		assertEquals(409, post("/api/v1/integrations/mcp-servers/" + mcpId + "/enable", "{}").statusCode());
		assertEquals(200, delete("/api/v1/integrations/mcp-servers/" + mcpId).statusCode());
	}

	@Test
	void publishedServicesMustBeUnpublishedBeforeDeletion() throws Exception {
		String apiId = "API-ROUNDTRIP-" + UUID.randomUUID().toString().substring(0, 8);
		String apiBody = "{\"id\":\"" + apiId + "\",\"name\":\"Round trip API\",\"definition\":\"Lifecycle test\",\"baseUrl\":\"https://example.invalid\",\"version\":\"v1\",\"environment\":\"test\",\"domainId\":\"DOMAIN-OPS\",\"owner\":\"integration-test\"}";
		assertEquals(200, post("/api/v1/integrations/api-services", apiBody).statusCode());
		assertEquals(200, post("/api/v1/integrations/api-services/" + apiId + "/publish", "{}").statusCode());
		assertEquals(409, delete("/api/v1/integrations/api-services/" + apiId).statusCode());
		assertEquals(200, post("/api/v1/integrations/api-services/" + apiId + "/unpublish", "{}").statusCode());
		assertEquals(200, delete("/api/v1/integrations/api-services/" + apiId).statusCode());

		String mcpId = "MCP-ROUNDTRIP-" + UUID.randomUUID().toString().substring(0, 8);
		String mcpBody = "{\"id\":\"" + mcpId + "\",\"name\":\"Round trip MCP\",\"definition\":\"Lifecycle test\",\"endpointUrl\":\"https://example.invalid/mcp\",\"transport\":\"HTTP\",\"domainId\":\"DOMAIN-OPS\",\"owner\":\"integration-test\"}";
		assertEquals(200, post("/api/v1/integrations/mcp-servers", mcpBody).statusCode());
		assertEquals(200, post("/api/v1/integrations/mcp-servers/" + mcpId + "/publish", "{}").statusCode());
		assertEquals(409, delete("/api/v1/integrations/mcp-servers/" + mcpId).statusCode());
		assertEquals(200, post("/api/v1/integrations/mcp-servers/" + mcpId + "/unpublish", "{}").statusCode());
		assertEquals(200, delete("/api/v1/integrations/mcp-servers/" + mcpId).statusCode());
	}

	@Test
	void directRequestsOutsidePublicRoutesRemainDenied() throws Exception {
		assertEquals(401, get("/error").statusCode());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
			.timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> patch(String path, String body) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
			.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
			.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String body) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
			.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> delete(String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
			.timeout(Duration.ofSeconds(10)).method("DELETE", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
	}

}
