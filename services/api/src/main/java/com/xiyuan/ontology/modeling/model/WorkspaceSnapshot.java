package com.xiyuan.ontology.modeling.model;

import java.util.List;
import java.util.Map;

public record WorkspaceSnapshot(int formatVersion, WorkspaceMetadata metadata, List<ModelDefinition> definitions,
		List<ElementIdentity> identities, LayoutState layout, List<RevisionRecord> revisions,
		List<AuditEvent> events, Map<String, Object> reviewFlags) {

	public WorkspaceSnapshot {
		if (formatVersion != 1) {
			throw new IllegalArgumentException("Unsupported migration snapshot format: " + formatVersion);
		}
		definitions = List.copyOf(definitions);
		identities = List.copyOf(identities);
		revisions = List.copyOf(revisions);
		events = List.copyOf(events);
		reviewFlags = ModelValues.freezeMap(reviewFlags);
		ModelIntegrity.validate(definitions, layout);
	}

}
