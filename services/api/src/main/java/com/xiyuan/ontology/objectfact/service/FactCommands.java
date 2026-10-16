package com.xiyuan.ontology.objectfact.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.xiyuan.ontology.modeling.ModelingReadFacade;
import com.xiyuan.ontology.objectfact.api.FactException;
import com.xiyuan.ontology.objectfact.storage.FactStorage;
import com.xiyuan.ontology.objectfact.validate.FactValidator;
import com.xiyuan.ontology.objectfact.validate.FactValidator.Violation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class FactCommands {
	private final FactStorage storage;
	private final ModelingReadFacade models;
	private final FactValidator validator = new FactValidator();
	private final int maxImport;

	public FactCommands(FactStorage storage, ModelingReadFacade models,
			@Value("${xiyuan.objectfact.import.max-instances:5000}") int maxImport) {
		this.storage = storage; this.models = models; this.maxImport = maxImport;
	}

	public ObjectNode importTopology(JsonNode body, String actor, Long revision) {
		List<Violation> structure = structure(body);
		ObjectNode response = storage.write(revision, state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			ObjectNode candidate = state.deepCopy();
			List<Violation> errors = new ArrayList<>(structure);
			Map<String, String> keys = new HashMap<>();
			List<JsonNode> added = new ArrayList<>(), relations = new ArrayList<>();
			Set<String> affected = new HashSet<>();
			String batchId = next(state, "batch", "BATCH-");
			if (errors.isEmpty()) {
				for (JsonNode input : body.path("instances")) {
					ObjectNode instance = instance(candidate, input.path("objectTypeRef").asString(), input.path("propertyValues"), body.path("sourceRef").asString(), catalog);
					instance.put("instanceKey", input.path("instanceKey").asString());
					instance.put("batchId", batchId);
					keys.put(input.path("instanceKey").asString(), instance.path("instanceId").asString());
					affected.add(instance.path("instanceId").asString());
					added.add(instance);
					errors.addAll(validator.properties(instance, catalog));
				}
				List<JsonNode> all = list(state.path("instances")); all.addAll(added);
				errors.addAll(validator.unique(all, catalog, affected));
				int relationIndex = -1;
				for (JsonNode input : body.path("relations")) {
					relationIndex++;
					String source = keys.get(input.path("sourceKey").asString()), target = keys.get(input.path("targetKey").asString());
					if (source == null || target == null) {
						errors.add(new Violation(input.path("sourceKey").asString(), "relations[" + relationIndex + "]", "REFERENCE_DANGLING", "关系两端必须引用本文件内的 instanceKey"));
						continue;
					}
					ObjectNode relation = relation(candidate, input.path("relationTypeRef").asString(), source, target, catalog);
					relation.put("sourceRef", body.path("sourceRef").asString()); relation.put("batchId", batchId);
					relations.add(relation);
				}
				List<JsonNode> allRelations = list(state.path("relationInstances")); allRelations.addAll(relations);
				errors.addAll(validator.relations(all, allRelations, catalog, affected, true));
				if (errors.isEmpty()) {
					for (JsonNode addedInstance : added) {
						ObjectNode value = (ObjectNode) addedInstance;
						value.set("businessIdentity", validator.identity(value, catalog, new ArrayList<>()));
						state.withArray("instances").add(value);
					}
					relations.forEach(value -> state.withArray("relationInstances").add(value));
					state.withObject("meta").withObject("idSequence").put("instance", sequence(candidate, "instance"));
					state.withObject("meta").withObject("idSequence").put("relation", sequence(candidate, "relation"));
				}
			}
			Map<String, String> localKeys = new HashMap<>();
			added.forEach(value -> localKeys.put(value.path("instanceId").asString(), value.path("instanceKey").asString()));
			errors.replaceAll(error -> {
				List<String> conflicts = error.conflicts().stream().map(ref -> localKeys.containsKey(ref) ? "本批:" + localKeys.get(ref) : ref).toList();
				return new Violation(localKeys.getOrDefault(error.instanceKey(), error.instanceKey()), error.fieldPath(), error.rule(),
						"IDENTITY_DUPLICATE".equals(error.rule()) ? "同类型内业务标识重复：" + String.join("、", conflicts) : error.message(), conflicts);
			});
			ObjectNode record = record(state, "IMPORT", actor);
			record.put("batchId", batchId); record.put("sourceRef", body.path("sourceRef").asString("UNKNOWN"));
			record.put("importedBy", actor); record.put("importedAt", Instant.now().toString());
			record.put("instanceCount", body.path("instances").size()); record.put("relationCount", body.path("relations").size());
			record.put("result", errors.isEmpty() ? "ACCEPTED" : "REJECTED"); record.put("errorCount", errors.size());
			record.set("errorDigest", violations(errors.stream().limit(100).toList()));
			ObjectNode result = record.deepCopy(); result.set("errors", violations(errors));
			return done(state, result);
		});
		if (!structure.isEmpty()) throw new FactException(400, "OF_FILE_INVALID", "拓扑文件结构不合法", response.path("errors"));
		return response;
	}

	private List<Violation> structure(JsonNode body) {
		List<Violation> errors = new ArrayList<>();
		if (!body.isObject() || !"1.0".equals(body.path("schemaVersion").asString()) || !"instance-topology".equals(body.path("kind").asString())
				|| !body.path("sourceRef").isString() || body.path("sourceRef").asString().isBlank()
				|| !body.path("instances").isArray() || !body.path("relations").isArray()) {
			errors.add(new Violation("", "$", "STRUCT_INVALID", "文件必须含 schemaVersion=1.0、kind=instance-topology、sourceRef、instances[]、relations[]")); return errors;
		}
		if (body.path("instances").size() > maxImport || body.path("relations").size() > 10000)
			errors.add(new Violation("", "$", "STRUCT_INVALID", "单文件最多 " + maxImport + " 个实例和 10000 条关系，请拆分拓扑"));
		Set<String> keys = new HashSet<>();
		for (JsonNode input : body.path("instances")) {
			String key = input.path("instanceKey").asString("");
			if (key.isBlank() || !keys.add(key) || !input.path("objectTypeRef").isString() || !input.path("propertyValues").isObject())
				errors.add(new Violation(key, "instances", "STRUCT_INVALID", "实例须有唯一 instanceKey、objectTypeRef 和 propertyValues 对象"));
		}
		for (JsonNode input : body.path("relations")) if (!input.path("relationTypeRef").isString() || !input.path("sourceKey").isString() || !input.path("targetKey").isString())
			errors.add(new Violation("", "relations", "STRUCT_INVALID", "关系须含 relationTypeRef、sourceKey 和 targetKey"));
		return errors;
	}

	public ObjectNode create(JsonNode body, String actor, Long revision) {
		return storage.write(revision, state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			ObjectNode created = instance(state, body.path("objectTypeRef").asString(), body.path("propertyValues"), "MANUAL", catalog);
			List<Violation> errors = validator.properties(created, catalog);
			List<JsonNode> all = list(state.path("instances")); all.add(created);
			String id = created.path("instanceId").asString();
			errors.addAll(validator.unique(all, catalog, Set.of(id)));
			reject(errors);
			created.set("businessIdentity", validator.identity(created, catalog, new ArrayList<>()));
			state.withArray("instances").add(created);
			// Single-point creation validates identity/values. Required relations can be
			// established in the next whole-card edit; /check reports any missing links.
			ObjectNode record = record(state, "INSTANCE_CREATE", actor);
			record.put("instanceRef", id); record.put("objectTypeRef", created.path("objectTypeRef").asString());
			record.set("businessIdentity", created.path("businessIdentity").deepCopy());
			return done(state, created.deepCopy());
		});
	}

	public ObjectNode edit(String id, JsonNode body, String actor, Long revision) {
		requireRevision(revision);
		return storage.write(revision, state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			ObjectNode current = find(state, id);
			ObjectNode before = current.deepCopy();
			List<Violation> errors = new ArrayList<>();
			JsonNode type = catalog.objects.get(current.path("objectTypeRef").asString());
			if (body.has("propertyValues") && !body.path("propertyValues").isObject()) throw new FactException(400, "OF_FILE_INVALID", "propertyValues 必须是对象");
			for (String name : body.path("propertyValues").propertyNames()) {
				JsonNode value = body.path("propertyValues").path(name);
				JsonNode attribute = type == null ? null : FactCatalog.attribute(type, name);
				if (attribute != null && !attribute.path("readOnlyNature").asString("").isBlank() && !value.equals(current.path("propertyValues").path(name)))
					errors.add(new Violation(id, "propertyValues." + name, "EDIT_READ_ONLY", "只读属性不可修改：" + attribute.path("readOnlyNature").asString()));
				current.withObject("propertyValues").set(name, value.deepCopy());
			}
			if (body.has("unsetProperties") && !body.path("unsetProperties").isArray()) throw new FactException(400, "OF_FILE_INVALID", "unsetProperties 必须是属性名数组");
			for (JsonNode field : body.path("unsetProperties")) {
				if (!field.isString()) throw new FactException(400, "OF_FILE_INVALID", "移除属性时必须提供属性名");
				String name = field.asString(); JsonNode attribute = type == null ? null : FactCatalog.attribute(type, name);
				if (attribute != null && !attribute.path("readOnlyNature").asString("").isBlank()) errors.add(new Violation(id, "propertyValues." + name, "EDIT_READ_ONLY", "只读属性不可移除"));
				current.withObject("propertyValues").remove(name);
			}
			Set<String> affected = new HashSet<>(Set.of(id));
			ArrayNode changes = JsonNodeFactory.instance.arrayNode();
			JsonNode operations = body.path("relationOps");
			if (!operations.isMissingNode() && !operations.isArray()) throw new FactException(400, "OF_FILE_INVALID", "relationOps 必须是数组");
			for (JsonNode operation : operations) {
				String op = operation.path("op").asString();
				if ("remove".equals(op)) {
					String relationId = operation.path("relationId").asString();
					ArrayNode edges = state.withArray("relationInstances");
					int index = -1;
					for (int i = 0; i < edges.size(); i++) if (relationId.equals(edges.get(i).path("relationId").asString())) index = i;
					if (index < 0) throw new FactException(404, "OF_RELATION_NOT_FOUND", "待移除关系不存在：" + relationId);
					JsonNode edge = edges.get(index);
					if (!id.equals(edge.path("sourceInstanceId").asString()) && !id.equals(edge.path("targetInstanceId").asString()))
						throw new FactException(400, "OF_RELATION_SCOPE_INVALID", "仅可编辑当前实例关联的关系");
					affected.add(edge.path("sourceInstanceId").asString()); affected.add(edge.path("targetInstanceId").asString());
					changes.add(edge.deepCopy()); edges.remove(index);
				} else if ("add".equals(op)) {
					String source = operation.path("sourceInstanceId").asString(id), target = operation.path("targetInstanceId").asString();
					if (!id.equals(source) && !id.equals(target)) throw new FactException(400, "OF_RELATION_SCOPE_INVALID", "新增关系必须关联当前实例");
					ObjectNode edge = relation(state, operation.path("relationTypeRef").asString(), source, target, catalog);
					state.withArray("relationInstances").add(edge); changes.add(edge.deepCopy());
					affected.add(source); affected.add(target);
				} else throw new FactException(400, "OF_FILE_INVALID", "关系操作仅支持 add/remove");
			}
			boolean deleting = body.path("deleteInstance").asBoolean(false);
			if (deleting) {
				protect(state, id);
				removeInstance(state, id);
				affected.remove(id);
			} else {
				errors.addAll(validator.properties(current, catalog));
				errors.addAll(validator.unique(list(state.path("instances")), catalog, Set.of(id)));
			}
			if (!operations.isMissingNode() || deleting) {
				// Check changed relation definitions on both ends. An unrelated missing
				// required relationship from manual creation must not block this edit.
				Set<String> changedTypes = new HashSet<>(); changes.forEach(edge -> changedTypes.add(edge.path("relationTypeRef").asString()));
				List<Violation> relationErrors = validator.relations(list(state.path("instances")), list(state.path("relationInstances")), catalog, affected, true);
				for (Violation error : relationErrors) if (!"CARDINALITY_OUT_OF_RANGE".equals(error.rule()) || changedTypes.stream().anyMatch(ref -> error.fieldPath().equals("relations." + ref))) errors.add(error);
			}
			reject(errors);
			if (!deleting) { current.set("businessIdentity", validator.identity(current, catalog, new ArrayList<>())); current.set("binding", catalog.binding(current)); current.put("updatedAt", Instant.now().toString()); }
			ObjectNode record = record(state, deleting ? "INSTANCE_DELETE" : "EDIT", actor);
			record.put("instanceRef", id);
			ObjectNode summary = record.putObject("changeSummary");
			summary.set("propertyValues", body.path("propertyValues").isMissingNode() ? JsonNodeFactory.instance.objectNode() : body.path("propertyValues").deepCopy());
			summary.set("unsetProperties", body.path("unsetProperties").isMissingNode() ? JsonNodeFactory.instance.arrayNode() : body.path("unsetProperties").deepCopy());
			summary.set("relationOps", operations.isMissingNode() ? JsonNodeFactory.instance.arrayNode() : operations.deepCopy());
			summary.put("deleted", deleting);
			if (deleting) record.set("removedRelationRefs", changes);
			ObjectNode response = deleting ? JsonNodeFactory.instance.objectNode().put("deleted", true).put("instanceId", id) : current.deepCopy();
			response.put("operationId", record.path("operationId").asString());
			return done(state, response);
		});
	}

	public ObjectNode delete(String id, String actor, Long revision) {
		requireRevision(revision);
		return storage.write(revision, state -> {
			find(state, id); protect(state, id); removeInstance(state, id);
			ObjectNode record = record(state, "INSTANCE_DELETE", actor); record.put("instanceRef", id);
			record.putArray("removedRelationRefs");
			return done(state, JsonNodeFactory.instance.objectNode().put("deleted", true).put("instanceId", id));
		});
	}

	public ObjectNode purge(boolean confirm, String actor, Long revision) {
		if (!confirm) throw new FactException(400, "OF_CONFIRM_REQUIRED", "清除实例数据必须二次确认");
		requireRevision(revision);
		return storage.write(revision, state -> {
			ObjectNode record = record(state, "PURGE", actor);
			record.put("confirmedAt", Instant.now().toString());
			record.putObject("purgedCounts").put("instances", state.path("instances").size()).put("relations", state.path("relationInstances").size());
			state.putArray("instances"); state.putArray("relationInstances");
			return done(state, record.deepCopy());
		});
	}

	public ObjectNode check(String actor, Long revision) {
		return storage.write(revision, state -> {
			FactCatalog catalog = new FactCatalog(models.snapshot());
			List<JsonNode> instances = list(state.path("instances")), edges = list(state.path("relationInstances"));
			Set<String> ids = new HashSet<>(); instances.forEach(value -> ids.add(value.path("instanceId").asString()));
			List<Violation> validation = new ArrayList<>(validator.unique(instances, catalog, ids));
			validation.addAll(validator.relations(instances, edges, catalog, ids, true));
			Map<String, JsonNode> byId = new HashMap<>();
			Map<String, List<JsonNode>> adjacency = new HashMap<>();
			Map<String, List<Violation>> validationById = new HashMap<>();
			instances.forEach(value -> byId.put(value.path("instanceId").asString(), value));
			for (JsonNode edge : edges) {
				adjacency.computeIfAbsent(edge.path("sourceInstanceId").asString(), ignored -> new ArrayList<>()).add(edge);
				if (!edge.path("sourceInstanceId").equals(edge.path("targetInstanceId"))) adjacency.computeIfAbsent(edge.path("targetInstanceId").asString(), ignored -> new ArrayList<>()).add(edge);
			}
			validation.forEach(error -> validationById.computeIfAbsent(error.instanceKey(), ignored -> new ArrayList<>()).add(error));
			ObjectNode result = JsonNodeFactory.instance.objectNode();
			ArrayNode pending = result.putArray("pendingReview"), invalid = result.putArray("invalidRef");
			for (JsonNode node : instances) {
				ObjectNode instance = (ObjectNode) node;
				JsonNode previousMarks = instance.path("marks").deepCopy();
				ArrayNode marks = instance.putArray("marks");
				String id = instance.path("instanceId").asString(), typeRef = instance.path("objectTypeRef").asString();
				JsonNode type = catalog.objects.get(typeRef);
				if (!FactCatalog.active(type)) mark(marks, "invalidRef", typeRef, "对象类型已删除或废弃");
				else {
					JsonNode binding = instance.path("binding");
					if (!binding.path("identity").asString().equals(FactCatalog.digest(type.path("identitySpec")))) mark(marks, "pendingReview", typeRef, "业务标识定义发生变更");
					for (String name : binding.path("attributes").propertyNames()) {
						JsonNode attribute = FactCatalog.attribute(type, name);
						if (attribute == null || !binding.path("attributes").path(name).asString().equals(FactCatalog.attributeSignature(attribute)))
							mark(marks, "pendingReview", typeRef + "/" + name, "属性已删除或定义发生变更：" + name);
					}
					for (Violation error : validator.properties(instance, catalog)) mark(marks, "pendingReview", typeRef, error.message());
				}
				for (JsonNode edge : adjacency.getOrDefault(id, List.of())) {
					String ref = edge.path("relationTypeRef").asString(); JsonNode definition = catalog.relations.get(ref);
					if (!FactCatalog.active(definition)) mark(marks, "invalidRef", ref, "关系类型已删除或废弃");
					else if (!edge.path("binding").asString().equals(FactCatalog.relationSignature(definition))) mark(marks, "pendingReview", ref, "关系两端或基数定义发生变更");
					String other = id.equals(edge.path("sourceInstanceId").asString()) ? edge.path("targetInstanceId").asString() : edge.path("sourceInstanceId").asString();
					JsonNode opposite = byId.get(other);
					if (opposite == null || !FactCatalog.active(catalog.objects.get(opposite.path("objectTypeRef").asString()))) mark(marks, "invalidRef", ref, "关联实例的对象类型已删除或废弃");
				}
				for (Violation error : validationById.getOrDefault(id, List.of())) mark(marks, "pendingReview", error.fieldPath(), error.message());
				for (JsonNode flag : marks) for (JsonNode previous : previousMarks) {
					if (flag.path("markKind").equals(previous.path("markKind")) && flag.path("modelElementRef").equals(previous.path("modelElementRef")) && flag.path("reason").equals(previous.path("reason")))
						((ObjectNode) flag).set("markedAt", previous.path("markedAt"));
				}
				boolean hasInvalid = false, hasPending = false;
				for (JsonNode flag : marks) { if ("invalidRef".equals(flag.path("markKind").asString())) hasInvalid = true; else hasPending = true; }
				if (hasInvalid) invalid.add(id); if (hasPending) pending.add(id);
			}
			result.put("checkedAt", Instant.now().toString()); result.put("checkedBy", actor); result.put("checkedCount", instances.size());
			state.set("lastCheck", result.deepCopy());
			return done(state, result);
		});
	}

	private ObjectNode instance(ObjectNode state, String type, JsonNode values, String source, FactCatalog catalog) {
		ObjectNode instance = JsonNodeFactory.instance.objectNode();
		instance.put("instanceId", next(state, "instance", "INST-")); instance.put("objectTypeRef", type);
		instance.set("propertyValues", values.deepCopy()); instance.put("sourceRef", source);
		instance.put("createdAt", Instant.now().toString()); instance.putArray("marks");
		instance.set("binding", catalog.binding(instance));
		return instance;
	}
	private ObjectNode relation(ObjectNode state, String ref, String source, String target, FactCatalog catalog) {
		ObjectNode relation = JsonNodeFactory.instance.objectNode();
		relation.put("relationId", next(state, "relation", "RELI-")); relation.put("relationTypeRef", ref);
		relation.put("sourceInstanceId", source); relation.put("targetInstanceId", target); relation.put("sourceRef", "MANUAL");
		if (catalog.relations.containsKey(ref)) relation.put("binding", FactCatalog.relationSignature(catalog.relations.get(ref)));
		return relation;
	}
	private ObjectNode record(ObjectNode state, String kind, String actor) {
		ObjectNode record = JsonNodeFactory.instance.objectNode();
		record.put("recordKind", kind); record.put("operationId", next(state, "operation", "OP-")); record.put("operatedBy", actor); record.put("operatedAt", Instant.now().toString());
		state.withArray("records").add(record); return record;
	}
	private static ObjectNode done(ObjectNode state, ObjectNode response) { response.put("revision", state.path("revision").asLong() + 1); return response; }
	private static long sequence(ObjectNode state, String key) { return state.path("meta").path("idSequence").path(key).asLong(); }
	private static String next(ObjectNode state, String key, String prefix) { long number = sequence(state, key) + 1; state.withObject("meta").withObject("idSequence").put(key, number); return prefix + String.format("%06d", number); }
	public static List<JsonNode> list(JsonNode values) { List<JsonNode> result = new ArrayList<>(); values.forEach(result::add); return result; }
	public static ObjectNode find(ObjectNode state, String id) {
		for (JsonNode instance : state.path("instances")) if (id.equals(instance.path("instanceId").asString())) return (ObjectNode) instance;
		throw new FactException(404, "OF_INSTANCE_NOT_FOUND", "对象实例不存在：" + id);
	}
	private static void protect(ObjectNode state, String id) {
		List<JsonNode> refs = list(state.path("relationInstances")).stream().filter(edge -> id.equals(edge.path("sourceInstanceId").asString()) || id.equals(edge.path("targetInstanceId").asString())).toList();
		if (!refs.isEmpty()) throw new FactException(409, "OF_DELETE_PROTECTED", "实例仍有关联关系，请先明确解除引用", refs);
	}
	private static void removeInstance(ObjectNode state, String id) { ArrayNode values = state.withArray("instances"); for (int i = 0; i < values.size(); i++) if (id.equals(values.get(i).path("instanceId").asString())) { values.remove(i); return; } }
	private static void reject(List<Violation> errors) { if (!errors.isEmpty()) throw new FactException(422, "OF_VALIDATION_FAILED", "实例校验未通过", errors); }
	private static void requireRevision(Long revision) { if (revision == null) throw new FactException(400, "OF_REVISION_REQUIRED", "修改操作必须携带 If-Match 实例数据修订号"); }
	private static ArrayNode violations(List<Violation> errors) {
		ArrayNode output = JsonNodeFactory.instance.arrayNode();
		for (Violation error : errors) { ObjectNode item = output.addObject(); item.put("instanceKey", error.instanceKey()); item.put("fieldPath", error.fieldPath()); item.put("rule", error.rule()); item.put("message", error.message()); ArrayNode conflicts = item.putArray("conflicts"); error.conflicts().forEach(conflicts::add); }
		return output;
	}
	private static void mark(ArrayNode marks, String kind, String ref, String reason) {
		for (JsonNode existing : marks) if (kind.equals(existing.path("markKind").asString()) && ref.equals(existing.path("modelElementRef").asString()) && reason.equals(existing.path("reason").asString())) return;
		marks.addObject().put("markKind", kind).put("modelElementRef", ref).put("reason", reason).put("markedAt", Instant.now().toString());
	}
}
