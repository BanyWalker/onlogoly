package com.xiyuan.ontology.integration.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class IntegrationResponses {
	private IntegrationResponses() { }
	static String requestId() { return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
	static Map<String, Object> ok(Object data) { Map<String, Object> body = new LinkedHashMap<>(); body.put("requestId", requestId()); body.put("data", data); return body; }
	static Map<String, Object> error(String code, String message, Object details, boolean retryable) { Map<String, Object> body = new LinkedHashMap<>(); body.put("requestId", requestId()); body.put("code", code); body.put("message", message); body.put("details", details); body.put("retryable", retryable); return body; }
}
