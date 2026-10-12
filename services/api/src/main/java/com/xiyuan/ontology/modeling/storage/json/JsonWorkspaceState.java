package com.xiyuan.ontology.modeling.storage.json;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.xiyuan.ontology.modeling.model.AuditEvent;
import com.xiyuan.ontology.modeling.model.ElementIdentity;
import com.xiyuan.ontology.modeling.model.LayoutState;
import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;
import com.xiyuan.ontology.modeling.model.RevisionRecord;
import com.xiyuan.ontology.modeling.model.WorkspaceMetadata;

final class JsonWorkspaceState {

	WorkspaceMetadata metadata;

	LayoutState layout;

	Map<String, Object> reviewFlags;

	final Map<Kind, LinkedHashMap<UUID, ModelDefinition>> definitions = new EnumMap<>(Kind.class);

	final Map<UUID, ElementIdentity> identities = new LinkedHashMap<>();

	final List<RevisionRecord> revisions = new ArrayList<>();

	final List<AuditEvent> events = new ArrayList<>();

	JsonWorkspaceState() {
		for (Kind kind : Kind.values()) {
			this.definitions.put(kind, new LinkedHashMap<>());
		}
	}

	JsonWorkspaceState copy() {
		JsonWorkspaceState copy = new JsonWorkspaceState();
		copy.metadata = this.metadata;
		copy.layout = this.layout;
		copy.reviewFlags = this.reviewFlags;
		this.definitions.forEach((kind, elements) -> copy.definitions.get(kind).putAll(elements));
		copy.identities.putAll(this.identities);
		copy.revisions.addAll(this.revisions);
		copy.events.addAll(this.events);
		return copy;
	}

}
