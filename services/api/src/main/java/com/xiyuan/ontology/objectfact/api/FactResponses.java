package com.xiyuan.ontology.objectfact.api;

import java.util.Map;
import java.util.UUID;

public final class FactResponses {
	private FactResponses() { }
	public static Map<String, Object> ok(Object data) { return Map.of("requestId", requestId(), "data", data); }
	public static Map<String, Object> error(String code, String message, Object details, boolean retryable) {
		return Map.of("requestId", requestId(), "code", code, "message", message, "details", details, "retryable", retryable);
	}
	private static String requestId() { return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
