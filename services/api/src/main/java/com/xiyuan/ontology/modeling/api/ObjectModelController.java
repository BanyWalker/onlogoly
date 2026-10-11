package com.xiyuan.ontology.modeling.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xiyuan.ontology.modeling.service.ModelingService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 画布加载 / 布局保存 / 完整 JSON 导出（TDD-OM-002 §9.2）。
 */
@RestController
@RequestMapping("/api/v1/object-model")
public class ObjectModelController {

	private final ModelingService modelingService;

	public ObjectModelController(ModelingService modelingService) {
		this.modelingService = modelingService;
	}

	@GetMapping("/graph")
	public Map<String, Object> graph(@RequestParam(required = false) String domainId) {
		return ApiResponses.ok(this.modelingService.graph(domainId));
	}

	@GetMapping("/export")
	public Map<String, Object> exportModel() {
		return ApiResponses.ok(this.modelingService.exportModel());
	}

	@PutMapping("/layout")
	public Map<String, Object> saveLayout(@RequestHeader(value = "If-Match", required = false) Integer ifMatch,
			@RequestBody ObjectNode body) {
		JsonNode positions = body.has("positions") ? body.path("positions") : body;
		return ApiResponses.ok(this.modelingService.saveLayout(ifMatch, positions));
	}

}
