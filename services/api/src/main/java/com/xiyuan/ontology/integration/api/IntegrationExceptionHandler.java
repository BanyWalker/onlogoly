package com.xiyuan.ontology.integration.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.xiyuan.ontology.integration.storage.IntegrationStorageException;

@RestControllerAdvice(basePackages = "com.xiyuan.ontology.integration")
public class IntegrationExceptionHandler {
	@ExceptionHandler(IntegrationStorageException.class)
	ResponseEntity<Map<String, Object>> handle(IntegrationStorageException exception) {
		return ResponseEntity.status(exception.status()).body(IntegrationResponses.error(exception.code(), exception.getMessage(), List.of(), false));
	}
	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
		return ResponseEntity.internalServerError().body(IntegrationResponses.error("INTERNAL_ERROR", "服务内部错误", List.of(), true));
	}
}
