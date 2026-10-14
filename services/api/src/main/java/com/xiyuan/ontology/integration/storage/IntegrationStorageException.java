package com.xiyuan.ontology.integration.storage;

import org.springframework.http.HttpStatus;

public final class IntegrationStorageException extends RuntimeException {
	private final HttpStatus status;
	private final String code;

	private IntegrationStorageException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static IntegrationStorageException badRequest(String code, String message) { return new IntegrationStorageException(HttpStatus.BAD_REQUEST, code, message); }
	public static IntegrationStorageException notFound(String code, String message) { return new IntegrationStorageException(HttpStatus.NOT_FOUND, code, message); }
	public static IntegrationStorageException conflict(String code, String message) { return new IntegrationStorageException(HttpStatus.CONFLICT, code, message); }
	public HttpStatus status() { return status; }
	public String code() { return code; }
}
