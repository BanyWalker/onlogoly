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

import com.xiyuan.ontology.integration.storage.ApiServiceStorage;

import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/integrations/api-services")
public class ApiServiceController {
	private final ApiServiceStorage storage;

	public ApiServiceController(ApiServiceStorage storage) { this.storage = storage; }

	@GetMapping public Map<String, Object> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) String domainId) { return IntegrationResponses.ok(storage.list(q, status, domainId)); }
	@GetMapping("/{id}") public Map<String, Object> get(@PathVariable String id) { return IntegrationResponses.ok(storage.get(id)); }
	@PostMapping public Map<String, Object> create(@RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.create(body)); }
	@PatchMapping("/{id}") public Map<String, Object> update(@PathVariable String id, @RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.update(id, body)); }
	@PostMapping("/{id}/publish") public Map<String, Object> publish(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.publish(id, body)); }
	@PostMapping("/{id}/unpublish") public Map<String, Object> unpublish(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.unpublish(id, body)); }
	@PostMapping("/{id}/enable") public Map<String, Object> enable(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.enable(id, body)); }
	@PostMapping("/{id}/disable") public Map<String, Object> disable(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.disable(id, body)); }
	@DeleteMapping("/{id}") public Map<String, Object> delete(@PathVariable String id, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.delete(id, body)); }
	@GetMapping("/{id}/endpoints") public Map<String, Object> endpoints(@PathVariable String id) { return IntegrationResponses.ok(storage.endpoints(id)); }
	@PostMapping("/{id}/endpoints") public Map<String, Object> createEndpoint(@PathVariable String id, @RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.createEndpoint(id, body)); }
	@PatchMapping("/{id}/endpoints/{endpointId}") public Map<String, Object> updateEndpoint(@PathVariable String id, @PathVariable String endpointId, @RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.updateEndpoint(id, endpointId, body)); }
	@DeleteMapping("/{id}/endpoints/{endpointId}") public Map<String, Object> deleteEndpoint(@PathVariable String id, @PathVariable String endpointId) { return IntegrationResponses.ok(storage.deleteEndpoint(id, endpointId)); }
	@PostMapping("/{id}/endpoints/{endpointId}/test") public Map<String, Object> test(@PathVariable String id, @PathVariable String endpointId, @RequestBody(required = false) ObjectNode body) { return IntegrationResponses.ok(storage.testEndpoint(id, endpointId, body)); }
	@GetMapping("/{id}/snapshots") public Map<String, Object> snapshots(@PathVariable String id) { return IntegrationResponses.ok(storage.snapshots(id)); }
	@PostMapping("/{id}/snapshots") public Map<String, Object> createSnapshot(@PathVariable String id) { return IntegrationResponses.ok(storage.createSnapshot(id)); }
	@GetMapping("/{id}/call-log") public Map<String, Object> callLogs(@PathVariable String id) { return IntegrationResponses.ok(storage.callLogs(id)); }
	@GetMapping("/call-log") public Map<String, Object> callLogsByService(@RequestParam(required = false) String serviceId) { return IntegrationResponses.ok(storage.callLogs(serviceId)); }
	@GetMapping("/api-mappings") public Map<String, Object> mappings(@RequestParam(required = false) String serviceId, @RequestParam(required = false) String targetRef) { return IntegrationResponses.ok(storage.mappings(serviceId, targetRef)); }
	@PostMapping("/api-mappings") public Map<String, Object> createMapping(@RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.createMapping(body)); }
	@PatchMapping("/api-mappings/{mappingId}") public Map<String, Object> updateMapping(@PathVariable String mappingId, @RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.updateMapping(mappingId, body)); }
	@DeleteMapping("/api-mappings/{mappingId}") public Map<String, Object> deleteMapping(@PathVariable String mappingId) { return IntegrationResponses.ok(storage.deleteMapping(mappingId)); }
	@GetMapping("/mappings") public Map<String, Object> mappingsByService(@RequestParam(required = false) String serviceId, @RequestParam(required = false) String targetRef) { return IntegrationResponses.ok(storage.mappings(serviceId, targetRef)); }
	@PostMapping("/mappings") public Map<String, Object> createMappingAlias(@RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.createMapping(body)); }
	@PatchMapping("/mappings/{mappingId}") public Map<String, Object> updateMappingAlias(@PathVariable String mappingId, @RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.updateMapping(mappingId, body)); }
	@DeleteMapping("/mappings/{mappingId}") public Map<String, Object> deleteMappingAlias(@PathVariable String mappingId) { return IntegrationResponses.ok(storage.deleteMapping(mappingId)); }
	@PostMapping("/{id}/openapi-import") public Map<String, Object> openApiImport(@PathVariable String id, @RequestBody ObjectNode body) { return IntegrationResponses.ok(storage.importOpenApi(id, body)); }
	@PostMapping("/import") public Map<String, Object> importService(@RequestBody ObjectNode body) { ObjectNode source = body.path("document").isObject() ? (ObjectNode) body.path("document") : body; ObjectNode options = body.path("service").isObject() ? (ObjectNode) body.path("service") : tools.jackson.databind.node.JsonNodeFactory.instance.objectNode(); return IntegrationResponses.ok(storage.importService(source, options)); }
}
