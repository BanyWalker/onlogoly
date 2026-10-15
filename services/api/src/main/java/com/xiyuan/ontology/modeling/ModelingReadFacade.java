package com.xiyuan.ontology.modeling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xiyuan.ontology.modeling.codec.ModelJsonCodec;
import com.xiyuan.ontology.modeling.storage.ModelStorage;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Published read boundary. Every call returns one detached, consistent catalog. */
@Service
public class ModelingReadFacade {
	private final ModelStorage storage;
	private final String workspaceId;
	private final ModelJsonCodec codec = new ModelJsonCodec();

	public ModelingReadFacade(ModelStorage storage, @Value("${xiyuan.workspace.id:ENV-OM-001}") String workspaceId) {
		this.storage = storage;
		this.workspaceId = workspaceId;
	}

	public ObjectNode snapshot() {
		return storage.read(workspaceId, session -> {
			ObjectNode result = JsonNodeFactory.instance.objectNode();
			result.put("version", session.metadata().commitVersion());
			session.objects().findAll().forEach(value -> result.withArray("objects").add(codec.json(value)));
			session.relations().findAll().forEach(value -> result.withArray("relations").add(codec.json(value)));
			session.domains().findAll().forEach(value -> result.withArray("domains").add(codec.json(value)));
			for (String field : new String[] { "objects", "relations", "domains" }) result.withArray(field);
			return result;
		});
	}
}
