package com.xiyuan.ontology.modeling.codec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.xiyuan.ontology.modeling.model.ModelDefinition;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class ModelJsonCodec {

	private final JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).findAndAddModules().build();

	public Map<String, Object> properties(JsonNode node) {
		return this.mapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { });
	}

	public ObjectNode json(Map<String, Object> properties) {
		return (ObjectNode) this.mapper.valueToTree(properties);
	}

	public ModelDefinition definition(UUID key, Kind kind, ObjectNode node) {
		Map<String, Object> properties = properties(node);
		String id = (String) properties.remove(kind == Kind.DOMAIN ? "domainId" : "id");
		int revision = node.path("revision").asInt(1);
		if (kind != Kind.DOMAIN) {
			properties.remove("revision");
		}
		if (kind == Kind.OBJECT) {
			properties.remove("graph");
		}
		return new ModelDefinition(key, kind, id, revision, properties);
	}

	public ObjectNode json(ModelDefinition definition) {
		ObjectNode node = json(definition.properties());
		node.put(definition.kind() == Kind.DOMAIN ? "domainId" : "id", definition.id());
		if (definition.kind() != Kind.DOMAIN) {
			node.put("revision", definition.revision());
		}
		return node;
	}

}
