package com.xiyuan.ontology.modeling.api;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xiyuan.ontology.modeling.service.ModelingService;

import tools.jackson.databind.node.ObjectNode;

/**
 * 领域 API（TDD-OM-002 §4/§9.2：FR-COM-011 系统层能力完整实现，前端首版仅展示 + 内联创建，DEV-1）。
 */
@RestController
@RequestMapping("/api/v1/domains")
public class DomainController {

	private final ModelingService modelingService;

	public DomainController(ModelingService modelingService) {
		this.modelingService = modelingService;
	}

	@GetMapping
	public Map<String, Object> list() {
		return ApiResponses.ok(this.modelingService.listDomains());
	}

	@PostMapping
	public Map<String, Object> create(@RequestBody ObjectNode body) {
		return ApiResponses.ok(this.modelingService.createDomain(body));
	}

	@PatchMapping("/{id}")
	public Map<String, Object> update(@PathVariable String id, @RequestBody ObjectNode body) {
		return ApiResponses.ok(this.modelingService.updateDomain(id, body));
	}

	@DeleteMapping("/{id}")
	public Map<String, Object> delete(@PathVariable String id) {
		return ApiResponses.ok(this.modelingService.deleteDomain(id));
	}

}
