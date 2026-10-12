package com.xiyuan.ontology.modeling.storage;

import java.util.ArrayList;
import java.util.List;

import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.model.RevisionRecord;
import com.xiyuan.ontology.modeling.model.WorkspaceSnapshot;

public final class StorageSnapshots {

	private StorageSnapshots() {
	}

	public static WorkspaceSnapshot capture(ModelStorage storage, String workspaceId) {
		return storage.read(workspaceId, session -> {
			List<ModelDefinition> definitions = new ArrayList<>();
			definitions.addAll(session.objects().findAll());
			definitions.addAll(session.relations().findAll());
			definitions.addAll(session.domains().findAll());
			List<RevisionRecord> revisions = new ArrayList<>();
			var identities = session.identities();
			identities.forEach(identity -> revisions.addAll(session.revisions().findByElementKey(identity.elementKey())));
			return new WorkspaceSnapshot(1, session.metadata(), definitions, identities, session.layout().get(),
					revisions, session.audit().findAll(), session.reviewFlags());
		});
	}

}
