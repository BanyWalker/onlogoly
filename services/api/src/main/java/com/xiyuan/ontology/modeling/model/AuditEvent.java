package com.xiyuan.ontology.modeling.model;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(String eventId, String eventType, String elementId, Instant occurredAt,
		Map<String, Object> properties) {

	public AuditEvent {
		properties = ModelValues.freezeMap(properties);
	}

}
