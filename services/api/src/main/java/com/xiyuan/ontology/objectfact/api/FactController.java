package com.xiyuan.ontology.objectfact.api;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.xiyuan.ontology.objectfact.service.FactCommands;
import com.xiyuan.ontology.objectfact.service.FactQueries;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/facts")
public class FactController {
	private final FactCommands commands;
	private final FactQueries queries;
	public FactController(FactCommands commands, FactQueries queries) { this.commands = commands; this.queries = queries; }
	@GetMapping("/object-types") public Map<String, Object> catalog() { return FactResponses.ok(queries.catalog()); }
	@GetMapping("/instances") public Map<String, Object> instances(@RequestParam(required = false) String type, @RequestParam(required = false) String domain,
			@RequestParam(required = false) String q, @RequestParam(required = false) String mark, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int pageSize) {
		return FactResponses.ok(queries.instances(type, domain, q, mark, page, pageSize));
	}
	@GetMapping("/instances/{id}") public Map<String, Object> detail(@PathVariable String id) { return FactResponses.ok(queries.detail(id)); }
	@GetMapping("/instances/{id}/references") public Map<String, Object> references(@PathVariable String id) { return FactResponses.ok(queries.references(id)); }
	@GetMapping("/graph") public Map<String, Object> graph(@RequestParam(required = false) String types, @RequestParam(required = false) String domains,
			@RequestParam(required = false) String q, @RequestParam(required = false) String mark,
			@RequestParam(required = false) String focus, @RequestParam(defaultValue = "1") int hops, @RequestParam(defaultValue = "300") int limit) {
		return FactResponses.ok(queries.graph(types, domains, q, mark, focus, hops, limit));
	}
	@GetMapping("/batches") public Map<String, Object> batches(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "30") int pageSize) { return FactResponses.ok(queries.batches(page, pageSize)); }
	@PostMapping("/import") public Map<String, Object> importTopology(@RequestBody ObjectNode body,
			@RequestHeader(value = "X-Xiyuan-Actor", defaultValue = "modeler-local") String actor, @RequestHeader(value = "If-Match", required = false) Long revision) {
		return FactResponses.ok(commands.importTopology(body, actor, revision));
	}
	@PostMapping("/instances") public Map<String, Object> create(@RequestBody ObjectNode body,
			@RequestHeader(value = "X-Xiyuan-Actor", defaultValue = "modeler-local") String actor, @RequestHeader(value = "If-Match", required = false) Long revision) {
		return FactResponses.ok(commands.create(body, actor, revision));
	}
	@PatchMapping("/instances/{id}") public Map<String, Object> edit(@PathVariable String id, @RequestBody ObjectNode body,
			@RequestHeader(value = "X-Xiyuan-Actor", defaultValue = "modeler-local") String actor, @RequestHeader(value = "If-Match", required = false) Long revision) {
		return FactResponses.ok(commands.edit(id, body, actor, revision));
	}
	@DeleteMapping("/instances/{id}") public Map<String, Object> delete(@PathVariable String id,
			@RequestHeader(value = "X-Xiyuan-Actor", defaultValue = "modeler-local") String actor, @RequestHeader(value = "If-Match", required = false) Long revision) {
		return FactResponses.ok(commands.delete(id, actor, revision));
	}
	@DeleteMapping("/data") public Map<String, Object> purge(@RequestBody ObjectNode body,
			@RequestHeader(value = "X-Xiyuan-Actor", defaultValue = "modeler-local") String actor, @RequestHeader(value = "If-Match", required = false) Long revision) {
		return FactResponses.ok(commands.purge(body.path("confirm").asBoolean(false), actor, revision));
	}
	@PostMapping("/check") public Map<String, Object> check(@RequestHeader(value = "X-Xiyuan-Actor", defaultValue = "modeler-local") String actor,
			@RequestHeader(value = "If-Match", required = false) Long revision) { return FactResponses.ok(commands.check(actor, revision)); }
}
