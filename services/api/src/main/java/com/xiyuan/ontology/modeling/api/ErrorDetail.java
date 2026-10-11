package com.xiyuan.ontology.modeling.api;

/**
 * 字段级错误定位（TDD-OM-002 §9.1 统一错误报文 details 元素）。
 */
public record ErrorDetail(String field, String value, String elementId) {
}
