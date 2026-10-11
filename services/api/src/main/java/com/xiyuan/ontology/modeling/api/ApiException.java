package com.xiyuan.ontology.modeling.api;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * 业务校验异常：携带 TDD-OM-002 §9.4 校验矩阵中的错误码与字段级定位。
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final List<ErrorDetail> details;

	private ApiException(HttpStatus status, String code, String message, List<ErrorDetail> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = details == null ? List.of() : List.copyOf(details);
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message, List.of());
	}

	public static ApiException badRequest(String code, String message, List<ErrorDetail> details) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message, details);
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message, List.of());
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message, List.of());
	}

	public static ApiException conflict(String code, String message, List<ErrorDetail> details) {
		return new ApiException(HttpStatus.CONFLICT, code, message, details);
	}

	public static ApiException revisionConflict(String elementId, long expected, long actual) {
		return new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT", "定义已被他人修订，请加载最新版本后重试",
				List.of(new ErrorDetail("If-Match", String.valueOf(expected), elementId)));
	}

	public HttpStatus getStatus() {
		return this.status;
	}

	public String getCode() {
		return this.code;
	}

	public List<ErrorDetail> getDetails() {
		return this.details;
	}

}
