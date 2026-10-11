package com.xiyuan.ontology.modeling.api;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 建模线统一异常出口，保证所有失败响应符合 §9.1 错误报文结构。
 */
@RestControllerAdvice(basePackages = "com.xiyuan.ontology.modeling")
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
		return ResponseEntity.status(ex.getStatus())
			.body(ApiResponses.error(ex.getCode(), ex.getMessage(), ex.getDetails(), false));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
		log.error("建模接口未预期异常", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponses.error("INTERNAL_ERROR", "服务内部错误", List.of(), true));
	}

}
