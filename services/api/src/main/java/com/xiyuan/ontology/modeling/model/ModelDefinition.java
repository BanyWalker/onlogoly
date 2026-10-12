package com.xiyuan.ontology.modeling.model;

import java.util.Map;
import java.util.UUID;

public record ModelDefinition(UUID elementKey, Kind kind, String id, int revision, Map<String, Object> properties) {

	public enum Kind {
		OBJECT, RELATION, DOMAIN
	}

	public ModelDefinition {
		if (elementKey == null || kind == null || id == null || id.isBlank() || revision < 1) {
			throw new IllegalArgumentException("Invalid model definition identity");
		}
		properties = ModelValues.freezeMap(properties);
	}

	public String domainId() {
		return this.kind == Kind.DOMAIN ? this.id : (String) this.properties.get("domainId");
	}

}
