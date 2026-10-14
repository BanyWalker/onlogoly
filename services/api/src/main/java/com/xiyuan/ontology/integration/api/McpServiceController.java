package com.xiyuan.ontology.integration.api;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xiyuan.ontology.integration.storage.McpServiceStorage;

import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/integrations")
public class McpServiceController {

	private final McpServiceStorage storage;

	public McpServiceController(McpServiceStorage storage) {
		this.storage = storage;
	}

	@GetMapping("/mcp-servers")
	public Map<String, Object> list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String domainId) {
		return IntegrationResponses.ok(storage.list(q, status, domainId));
	}

	@GetMapping("/mcp-servers/{id}")
	public Map<String, Object> get(@PathVariable String id) {
		return IntegrationResponses.ok(storage.get(id));
	}

	@PostMapping("/mcp-servers")
	public Map<String, Object> create(@RequestBody ObjectNode body) {
		return IntegrationResponses.ok(storage.create(body));
	}

	@PatchMapping("/mcp-servers/{id}")
	public Map<String, Object> update(@PathVariable String id, @RequestBody ObjectNode body) {
		return IntegrationResponses.ok(storage.update(id, body));
	}

	@PostMapping("/mcp-servers/{id}/publish")
	public Map<String, Object> publish(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.publish(id, body)); }

	@PostMapping("/mcp-servers/{id}/unpublish")
	public Map<String, Object> unpublish(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.unpublish(id, body)); }

	@PostMapping("/mcp-servers/{id}/enable")
	public Map<String, Object> enable(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.enable(id, body)); }

	@PostMapping("/mcp-servers/{id}/disable")
	public Map<String, Object> disable(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.disable(id, body)); }

	@DeleteMapping("/mcp-servers/{id}")
	public Map<String, Object> delete(@PathVariable String id) {
		return IntegrationResponses.ok(storage.delete(id));
	}

	@PostMapping("/mcp-servers/{id}/discover")
	public Map<String, Object> discover(@PathVariable String id) {
		return IntegrationResponses.ok(storage.discover(id));
	}

	@PostMapping("/mcp-servers/{id}/refresh")
	public Map<String, Object> refresh(@PathVariable String id) {
		return IntegrationResponses.ok(storage.refresh(id));
	}

	@GetMapping("/mcp-servers/{id}/tools")
	public Map<String, Object> tools(@PathVariable String id) {
		return IntegrationResponses.ok(storage.tools(id));
	}

	@PatchMapping("/mcp-servers/{id}/tools/{toolKey}")
	public Map<String, Object> updateTool(@PathVariable String id, @PathVariable String toolKey, @RequestBody ObjectNode body) {
		return IntegrationResponses.ok(storage.updateTool(id, toolKey, body));
	}

	@PostMapping("/mcp-servers/{id}/tools/{toolKey}/review")
	public Map<String, Object> reviewTool(@PathVariable String id, @PathVariable String toolKey) {
		return IntegrationResponses.ok(storage.reviewTool(id, toolKey));
	}

	@PostMapping("/mcp-servers/{id}/tools/{toolKey}/invoke")
	public Map<String, Object> invoke(@PathVariable String id, @PathVariable String toolKey, @RequestBody ObjectNode body) {
		return IntegrationResponses.ok(storage.invoke(id, toolKey, body));
	}

	@GetMapping("/mcp-servers/{id}/snapshots")
	public Map<String, Object> snapshots(@PathVariable String id) {
		return IntegrationResponses.ok(storage.snapshots(id));
	}

	@GetMapping("/call-log")
	public Map<String, Object> logs(@RequestParam(required = false) String serverId,
			@RequestParam(required = false) String resultStatus) {
		return IntegrationResponses.ok(storage.logs(serverId, resultStatus));
	}

	@GetMapping("/mappings")
	public Map<String, Object> mappings(@RequestParam(required = false) String serverId,
			@RequestParam(required = false) String targetRef) {
		return IntegrationResponses.ok(storage.mappings(serverId, targetRef));
	}

	@PostMapping("/mappings")
	public Map<String, Object> createMapping(@RequestBody ObjectNode body) {
		return IntegrationResponses.ok(storage.createMapping(body));
	}

	@PatchMapping("/mappings/{id}")
	public Map<String, Object> updateMapping(@PathVariable String id, @RequestBody ObjectNode body) {
		return IntegrationResponses.ok(storage.updateMapping(id, body));
	}

	@DeleteMapping("/mappings/{id}")
	public Map<String, Object> deleteMapping(@PathVariable String id) {
		return IntegrationResponses.ok(storage.deleteMapping(id));
	}
}
