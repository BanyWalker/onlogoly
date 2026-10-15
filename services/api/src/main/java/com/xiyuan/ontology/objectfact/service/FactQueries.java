package com.xiyuan.ontology.objectfact.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import com.xiyuan.ontology.modeling.ModelingReadFacade;
import com.xiyuan.ontology.objectfact.api.FactException;
import com.xiyuan.ontology.objectfact.storage.FactStorage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class FactQueries {
	private final FactStorage storage;
	private final ModelingReadFacade models;
	public FactQueries(FactStorage storage, ModelingReadFacade models) { this.storage = storage; this.models = models; }

	public ObjectNode catalog() {
		return storage.read(state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			ObjectNode result = base(state);
			ArrayNode types = result.putArray("types"), relations = result.putArray("relations"), domains = result.putArray("domains");
			Map<String, Integer> counts = new HashMap<>(); state.path("instances").forEach(value -> counts.merge(value.path("objectTypeRef").asString(), 1, Integer::sum));
			for (JsonNode value : catalog.objects.values()) { ObjectNode item = (ObjectNode) value.deepCopy(); item.put("instanceCount", counts.getOrDefault(value.path("id").asString(), 0)); types.add(item); }
			catalog.relations.values().forEach(relations::add); catalog.domains.values().forEach(domains::add);
			result.set("lastCheck", state.path("lastCheck"));
			return result;
		});
	}

	public ObjectNode instances(String type, String domain, String query, String mark, int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > 5000) throw new FactException(400, "OF_QUERY_INVALID", "page 必须大于 0，pageSize 范围为 1..5000");
		return storage.read(state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			List<JsonNode> matched = new ArrayList<>();
			for (JsonNode instance : state.path("instances")) if (matches(instance, catalog, csv(type), csv(domain), query, mark)) matched.add(instance);
			ObjectNode result = base(state); result.put("total", matched.size()); result.put("page", page); result.put("pageSize", pageSize);
			ArrayNode items = result.putArray("items");
			long start = (long) (page - 1) * pageSize;
			for (long index = start; index < Math.min(start + pageSize, matched.size()); index++) items.add(summary(matched.get((int) index), catalog));
			return result;
		});
	}

	public ObjectNode detail(String id) {
		return storage.read(state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			JsonNode instance = FactCommands.find(state, id);
			ObjectNode result = summary(instance, catalog); result.set("propertyValues", instance.path("propertyValues").deepCopy());
			ObjectNode references = references(state, catalog, id);
			result.set("outbound", references.path("outbound")); result.set("inbound", references.path("inbound"));
			ObjectNode derived = result.putObject("referenceValues");
			JsonNode type = catalog.objects.get(instance.path("objectTypeRef").asString());
			if (type != null) for (JsonNode attribute : type.path("attributes")) if ("Reference".equals(attribute.path("dataType").asString())) {
				ArrayNode values = derived.putArray(attribute.path("name").asString());
				for (JsonNode edge : references.path("outbound")) if (attribute.path("relationRef").asString().equals(edge.path("relationTypeRef").asString())) values.add(edge.path("other"));
			}
			result.put("revision", state.path("revision").asLong()); return result;
		});
	}

	public ObjectNode references(String id) {
		return storage.read(state -> { FactCommands.find(state, id); ObjectNode result = references(state, new FactCatalog(models.snapshot()), id); result.put("revision", state.path("revision").asLong()); return result; });
	}

	private ObjectNode references(ObjectNode state, FactCatalog catalog, String id) {
		Map<String, JsonNode> byId = new HashMap<>(); state.path("instances").forEach(instance -> byId.put(instance.path("instanceId").asString(), instance));
		ObjectNode result = JsonNodeFactory.instance.objectNode(); ArrayNode out = result.putArray("outbound"), in = result.putArray("inbound");
		for (JsonNode edge : state.path("relationInstances")) {
			String source = edge.path("sourceInstanceId").asString(), target = edge.path("targetInstanceId").asString();
			if (id.equals(source)) out.add(reference(edge, byId.get(target), catalog));
			if (id.equals(target)) in.add(reference(edge, byId.get(source), catalog));
		}
		return result;
	}
	private ObjectNode reference(JsonNode edge, JsonNode other, FactCatalog catalog) {
		ObjectNode result = (ObjectNode) edge.deepCopy(); result.remove("binding");
		JsonNode definition = catalog.relations.get(edge.path("relationTypeRef").asString());
		result.put("relationName", definition == null ? edge.path("relationTypeRef").asString() : definition.path("name").asString());
		if (other != null) result.set("other", summary(other, catalog));
		return result;
	}

	public ObjectNode graph(String types, String domains, String focus, int hops, int limit) {
		if (hops < 0 || hops > 3 || limit < 1 || limit > 500) throw new FactException(400, "OF_QUERY_INVALID", "hops 范围为 0..3，limit 范围为 1..500");
		return storage.read(state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			Map<String, JsonNode> eligible = new java.util.LinkedHashMap<>();
			for (JsonNode instance : state.path("instances")) if (matches(instance, catalog, csv(types), csv(domains), "", "")) eligible.put(instance.path("instanceId").asString(), instance);
			List<JsonNode> edges = FactCommands.list(state.path("relationInstances"));
			Set<String> candidates = new LinkedHashSet<>(eligible.keySet());
			if (focus != null && !focus.isBlank()) {
				FactCommands.find(state, focus);
				Map<String, List<String>> adjacent = new HashMap<>();
				for (JsonNode edge : edges) { String source = edge.path("sourceInstanceId").asString(), target = edge.path("targetInstanceId").asString(); adjacent.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target); adjacent.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source); }
				candidates.clear(); List<String> frontier = new ArrayList<>();
				if (eligible.containsKey(focus)) { candidates.add(focus); frontier.add(focus); }
				for (int hop = 0; hop < hops && !frontier.isEmpty(); hop++) {
					List<String> next = new ArrayList<>();
					for (String id : frontier) for (String other : adjacent.getOrDefault(id, List.of())) if (eligible.containsKey(other) && candidates.add(other)) next.add(other);
					frontier = next;
				}
			}
			ObjectNode result = base(state); result.put("total", candidates.size()); result.put("truncated", candidates.size() > limit);
			Set<String> chosen = new LinkedHashSet<>(candidates.stream().limit(limit).toList());
			ArrayNode nodes = result.putArray("nodes"), graphEdges = result.putArray("edges");
			chosen.forEach(id -> nodes.add(summary(eligible.get(id), catalog)));
			for (JsonNode edge : edges) if (chosen.contains(edge.path("sourceInstanceId").asString()) && chosen.contains(edge.path("targetInstanceId").asString())) {
				ObjectNode item = (ObjectNode) edge.deepCopy(); item.remove("binding");
				JsonNode definition = catalog.relations.get(edge.path("relationTypeRef").asString());
				item.put("relationName", definition == null ? edge.path("relationTypeRef").asString() : definition.path("name").asString()); graphEdges.add(item);
			}
			return result;
		});
	}

	public ObjectNode batches(int page, int size) {
		if (page < 1 || size < 1 || size > 200) throw new FactException(400, "OF_QUERY_INVALID", "操作记录分页范围不合法");
		return storage.read(state -> {
			ObjectNode result = base(state); int count = state.path("records").size(); result.put("total", count);
			ArrayNode items = result.putArray("items"); long offset = (long) (page - 1) * size;
			for (long index = count - 1L - offset; index >= Math.max(0, count - offset - size); index--) items.add(state.path("records").get((int) index));
			return result;
		});
	}

	private static ObjectNode base(ObjectNode state) { ObjectNode result = JsonNodeFactory.instance.objectNode(); result.put("revision", state.path("revision").asLong()); result.put("instanceCount", state.path("instances").size()); result.put("relationCount", state.path("relationInstances").size()); return result; }
	private static Set<String> csv(String value) { return value == null || value.isBlank() ? Set.of() : new HashSet<>(List.of(value.split(","))); }
	private static boolean matches(JsonNode instance, FactCatalog catalog, Set<String> types, Set<String> domains, String query, String mark) {
		if (!types.isEmpty() && !types.contains(instance.path("objectTypeRef").asString())) return false;
		JsonNode type = catalog.objects.get(instance.path("objectTypeRef").asString());
		if (!domains.isEmpty() && (type == null || !domains.contains(type.path("domainId").asString()))) return false;
		if (mark != null && !mark.isBlank()) { boolean found = false; for (JsonNode flag : instance.path("marks")) if (mark.equals(flag.path("markKind").asString())) found = true; if (!found) return false; }
		String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		return text.isEmpty() || summary(instance, catalog).path("displayName").asString().toLowerCase(Locale.ROOT).contains(text)
				|| instance.path("businessIdentity").toString().toLowerCase(Locale.ROOT).contains(text) || instance.path("instanceId").asString().toLowerCase(Locale.ROOT).contains(text);
	}
	public static ObjectNode summary(JsonNode instance, FactCatalog catalog) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		for (String field : new String[] { "instanceId", "instanceKey", "objectTypeRef", "businessIdentity", "sourceRef", "batchId", "marks", "createdAt", "updatedAt" }) if (instance.has(field)) result.set(field, instance.path(field).deepCopy());
		JsonNode type = catalog.objects.get(instance.path("objectTypeRef").asString());
		String domain = type == null ? "" : type.path("domainId").asString();
		JsonNode domainDefinition = catalog.domains.get(domain);
		result.put("typeName", type == null ? instance.path("objectTypeRef").asString() : type.path("name").asString()); result.put("domainId", domain);
		result.put("domainName", domainDefinition == null ? domain : domainDefinition.path("name").asString());
		result.put("domainColor", domainDefinition == null ? "#38A6F2" : domainDefinition.path("color").asString("#38A6F2"));
		String identity = String.join(" · ", instance.path("businessIdentity").properties().stream().map(entry -> entry.getValue().asString()).toList());
		result.put("businessIdentityText", identity);
		JsonNode values = instance.path("propertyValues");
		String name = "";
		for (String field : new String[] { "name", "displayName", "feature", "hostname", "metricName" }) if (values.path(field).isString() && !values.path(field).asString().isBlank()) { name = values.path(field).asString(); break; }
		result.put("displayName", name.isBlank() ? (identity.isBlank() ? instance.path("instanceId").asString() : identity) : name);
		return result;
	}
}
