package com.xiyuan.ontology.modeling.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RevisionRecord(UUID elementKey, String elementId, int revision, String changeType,
		String changeReason, String changedBy, Instant changedAt, Map<String, Object> snapshot,
		Map<String, Object> properties) {

	public RevisionRecord {
		snapshot = ModelValues.freezeMap(snapshot);
		properties = ModelValues.freezeMap(properties);
	}

}
