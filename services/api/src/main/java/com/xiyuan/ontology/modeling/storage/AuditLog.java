package com.xiyuan.ontology.modeling.storage;

import java.util.List;

import com.xiyuan.ontology.modeling.model.AuditEvent;

public interface AuditLog {

	List<AuditEvent> findAll();

	interface Writable extends AuditLog {
		void append(AuditEvent event);
	}

}
