package com.xiyuan.ontology.objectfact.api;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.xiyuan.ontology.objectfact")
public class FactExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(FactExceptionHandler.class);
	@ExceptionHandler(FactException.class)
	ResponseEntity<?> fact(FactException error) {
		return ResponseEntity.status(error.status()).body(FactResponses.error(error.code(), error.getMessage(), error.details(), error.status() >= 500));
	}
	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<?> input(Exception error) {
		return ResponseEntity.badRequest().body(FactResponses.error("OF_FILE_INVALID", "请求格式不合法，请检查 JSON 内容及查询参数", List.of(), false));
	}
	@ExceptionHandler(Exception.class)
	ResponseEntity<?> unexpected(Exception error) {
		log.error("对象事实接口执行失败", error);
		return ResponseEntity.internalServerError().body(FactResponses.error("OF_INTERNAL_ERROR", "对象事实操作失败，既有事实保持不变", List.of(), true));
	}
}
