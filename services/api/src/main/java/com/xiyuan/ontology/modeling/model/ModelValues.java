package com.xiyuan.ontology.modeling.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModelValues {

	private ModelValues() {
	}

	public static Map<String, Object> freezeMap(Map<String, ?> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		source.forEach((key, value) -> result.put(key, freeze(value)));
		return Collections.unmodifiableMap(result);
	}

	private static Object freeze(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<>();
			map.forEach((key, item) -> result.put((String) key, freeze(item)));
			return Collections.unmodifiableMap(result);
		}
		if (value instanceof List<?> list) {
			List<Object> result = new ArrayList<>();
			list.forEach(item -> result.add(freeze(item)));
			return Collections.unmodifiableList(result);
		}
		if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
			return value;
		}
		throw new IllegalArgumentException("Unsupported model value: " + value.getClass().getName());
	}

}
