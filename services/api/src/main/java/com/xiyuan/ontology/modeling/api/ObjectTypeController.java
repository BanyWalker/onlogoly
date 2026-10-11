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
 * 对象类型与属性 CRUD（TDD-OM-002 §9.2：FR-OBJ-001/002/003、FR-COM-010）。
 */
@RestController
@RequestMapping("/api/v1/object-types")
public class ObjectTypeController {

	private final ModelingService modelingService;

	public ObjectTypeController(ModelingService modelingService) {
		this.modelingService = modelingService;
	}

	@PostMapping
	public Map<String, Object> create(@RequestBody ObjectNode body) {
		return ApiResponses.ok(this.modelingService.createObjectType(body));
	}

	@GetMapping("/{id}")
	public Map<String, Object> get(@PathVariable String id) {
		return ApiResponses.ok(this.modelingService.getObjectType(id));
	}

	@PatchMapping("/{id}")
	public Map<String, Object> update(@PathVariable String id, @RequestBody ObjectNode body,
			@RequestHeader(value = "If-Match", required = false) Integer ifMatch) {
		return ApiResponses.ok(this.modelingService.updateObjectType(id, body, ifMatch));
	}

	@DeleteMapping("/{id}")
	public Map<String, Object> deprecate(@PathVariable String id,
			@RequestHeader(value = "If-Match", required = false) Integer ifMatch) {
		return ApiResponses.ok(this.modelingService.deprecateObjectType(id, ifMatch));
	}

	@GetMapping("/{id}/attributes")
	public Map<String, Object> listAttributes(@PathVariable String id) {
		return ApiResponses.ok(this.modelingService.getObjectType(id).path("attributes"));
	}

	@PostMapping("/{id}/attributes")
	public Map<String, Object> createAttribute(@PathVariable String id, @RequestBody ObjectNode body) {
		return ApiResponses.ok(this.modelingService.createAttribute(id, body));
	}

	@PatchMapping("/{id}/attributes/{attrId}")
	public Map<String, Object> updateAttribute(@PathVariable String id, @PathVariable String attrId,
			@RequestBody ObjectNode body) {
		return ApiResponses.ok(this.modelingService.updateAttribute(id, attrId, body));
	}

	@DeleteMapping("/{id}/attributes/{attrId}")
	public Map<String, Object> deleteAttribute(@PathVariable String id, @PathVariable String attrId) {
		return ApiResponses.ok(this.modelingService.deleteAttribute(id, attrId));
	}

}
