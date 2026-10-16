package com.xiyuan.ontology.objectfact.validate;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xiyuan.ontology.objectfact.service.FactCatalog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Shared by imports, create, edit and consistency checks. It never mutates facts. */
public final class FactValidator {
	public record Violation(String instanceKey, String fieldPath, String rule, String message, List<String> conflicts) {
		public Violation(String key, String path, String rule, String message) { this(key, path, rule, message, List.of()); }
	}

	public List<Violation> properties(JsonNode instance, FactCatalog catalog) {
		List<Violation> errors = new ArrayList<>();
		String key = key(instance);
		JsonNode type = catalog.objects.get(instance.path("objectTypeRef").asString());
		if (!FactCatalog.active(type)) {
			errors.add(new Violation(key, "objectTypeRef", "TYPE_NOT_FOUND", "对象类型未定义或已废弃"));
			return errors;
		}
		JsonNode values = instance.path("propertyValues");
		if (!values.isObject()) { errors.add(new Violation(key, "propertyValues", "STRUCT_INVALID", "属性取值必须是 JSON 对象")); return errors; }
		for (String name : values.propertyNames()) {
			JsonNode attribute = FactCatalog.attribute(type, name);
			if (attribute == null || !name.equals(attribute.path("name").asString())) errors.add(new Violation(key, "propertyValues." + name, "PROPERTY_NOT_FOUND", "属性未在对象类型中定义"));
			else if ("Reference".equals(attribute.path("dataType").asString())) errors.add(new Violation(key, "propertyValues." + name, "REFERENCE_PROPERTY_DUAL_WRITE", "引用型属性由关系派生，请通过关系编辑"));
		}
		for (JsonNode attribute : type.path("attributes")) {
			if ("Reference".equals(attribute.path("dataType").asString())) continue;
			String name = attribute.path("name").asString();
			JsonNode value = values.path(name);
			String path = "propertyValues." + name;
			boolean required = attribute.path("required").asBoolean(false);
			if (value.isMissingNode()) {
				if (required) errors.add(new Violation(key, path, "PROPERTY_REQUIRED_MISSING", "必填属性未提供"));
				continue;
			}
			boolean nullObject = value.isObject() && value.has("value") && value.path("value").isNull();
			if (value.isNull() || nullObject) {
				String reason = value.isObject() ? value.path("nullReason").asString("") : "";
				String expected = attribute.path("nullMeaning").asString("");
				if (!attribute.path("nullable").asBoolean(false)) errors.add(new Violation(key, path, "PROPERTY_REQUIRED_MISSING", "属性不允许空值"));
				else if (!reason.equals(expected) || (!expected.isBlank() && !Set.of("UNKNOWN", "NOT_PROVIDED", "NOT_APPLICABLE").contains(reason)))
					errors.add(new Violation(key, path, "NULL_MEANING_MISMATCH", "空值原因必须符合模型声明：" + expected));
				continue;
			}
			if (value.isString() && value.asString().isBlank() && required) errors.add(new Violation(key, path, "PROPERTY_REQUIRED_MISSING", "必填属性不可为空字符串"));
			int count = value.isArray() ? value.size() : 1;
			int min = attribute.path("cardinality").path("min").asInt(required ? 1 : 0);
			int max = maximum(attribute.path("cardinality"));
			if (count < min || count > max) errors.add(new Violation(key, path, "PROPERTY_CARDINALITY", "属性取值数量超出 " + min + ".." + (max == Integer.MAX_VALUE ? "N" : max)));
			List<JsonNode> items = new ArrayList<>();
			if (value.isArray()) value.forEach(items::add); else items.add(value);
			for (JsonNode item : items) {
				String dataType = attribute.path("dataType").asString();
				boolean matches = switch (dataType) {
					case "String", "Enumeration" -> item.isString();
					case "Integer" -> item.isIntegralNumber();
					case "Decimal" -> item.isNumber();
					case "Boolean" -> item.isBoolean();
					case "DateTime" -> item.isString() && dateTime(item.asString());
					default -> false;
				};
				if (!matches) errors.add(new Violation(key, path, "PROPERTY_TYPE_MISMATCH", "属性取值不符合 " + dataType + " 类型"));
				if ("Enumeration".equals(dataType) && matches) {
					boolean allowed = false;
					for (JsonNode choice : attribute.path("allowedValues")) if (item.asString().equals(choice.isString() ? choice.asString() : choice.path("code").asString())) allowed = true;
					if (!allowed) errors.add(new Violation(key, path, "ENUM_OUT_OF_RANGE", "值 " + item.asString() + " 不在模型枚举范围内"));
				}
			}
		}
		return errors;
	}

	public ObjectNode identity(JsonNode instance, FactCatalog catalog, List<Violation> errors) {
		ObjectNode identity = JsonNodeFactory.instance.objectNode();
		JsonNode type = catalog.objects.get(instance.path("objectTypeRef").asString());
		if (!FactCatalog.active(type)) return identity;
		if (type.path("identitySpec").path("attributes").isEmpty()) errors.add(new Violation(key(instance), "businessIdentity", "IDENTITY_MISSING", "对象模型尚未声明业务标识，请先完善对象建模"));
		for (JsonNode reference : type.path("identitySpec").path("attributes")) {
			JsonNode attribute = FactCatalog.attribute(type, reference.asString());
			String name = attribute == null ? reference.asString() : attribute.path("name").asString();
			JsonNode value = instance.path("propertyValues").path(name);
			if (value.isMissingNode() || value.isNull() || value.isContainer() || (value.isString() && value.asString().isBlank()))
				errors.add(new Violation(key(instance), "propertyValues." + name, "IDENTITY_MISSING", "业务标识必须提供非空标量值"));
			else identity.set(name, value);
		}
		return identity;
	}

	public List<Violation> unique(List<JsonNode> instances, FactCatalog catalog, Set<String> changed) {
		List<Violation> errors = new ArrayList<>();
		Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
		for (JsonNode instance : instances) {
			List<Violation> missing = new ArrayList<>();
			ObjectNode identity = identity(instance, catalog, missing);
			if (changed.contains(instance.path("instanceId").asString())) errors.addAll(missing);
			if (missing.isEmpty()) groups.computeIfAbsent(instance.path("objectTypeRef").asString() + ":" + FactCatalog.digest(identity), ignored -> new ArrayList<>()).add(instance);
		}
		for (List<JsonNode> group : groups.values()) if (group.size() > 1) {
			List<String> conflicts = group.stream().map(FactValidator::key).toList();
			for (JsonNode instance : group) if (changed.contains(instance.path("instanceId").asString()))
				errors.add(new Violation(key(instance), "businessIdentity", "IDENTITY_DUPLICATE", "同类型内业务标识重复：" + String.join("、", conflicts), conflicts));
		}
		return errors;
	}

	public List<Violation> relations(List<JsonNode> instances, List<JsonNode> relations, FactCatalog catalog, Set<String> affected, boolean minima) {
		List<Violation> errors = new ArrayList<>();
		Map<String, JsonNode> byId = new HashMap<>();
		instances.forEach(instance -> byId.put(instance.path("instanceId").asString(), instance));
		Map<String, Integer> out = new HashMap<>(), in = new HashMap<>();
		Set<String> duplicates = new HashSet<>();
		for (JsonNode edge : relations) {
			String source = edge.path("sourceInstanceId").asString(), target = edge.path("targetInstanceId").asString(), ref = edge.path("relationTypeRef").asString();
			boolean relevant = affected.contains(source) || affected.contains(target);
			JsonNode definition = catalog.relations.get(ref);
			String key = byId.containsKey(source) ? key(byId.get(source)) : source;
			String path = "relations." + edge.path("relationId").asString();
			if (!FactCatalog.active(definition)) { if (relevant) errors.add(new Violation(key, path, "RELATION_NOT_FOUND", "关系类型未定义或已废弃：" + ref)); continue; }
			if (!byId.containsKey(source) || !byId.containsKey(target)) { if (relevant) errors.add(new Violation(key, path, "REFERENCE_DANGLING", "关系两端实例必须存在")); continue; }
			if (!definition.path("sourceType").asString().equals(byId.get(source).path("objectTypeRef").asString()) || !FactCatalog.targets(definition).contains(byId.get(target).path("objectTypeRef").asString())) {
				if (relevant) errors.add(new Violation(key, path, "RELATION_TYPE_MISMATCH", "源与目标对象类型不符合关系定义"));
				continue;
			}
			String triple = ref + ":" + source + ":" + target;
			if (!duplicates.add(triple) && relevant) errors.add(new Violation(key, path, "RELATION_DUPLICATE", "相同关系已存在，请勿重复连线"));
			out.merge(ref + ":" + source, 1, Integer::sum);
			in.merge(ref + ":" + target, 1, Integer::sum);
		}
		for (JsonNode instance : instances) {
			String id = instance.path("instanceId").asString(), type = instance.path("objectTypeRef").asString();
			if (!affected.contains(id)) continue;
			for (JsonNode definition : catalog.relations.values()) {
				if (!FactCatalog.active(definition)) continue;
				String ref = definition.path("id").asString();
				if (type.equals(definition.path("sourceType").asString())) cardinality(errors, instance, definition, "sourceCardinality", "出向", out.getOrDefault(ref + ":" + id, 0), minima);
				if (FactCatalog.targets(definition).contains(type)) cardinality(errors, instance, definition, "targetCardinality", "入向", in.getOrDefault(ref + ":" + id, 0), minima);
			}
		}
		return errors;
	}

	private void cardinality(List<Violation> errors, JsonNode instance, JsonNode definition, String field, String direction, int count, boolean minima) {
		JsonNode bounds = definition.path(field);
		if ((minima && count < bounds.path("min").asInt(0)) || count > maximum(bounds))
			errors.add(new Violation(key(instance), "relations." + definition.path("id").asString(), "CARDINALITY_OUT_OF_RANGE", definition.path("name").asString() + " " + direction + "数量 " + count + " 不符合 " + bounds.path("min").asInt(0) + ".." + bounds.path("max").asString("N")));
	}
	public static String key(JsonNode instance) {
		return instance.path("instanceId").asString(instance.path("instanceKey").asString());
	}
	private static int maximum(JsonNode bounds) { return bounds.path("max").isNumber() ? bounds.path("max").asInt() : Integer.MAX_VALUE; }
	private static boolean dateTime(String value) {
		try { OffsetDateTime.parse(value); return true; }
		catch (java.time.format.DateTimeParseException error) {
			try { LocalDateTime.parse(value); return true; } catch (java.time.format.DateTimeParseException ignored) { return false; }
		}
	}
}
