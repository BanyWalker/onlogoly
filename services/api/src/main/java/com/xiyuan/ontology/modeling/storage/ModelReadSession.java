package com.xiyuan.ontology.modeling.storage;

import java.util.Map;
import java.util.List;

import com.xiyuan.ontology.modeling.model.ElementIdentity;
import com.xiyuan.ontology.modeling.model.WorkspaceMetadata;

public interface ModelReadSession {

	WorkspaceMetadata metadata();

	List<ElementIdentity> identities();

	DefinitionRepository objects();

	DefinitionRepository relations();

	DefinitionRepository domains();

	LayoutRepository layout();

	RevisionRepository revisions();

	AuditLog audit();

	Map<String, Object> reviewFlags();

}
