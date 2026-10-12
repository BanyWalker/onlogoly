package com.xiyuan.ontology.modeling.storage;

import java.util.Map;

public interface ModelWriteSession extends ModelReadSession {

	@Override
	DefinitionRepository.Writable objects();

	@Override
	DefinitionRepository.Writable relations();

	@Override
	DefinitionRepository.Writable domains();

	@Override
	LayoutRepository.Writable layout();

	@Override
	RevisionRepository.Writable revisions();

	@Override
	AuditLog.Writable audit();

	void saveReviewFlags(Map<String, Object> flags);

}
