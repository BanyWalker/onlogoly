package com.xiyuan.ontology.modeling.api;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xiyuan.ontology.modeling.service.ModelingService;

import tools.jackson.databind.node.ObjectNode;

/**
 * 业务关系 CRUD（TDD-OM-002 §9.2：FR-OBJ-004/011/012/013）。
 */
@RestController
@RequestMapping("/api/v1/relationships")
public class RelationshipController {

	private final ModelingService modelingService;

	public RelationshipController(ModelingService modelingService) {
		this.modelingService = modelingService;
	}

	@PostMapping
	public Map<String, Object> create(@RequestBody ObjectNode body) {
		return ApiResponses.ok(this.modelingService.createRelation(body));
	}

	@GetMapping("/{id}")
	public Map<String, Object> get(@PathVariable String id) {
		return ApiResponses.ok(this.modelingService.getRelation(id));
	}

	@PatchMapping("/{id}")
	public Map<String, Object> update(@PathVariable String id, @RequestBody ObjectNode body,
			@RequestHeader(value = "If-Match", required = false) Integer ifMatch) {
		return ApiResponses.ok(this.modelingService.updateRelation(id, body, ifMatch));
	}

	@DeleteMapping("/{id}")
	public Map<String, Object> delete(@PathVariable String id) {
		return ApiResponses.ok(this.modelingService.deleteRelation(id));
	}

}
