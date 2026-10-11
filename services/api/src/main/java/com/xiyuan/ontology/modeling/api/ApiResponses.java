package com.xiyuan.ontology.modeling.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一响应包装（TDD-OM-002 §9.1）：成功 { requestId, data }；失败 { requestId, code, message, details, retryable }。
 */
public final class ApiResponses {

	private ApiResponses() {
	}

	public static String newRequestId() {
		return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	public static Map<String, Object> ok(Object data) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("requestId", newRequestId());
		body.put("data", data);
		return body;
	}

	public static Map<String, Object> error(String code, String message, Object details, boolean retryable) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("requestId", newRequestId());
		body.put("code", code);
		body.put("message", message);
		body.put("details", details);
		body.put("retryable", retryable);
		return body;
	}

}
