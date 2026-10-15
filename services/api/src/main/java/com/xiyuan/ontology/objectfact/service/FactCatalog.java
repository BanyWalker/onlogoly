package com.xiyuan.ontology.objectfact.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class FactCatalog {
	public final Map<String, JsonNode> objects = new LinkedHashMap<>();
	public final Map<String, JsonNode> relations = new LinkedHashMap<>();
	public final Map<String, JsonNode> domains = new LinkedHashMap<>();
	public final long version;

	public FactCatalog(ObjectNode snapshot) {
		version = snapshot.path("version").asLong();
		snapshot.path("objects").forEach(value -> objects.put(value.path("id").asString(), value));
		snapshot.path("relations").forEach(value -> relations.put(value.path("id").asString(), value));
		snapshot.path("domains").forEach(value -> domains.put(value.path("domainId").asString(), value));
	}

	public static boolean active(JsonNode node) { return node != null && !"deprecated".equals(node.path("status").asString()); }
	public static List<String> targets(JsonNode relation) {
		JsonNode target = relation.path("targetType");
		return target.isArray() ? java.util.stream.StreamSupport.stream(target.spliterator(), false).map(JsonNode::asString).toList() : List.of(target.asString());
	}
	public static JsonNode attribute(JsonNode type, String reference) {
		for (JsonNode value : type.path("attributes"))
			if (reference.equals(value.path("id").asString()) || reference.equals(value.path("name").asString())) return value;
		return null;
	}
	public static String signature(JsonNode value, String... fields) {
		ObjectNode semantic = JsonNodeFactory.instance.objectNode();
		for (String field : fields) semantic.set(field, value.path(field));
		return digest(semantic);
	}
	public static String digest(JsonNode value) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(value).toString().getBytes(StandardCharsets.UTF_8))); }
		catch (Exception error) { throw new IllegalStateException(error); }
	}
	private static JsonNode canonical(JsonNode value) {
		if (value.isObject()) {
			ObjectNode output = JsonNodeFactory.instance.objectNode();
			value.propertyNames().stream().sorted().forEach(key -> output.set(key, canonical(value.path(key))));
			return output;
		}
		if (value.isArray()) {
			var output = JsonNodeFactory.instance.arrayNode();
			value.forEach(item -> output.add(canonical(item)));
			return output;
		}
		return value.isMissingNode() ? JsonNodeFactory.instance.nullNode() : value;
	}
	public static String attributeSignature(JsonNode attribute) {
		return signature(attribute, "id", "name", "dataType", "required", "nullable", "nullMeaning", "cardinality", "allowedValues", "readOnlyNature", "relationRef", "referenceTarget");
	}
	public static String relationSignature(JsonNode relation) {
		return signature(relation, "id", "sourceType", "targetType", "sourceCardinality", "targetCardinality");
	}
	public ObjectNode binding(JsonNode instance) {
		ObjectNode output = JsonNodeFactory.instance.objectNode();
		JsonNode type = objects.get(instance.path("objectTypeRef").asString());
		if (type == null) return output;
		output.put("identity", digest(type.path("identitySpec")));
		ObjectNode attributes = output.putObject("attributes");
		for (JsonNode attribute : type.path("attributes")) attributes.put(attribute.path("name").asString(), attributeSignature(attribute));
		return output;
	}
}
