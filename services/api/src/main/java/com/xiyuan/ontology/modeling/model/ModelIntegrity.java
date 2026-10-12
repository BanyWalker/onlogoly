package com.xiyuan.ontology.modeling.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;

public final class ModelIntegrity {

	private ModelIntegrity() {
	}

	public static void validate(Collection<ModelDefinition> definitions, LayoutState layout) {
		Map<String, ModelDefinition> objects = new HashMap<>();
		Set<String> domains = new HashSet<>();
		Set<String> relations = new HashSet<>();
		Set<String> ids = new HashSet<>();
		Set<UUID> keys = new HashSet<>();
		Set<UUID> objectKeys = new HashSet<>();
		for (ModelDefinition definition : definitions) {
			require(ids.add(definition.id()) && keys.add(definition.elementKey()), "Duplicate element identity: " + definition.id());
			switch (definition.kind()) {
				case DOMAIN -> domains.add(definition.id());
				case RELATION -> relations.add(definition.id());
				case OBJECT -> {
					objects.put(definition.id(), definition);
					objectKeys.add(definition.elementKey());
				}
			}
		}
		for (ModelDefinition definition : definitions) {
			Map<String, Object> fields = definition.properties();
			if (definition.kind() != Kind.DOMAIN) {
				require(domains.contains(definition.domainId()), "Missing domain for " + definition.id());
			}
			if (definition.kind() == Kind.OBJECT) {
				Set<String> attributes = new HashSet<>();
				for (Object value : list(fields.get("attributes"))) {
					Map<?, ?> attribute = (Map<?, ?>) value;
					String id = (String) attribute.get("id");
					require(id != null && !id.isBlank() && ids.add(id), "Duplicate or missing attribute id: " + id);
					attributes.add(id);
					for (Object target : list(attribute.get("referenceTarget"))) {
						require(objects.containsKey(target), "Missing attribute target: " + target);
					}
					Object relation = attribute.get("relationRef");
					require(relation == null || relations.contains(relation), "Missing attribute relation: " + relation);
				}
				Object identity = fields.get("identitySpec");
				if (identity instanceof Map<?, ?> identityFields) {
					for (Object attribute : list(identityFields.get("attributes"))) {
						require(attributes.contains(attribute), "Missing identity attribute: " + attribute);
					}
				}
			}
			if (definition.kind() == Kind.RELATION) {
				require(objects.containsKey(fields.get("sourceType")), "Missing relation source: " + definition.id());
				Object target = fields.get("targetType");
				List<?> targets = target instanceof List<?> values ? values : target == null ? List.of() : List.of(target);
				require(!targets.isEmpty(), "Missing relation targets: " + definition.id());
				for (Object targetId : targets) {
					require(objects.containsKey(targetId), "Missing relation target: " + targetId);
				}
			}
		}
		require(objectKeys.containsAll(layout.positions().keySet()), "Layout references an unknown object key");
	}

	private static List<?> list(Object value) {
		return value instanceof List<?> values ? values : List.of();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

}
