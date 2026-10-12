package com.xiyuan.ontology.modeling.storage;

import java.util.List;
import java.util.UUID;

import com.xiyuan.ontology.modeling.model.RevisionRecord;

public interface RevisionRepository {

	List<RevisionRecord> findByElementKey(UUID elementKey);

	interface Writable extends RevisionRepository {
		void append(RevisionRecord revision);
	}

}
