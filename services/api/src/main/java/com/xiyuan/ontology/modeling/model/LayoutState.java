package com.xiyuan.ontology.modeling.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record LayoutState(int revision, Map<UUID, Position> positions, Map<String, Object> properties) {

	public record Position(double x, double y, Map<String, Object> properties) {
		public Position {
			if (!Double.isFinite(x) || !Double.isFinite(y)) {
				throw new IllegalArgumentException("Layout coordinates must be finite");
			}
			properties = ModelValues.freezeMap(properties);
		}
	}

	public LayoutState {
		if (revision < 1) {
			throw new IllegalArgumentException("Layout revision must be positive");
		}
		positions = Collections.unmodifiableMap(new LinkedHashMap<>(positions));
		properties = ModelValues.freezeMap(properties);
	}

}
