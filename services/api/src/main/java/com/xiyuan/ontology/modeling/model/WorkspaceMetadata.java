package com.xiyuan.ontology.modeling.model;

import java.util.Map;

public record WorkspaceMetadata(String workspaceId, long commitVersion, int storageSchemaVersion,
		Map<String, Object> properties) {

	public WorkspaceMetadata {
		properties = ModelValues.freezeMap(properties);
	}

}
