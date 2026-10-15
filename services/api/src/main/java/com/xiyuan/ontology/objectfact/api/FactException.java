package com.xiyuan.ontology.objectfact.api;

import java.util.List;

public class FactException extends RuntimeException {
	private final int status;
	private final String code;
	private final Object details;

	public FactException(int status, String code, String message, Object details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = details;
	}

	public FactException(int status, String code, String message) { this(status, code, message, List.of()); }
	public int status() { return status; }
	public String code() { return code; }
	public Object details() { return details; }
}
