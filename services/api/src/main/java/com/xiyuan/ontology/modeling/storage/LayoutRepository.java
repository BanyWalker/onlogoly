package com.xiyuan.ontology.modeling.storage;

import com.xiyuan.ontology.modeling.model.LayoutState;

public interface LayoutRepository {

	LayoutState get();

	interface Writable extends LayoutRepository {
		void save(LayoutState layout, int expectedRevision);
	}

}
