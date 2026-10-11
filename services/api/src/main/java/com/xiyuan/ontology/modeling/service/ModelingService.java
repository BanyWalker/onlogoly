package com.xiyuan.ontology.modeling.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xiyuan.ontology.modeling.api.ApiException;
import com.xiyuan.ontology.modeling.api.ErrorDetail;
import com.xiyuan.ontology.modeling.storage.WorkspaceStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 对象建模应用服务（TDD-OM-002 §9 API 契约 + §13.1 MVP 范围）。
 *
 * <p>MVP 校验口径：保存级必填（DEV-6 四要素）、标识唯一、引用存在、基数合法、
 * 领域删除保护、If-Match 乐观锁；全量校验矩阵与影响标记（S8）暂缓。
 */
@Service
public class ModelingService {

	private static final String ACTOR = "modeler-local";

	private static final String[] DOMAIN_PALETTE = { "#2F80C9", "#4D9DE0", "#3BA7A0", "#5B8DEF", "#6B7FD7",
			"#3C9DAD", "#55708A", "#2A6FB0", "#4A86C5", "#477A91" };

	private static final Set<String> DATA_TYPES = Set.of("String", "Integer", "Decimal", "Boolean", "DateTime",
			"Reference", "Enumeration");

	private static final Set<String> COMPOSITION_KINDS = Set.of("Association", "Aggregation", "Composition");

	private static final Set<String> NULL_MEANINGS = Set.of("UNKNOWN", "NOT_PROVIDED", "NOT_APPLICABLE");

	private final WorkspaceStore store;

	public ModelingService(WorkspaceStore store) {
		this.store = store;
	}

	// ---------------------------------------------------------------- 画布与导出

	public ObjectNode graph(String domainId) {
		return this.store.read(doc -> {
			ObjectNode data = JsonNodeFactory.instance.objectNode();
			data.put("layoutRevision", this.store.layoutRevision());
			ArrayNode domains = data.putArray("domains");
			ArrayNode domainArray = (ArrayNode) doc.path("domains");
			for (int i = 0; i < domainArray.size(); i++) {
				ObjectNode domain = (ObjectNode) domainArray.get(i);
				ObjectNode item = domain.deepCopy();
				item.put("color", DOMAIN_PALETTE[i % DOMAIN_PALETTE.length]);
				item.put("objectCount", countObjectsOfDomain(doc, domain.path("domainId").asString()));
				domains.add(item);
			}
			ArrayNode objects = data.putArray("objects");
			for (JsonNode object : doc.path("objects")) {
				if (domainId != null && !domainId.isBlank()
						&& !domainId.equals(object.path("domainId").asString())) {
					continue;
				}
				objects.add(toGraphObject((ObjectNode) object));
			}
			ArrayNode relations = data.putArray("relations");
			for (JsonNode relation : doc.path("relations")) {
				if (domainId != null && !domainId.isBlank()) {
					String sourceDomain = domainOfObject(doc, relation.path("sourceType").asString());
					String targetDomain = domainOfObject(doc, firstTarget(relation));
					if (!domainId.equals(sourceDomain) && !domainId.equals(targetDomain)) {
						continue;
					}
				}
				relations.add(toGraphRelation((ObjectNode) relation));
			}
			return data;
		});
	}

	public ObjectNode exportModel() {
		return this.store.read(doc -> {
			ObjectNode export = doc.deepCopy();
			ObjectNode meta = JsonNodeFactory.instance.objectNode();
			meta.put("generatedAt", Instant.now().toString());
			meta.put("generator", "xiyuan-modeling-api/1.0");
			ObjectNode counts = meta.putObject("counts");
			counts.put("objects", export.path("objects").size());
			counts.put("relations", export.path("relations").size());
			int attributes = 0;
			for (JsonNode object : export.path("objects")) {
				attributes += object.path("attributes").size();
			}
			counts.put("attributes", attributes);
			counts.put("domains", export.path("domains").size());
			export.set("meta", meta);
			return export;
		});
	}

	// ---------------------------------------------------------------- 对象类型

	public ObjectNode createObjectType(JsonNode body) {
		ObjectNode created = this.store.mutate(doc -> {
			String id = optionalText(body, "id");
			if (id == null) {
				id = generateId("OBJ");
			}
			assertGloballyUnique(doc, id, null);
			String name = requireText(body, "name", "OBJ_REQUIRED_MISSING", id);
			String definition = requireText(body, "definition", "OBJ_REQUIRED_MISSING", id);
			String domainId = requireText(body, "domainId", "OBJ_REQUIRED_MISSING", id);
			assertDomainExists(doc, domainId);

			ObjectNode object = JsonNodeFactory.instance.objectNode();
			object.put("id", id);
			object.put("name", name);
			object.put("definition", definition);
			putIfText(object, "scope", body);
			ObjectNode boundary = object.putObject("boundary");
			JsonNode bodyBoundary = body.path("boundary");
			boundary.put("includes", bodyBoundary.path("includes").asString(null));
			boundary.put("excludes", bodyBoundary.path("excludes").asString(null));
			object.put("domainId", domainId);
			ObjectNode identitySpec = object.putObject("identitySpec");
			identitySpec.set("attributes", JsonNodeFactory.instance.arrayNode());
			identitySpec.put("mode", "SINGLE");
			identitySpec.putNull("uniqueScope");
			identitySpec.putNull("source");
			identitySpec.putNull("missingMeaning");
			object.set("attributes", JsonNodeFactory.instance.arrayNode());
			object.set("staticConstraints", JsonNodeFactory.instance.arrayNode());
			object.set("termRefs", body.path("termRefs").isArray() ? body.path("termRefs").deepCopy()
					: JsonNodeFactory.instance.arrayNode());
			ObjectNode graph = object.putObject("graph");
			graph.put("x", body.path("graph").path("x").asInt(120));
			graph.put("y", body.path("graph").path("y").asInt(120));
			object.put("completeness", "draft");
			object.put("status", "active");
			object.put("revision", 1);
			object.put("changeReason", optionalText(body, "changeReason") == null ? "初始创建"
					: optionalText(body, "changeReason"));
			object.put("owner", optionalText(body, "owner") == null ? "建模人员" : optionalText(body, "owner"));
			object.set("source", body.path("source").isArray() ? body.path("source").deepCopy()
					: JsonNodeFactory.instance.arrayNode());
			stampCreated(object);

			((ArrayNode) doc.path("objects")).add(object);
			applyAttributes(doc, object, body);
			if (body.path("identitySpec").isObject()) {
				validateIdentitySpec(object, body.path("identitySpec"), id);
				object.set("identitySpec", body.path("identitySpec").deepCopy());
			}
			appendRevision(id, "CREATE", object.path("changeReason").asString(), object);
			return object;
		});
		this.store.appendEvent(this.store.newEvent("OBJECT_TYPE_CREATED", created.path("id").asString()));
		return created;
	}

	public ObjectNode getObjectType(String id) {
		return this.store.read(doc -> {
			ObjectNode object = findObject(doc, id);
			if (object == null) {
				throw ApiException.notFound("OBJECT_TYPE_NOT_FOUND", "对象类型不存在: " + id);
			}
			return object.deepCopy();
		});
	}

	public ObjectNode updateObjectType(String id, JsonNode body, Integer ifMatch) {
		ObjectNode updated = this.store.mutate(doc -> {
			ObjectNode object = findObject(doc, id);
			if (object == null) {
				throw ApiException.notFound("OBJECT_TYPE_NOT_FOUND", "对象类型不存在: " + id);
			}
			assertRevision(object, ifMatch, id);

			String newId = body.has("id") ? requireText(body, "id", "OBJ_REQUIRED_MISSING", id) : null;
			if (newId != null && !newId.equals(id)) {
				assertGloballyUnique(doc, newId, id);
				rewriteObjectIdReferences(doc, id, newId);
				object.put("id", newId);
			}
			if (body.has("name")) {
				object.put("name", requireText(body, "name", "OBJ_REQUIRED_MISSING", id));
			}
			if (body.has("definition")) {
				object.put("definition", requireText(body, "definition", "OBJ_REQUIRED_MISSING", id));
			}
			if (body.has("domainId")) {
				String domainId = requireText(body, "domainId", "OBJ_REQUIRED_MISSING", id);
				assertDomainExists(doc, domainId);
				object.put("domainId", domainId);
			}
			if (body.has("scope")) {
				putIfText(object, "scope", body);
			}
			if (body.has("boundary") && body.path("boundary").isObject()) {
				ObjectNode boundary = object.putObject("boundary");
				boundary.put("includes", body.path("boundary").path("includes").asString(null));
				boundary.put("excludes", body.path("boundary").path("excludes").asString(null));
			}
			applyAttributes(doc, object, body);
			if (body.has("identitySpec") && body.path("identitySpec").isObject()) {
				validateIdentitySpec(object, body.path("identitySpec"), id);
				object.set("identitySpec", body.path("identitySpec").deepCopy());
			}
			validateIdentitySpec(object, object.path("identitySpec"), id);
			if (body.has("termRefs") && body.path("termRefs").isArray()) {
				object.set("termRefs", body.path("termRefs").deepCopy());
			}
			if (body.has("completeness")) {
				String completeness = optionalText(body, "completeness");
				if ("draft".equals(completeness) || "complete".equals(completeness)) {
					object.put("completeness", completeness);
				}
			}

			String finalId = object.path("id").asString();
			bumpRevision(object, body);
			appendRevision(finalId, "UPDATE", object.path("changeReason").asString(), object);
			return object;
		});
		this.store.appendEvent(this.store.newEvent("OBJECT_TYPE_UPDATED", updated.path("id").asString()));
		return updated;
	}

	public ObjectNode deprecateObjectType(String id, Integer ifMatch) {
		ObjectNode updated = this.store.mutate(doc -> {
			ObjectNode object = findObject(doc, id);
			if (object == null) {
				throw ApiException.notFound("OBJECT_TYPE_NOT_FOUND", "对象类型不存在: " + id);
			}
			assertRevision(object, ifMatch, id);
			List<ErrorDetail> references = new ArrayList<>();
			for (JsonNode relation : doc.path("relations")) {
				if (!"deprecated".equals(relation.path("status").asString()) && endpointsInclude(relation, id)) {
					references.add(new ErrorDetail("relations", relation.path("id").asString(), id));
				}
			}
			for (JsonNode other : doc.path("objects")) {
				for (JsonNode attr : other.path("attributes")) {
					for (JsonNode target : attr.path("referenceTarget")) {
						if (id.equals(target.asString())) {
							references.add(new ErrorDetail("attributes", attr.path("id").asString(), id));
						}
					}
				}
			}
			if (!references.isEmpty()) {
				throw ApiException.conflict("OBJ_DELETE_PROTECTED", "对象仍被引用，废弃请求被拒绝", references);
			}
			object.put("status", "deprecated");
			bumpRevision(object, null);
			object.put("changeReason", "废弃");
			appendRevision(id, "DEPRECATE", "废弃", object);
			return object;
		});
		this.store.appendEvent(this.store.newEvent("OBJECT_TYPE_DEPRECATED", id));
		return updated;
	}

	// ---------------------------------------------------------------- 属性

	public ObjectNode createAttribute(String objectId, JsonNode body) {
		ObjectNode updated = this.store.mutate(doc -> {
			ObjectNode object = requireObject(doc, objectId);
			String attrId = optionalText(body, "id");
			if (attrId == null) {
				attrId = generateId("ATTR");
			}
			assertAttributeIdUnique(doc, attrId);
			ObjectNode attribute = buildAttribute(doc, object, attrId, body, null);
			((ArrayNode) object.path("attributes")).add(attribute);
			bumpRevision(object, body);
			appendRevision(objectId, "ATTRIBUTE_CREATED", object.path("changeReason").asString(), object);
			return attribute;
		});
		this.store.appendEvent(this.store.newEvent("ATTRIBUTE_CREATED", updated.path("id").asString()));
		return updated;
	}

	public ObjectNode updateAttribute(String objectId, String attrId, JsonNode body) {
		ObjectNode updated = this.store.mutate(doc -> {
			ObjectNode object = requireObject(doc, objectId);
			ObjectNode attribute = findAttribute(object, attrId);
			if (attribute == null) {
				throw ApiException.notFound("ATTRIBUTE_NOT_FOUND", "属性不存在: " + attrId);
			}
			buildAttribute(doc, object, attrId, body, attribute);
			bumpRevision(object, body);
			appendRevision(objectId, "ATTRIBUTE_UPDATED", object.path("changeReason").asString(), object);
			return attribute;
		});
		this.store.appendEvent(this.store.newEvent("ATTRIBUTE_UPDATED", attrId));
		return updated;
	}

	public ObjectNode deleteAttribute(String objectId, String attrId) {
		ObjectNode updated = this.store.mutate(doc -> {
			ObjectNode object = requireObject(doc, objectId);
			for (JsonNode identityAttr : object.path("identitySpec").path("attributes")) {
				if (attrId.equals(identityAttr.asString())) {
					throw ApiException.conflict("OBJ_DELETE_PROTECTED", "属性被业务标识引用，删除被拒绝",
							List.of(new ErrorDetail("identitySpec.attributes", attrId, objectId)));
				}
			}
			ArrayNode attributes = (ArrayNode) object.path("attributes");
			boolean removed = false;
			for (int i = 0; i < attributes.size(); i++) {
				if (attrId.equals(attributes.get(i).path("id").asString())) {
					attributes.remove(i);
					removed = true;
					break;
				}
			}
			if (!removed) {
				throw ApiException.notFound("ATTRIBUTE_NOT_FOUND", "属性不存在: " + attrId);
			}
			bumpRevision(object, null);
			appendRevision(objectId, "ATTRIBUTE_DELETED", "删除属性 " + attrId, object);
			return object;
		});
		this.store.appendEvent(this.store.newEvent("ATTRIBUTE_DELETED", attrId));
		return updated;
	}

	// ---------------------------------------------------------------- 关系

	public ObjectNode createRelation(JsonNode body) {
		ObjectNode created = this.store.mutate(doc -> {
			String id = optionalText(body, "id");
			if (id == null) {
				id = generateId("REL");
			}
			assertGloballyUnique(doc, id, null);
			ObjectNode relation = buildRelation(doc, id, body, null);
			((ArrayNode) doc.path("relations")).add(relation);
			appendRevision(id, "CREATE", relation.path("changeReason").asString(), relation);
			return relation;
		});
		this.store.appendEvent(this.store.newEvent("RELATION_CREATED", created.path("id").asString()));
		return created;
	}

	public ObjectNode getRelation(String id) {
		return this.store.read(doc -> {
			ObjectNode relation = findRelation(doc, id);
			if (relation == null) throw ApiException.notFound("RELATION_NOT_FOUND", "关系不存在: " + id);
			return relation.deepCopy();
		});
	}

	public ObjectNode updateRelation(String id, JsonNode body, Integer ifMatch) {
		ObjectNode updated = this.store.mutate(doc -> {
			ObjectNode relation = findRelation(doc, id);
			if (relation == null) {
				throw ApiException.notFound("RELATION_NOT_FOUND", "关系不存在: " + id);
			}
			assertRevision(relation, ifMatch, id);
			buildRelation(doc, id, body, relation);
			bumpRevision(relation, body);
			appendRevision(id, "UPDATE", relation.path("changeReason").asString(), relation);
			return relation;
		});
		this.store.appendEvent(this.store.newEvent("RELATION_UPDATED", id));
		return updated;
	}

	public ObjectNode deleteRelation(String id) {
		ObjectNode removed = this.store.mutate(doc -> {
			ObjectNode relation = findRelation(doc, id);
			if (relation == null) {
				throw ApiException.notFound("RELATION_NOT_FOUND", "关系不存在: " + id);
			}
			List<ErrorDetail> references = new ArrayList<>();
			for (JsonNode object : doc.path("objects")) {
				for (JsonNode attr : object.path("attributes")) {
					if (id.equals(attr.path("relationRef").asString(null))) {
						references.add(new ErrorDetail("attributes", attr.path("id").asString(), id));
					}
				}
			}
			if (!references.isEmpty()) {
				throw ApiException.conflict("OBJ_DELETE_PROTECTED", "关系被引用型属性引用，删除被拒绝", references);
			}
			ArrayNode relations = (ArrayNode) doc.path("relations");
			for (int i = 0; i < relations.size(); i++) {
				if (id.equals(relations.get(i).path("id").asString())) {
					relations.remove(i);
					break;
				}
			}
			appendRevision(id, "DELETE", "删除关系", relation);
			return relation;
		});
		this.store.appendEvent(this.store.newEvent("RELATION_DELETED", id));
		return removed;
	}

	// ---------------------------------------------------------------- 领域

	public ArrayNode listDomains() {
		return this.store.read(doc -> {
			ArrayNode result = JsonNodeFactory.instance.arrayNode();
			for (JsonNode domain : doc.path("domains")) {
				ObjectNode item = ((ObjectNode) domain).deepCopy();
				item.put("objectCount", countObjectsOfDomain(doc, domain.path("domainId").asString()));
				result.add(item);
			}
			return result;
		});
	}

	public ObjectNode createDomain(JsonNode body) {
		ObjectNode created = this.store.mutate(doc -> {
			String name = requireText(body, "name", "DOMAIN_REQUIRED_MISSING", null);
			String ownerRole = requireText(body, "ownerRole", "DOMAIN_REQUIRED_MISSING", null);
			String domainId = optionalText(body, "domainId");
			if (domainId == null) {
				domainId = generateId("DOMAIN");
			}
			if (findDomain(doc, domainId) != null) {
				throw ApiException.badRequest("DOMAIN_ID_DUPLICATE", "领域标识重复: " + domainId,
						List.of(new ErrorDetail("domainId", domainId, domainId)));
			}
			assertGloballyUnique(doc, domainId, null);
			ObjectNode domain = JsonNodeFactory.instance.objectNode();
			domain.put("domainId", domainId);
			domain.put("name", name);
			putIfText(domain, "description", body);
			domain.put("ownerRole", ownerRole);
			((ArrayNode) doc.path("domains")).add(domain);
			return domain;
		});
		this.store.appendEvent(this.store.newEvent("DOMAIN_CREATED", created.path("domainId").asString()));
		return created;
	}

	public ObjectNode updateDomain(String id, JsonNode body) {
		ObjectNode updated = this.store.mutate(doc -> {
			ObjectNode domain = findDomain(doc, id);
			if (domain == null) {
				throw ApiException.notFound("DOMAIN_NOT_FOUND", "领域不存在: " + id);
			}
			if (body.has("name")) {
				domain.put("name", requireText(body, "name", "DOMAIN_REQUIRED_MISSING", id));
			}
			if (body.has("description")) {
				putIfText(domain, "description", body);
			}
			if (body.has("ownerRole")) {
				domain.put("ownerRole", requireText(body, "ownerRole", "DOMAIN_REQUIRED_MISSING", id));
			}
			return domain;
		});
		this.store.appendEvent(this.store.newEvent("DOMAIN_UPDATED", id));
		return updated;
	}

	public ObjectNode deleteDomain(String id) {
		ObjectNode removed = this.store.mutate(doc -> {
			ObjectNode domain = findDomain(doc, id);
			if (domain == null) {
				throw ApiException.notFound("DOMAIN_NOT_FOUND", "领域不存在: " + id);
			}
			List<ErrorDetail> usage = new ArrayList<>();
			for (JsonNode object : doc.path("objects")) {
				if (id.equals(object.path("domainId").asString())) {
					usage.add(new ErrorDetail("objects", object.path("id").asString(), id));
				}
			}
			for (JsonNode relation : doc.path("relations")) {
				if (id.equals(relation.path("domainId").asString())) {
					usage.add(new ErrorDetail("relations", relation.path("id").asString(), id));
				}
			}
			if (!usage.isEmpty()) {
				throw ApiException.conflict("DOMAIN_IN_USE", "领域仍被对象或关系使用，删除被拒绝", usage);
			}
			ArrayNode domains = (ArrayNode) doc.path("domains");
			for (int i = 0; i < domains.size(); i++) {
				if (id.equals(domains.get(i).path("domainId").asString())) {
					domains.remove(i);
					break;
				}
			}
			return domain;
		});
		this.store.appendEvent(this.store.newEvent("DOMAIN_DELETED", id));
		return removed;
	}

	// ---------------------------------------------------------------- 布局

	public ObjectNode saveLayout(Integer ifMatch, JsonNode positions) {
		if (ifMatch == null) {
			throw ApiException.badRequest("REVISION_REQUIRED", "保存布局必须携带 If-Match 布局修订号");
		}
		if (positions == null || !positions.isObject()) {
			throw ApiException.badRequest("LAYOUT_INVALID", "布局内容必须为 { 对象id: {x, y} } 结构");
		}
		int newRevision = this.store.saveLayout(ifMatch, (ObjectNode) positions);
		ObjectNode data = JsonNodeFactory.instance.objectNode();
		data.put("layoutRevision", newRevision);
		return data;
	}

	// ---------------------------------------------------------------- 内部构造与校验

	/** 概要与属性共享对象草稿，整体验证并一次落盘，错误不产生部分对象更新。 */
	private void applyAttributes(ObjectNode doc, ObjectNode object, JsonNode body) {
		if (!body.has("attributes")) return;
		if (!body.path("attributes").isArray()) throw requiredMissing("attributes", object.path("id").asString());
		ArrayNode attributes = JsonNodeFactory.instance.arrayNode();
		Set<String> localIds = new LinkedHashSet<>();
		for (JsonNode attribute : body.path("attributes")) {
			String id = requireText(attribute, "id", "OBJ_REQUIRED_MISSING", object.path("id").asString());
			validateId(id);
			if (!localIds.add(id)) throw ApiException.badRequest("OBJ_ID_NOT_UNIQUE", "属性标识重复: " + id);
			for (JsonNode other : doc.path("objects")) {
				if (other != object && findAttribute((ObjectNode) other, id) != null) {
					throw ApiException.badRequest("OBJ_ID_NOT_UNIQUE", "属性标识重复: " + id);
				}
			}
			if (findObject(doc, id) != null || findRelation(doc, id) != null || findDomain(doc, id) != null) {
				throw ApiException.badRequest("OBJ_ID_NOT_UNIQUE", "属性标识与模型元素重复: " + id);
			}
			attributes.add(buildAttribute(doc, object, id, attribute, null));
		}
		object.set("attributes", attributes);
	}

	private ObjectNode buildAttribute(ObjectNode doc, ObjectNode object, String attrId, JsonNode body,
			ObjectNode existing) {
		ObjectNode attribute = existing == null ? JsonNodeFactory.instance.objectNode() : existing;
		attribute.put("id", attrId);

		String name = body.has("name") ? requireText(body, "name", "OBJ_REQUIRED_MISSING", attrId)
				: attribute.path("name").asString(null);
		if (name == null) {
			throw requiredMissing("name", attrId);
		}
		attribute.put("name", name);

		String dataType = body.has("dataType") ? optionalText(body, "dataType")
				: attribute.path("dataType").asString(null);
		if (dataType == null) {
			throw requiredMissing("dataType", attrId);
		}
		if (!DATA_TYPES.contains(dataType)) {
			throw ApiException.badRequest("OBJ_ATTR_COMBO_INVALID", "未知数据类型: " + dataType,
					List.of(new ErrorDetail("dataType", dataType, attrId)));
		}
		attribute.put("dataType", dataType);

		mergeScalar(attribute, body, "unit");
		mergeScalar(attribute, body, "nullMeaning");
		mergeScalar(attribute, body, "derivedFrom");
		mergeScalar(attribute, body, "readOnlyNature");
		mergeScalar(attribute, body, "valueSemantics");
		mergeScalar(attribute, body, "relationRef");
		if (body.has("defaultValue")) {
			attribute.set("defaultValue", body.path("defaultValue").deepCopy());
		}
		if (body.has("required")) {
			attribute.put("required", body.path("required").asBoolean(false));
		}
		else if (!attribute.has("required")) {
			attribute.put("required", false);
		}
		if (body.has("nullable")) {
			attribute.put("nullable", body.path("nullable").asBoolean(true));
		}
		else if (!attribute.has("nullable")) {
			attribute.put("nullable", true);
		}
		if (body.has("allowedValues") && body.path("allowedValues").isArray()) {
			attribute.set("allowedValues", body.path("allowedValues").deepCopy());
		}
		if (body.has("cardinality") && body.path("cardinality").isObject()) {
			attribute.set("cardinality", body.path("cardinality").deepCopy());
		}
		if (!attribute.has("cardinality")) {
			ObjectNode cardinality = attribute.putObject("cardinality");
			cardinality.put("min", 0);
			cardinality.put("max", 1);
		}
		if (body.has("referenceTarget") && body.path("referenceTarget").isArray()) {
			attribute.set("referenceTarget", body.path("referenceTarget").deepCopy());
		}
		if (body.has("termRefs") && body.path("termRefs").isArray()) {
			attribute.set("termRefs", body.path("termRefs").deepCopy());
		}
		else if (!attribute.has("termRefs")) {
			attribute.set("termRefs", JsonNodeFactory.instance.arrayNode());
		}

		// 组合相容校验（CHK-OBJ-004/005/006，MVP 保存级）
		if ("Enumeration".equals(dataType)
				&& (!attribute.path("allowedValues").isArray() || attribute.path("allowedValues").isEmpty())) {
			throw ApiException.badRequest("OBJ_ATTR_COMBO_INVALID", "枚举类型必须提供 allowedValues",
					List.of(new ErrorDetail("allowedValues", null, attrId)));
		}
		if ("Reference".equals(dataType)) {
			JsonNode targets = attribute.path("referenceTarget");
			if (!targets.isArray() || targets.isEmpty()) {
				throw ApiException.badRequest("OBJ_REFERENCE_TARGET_INVALID", "Reference 类型必须声明目标对象类型",
						List.of(new ErrorDetail("referenceTarget", null, attrId)));
			}
			for (JsonNode target : targets) {
				if (findObject(doc, target.asString()) == null) {
					throw ApiException.badRequest("OBJ_REFERENCE_TARGET_INVALID", "引用目标对象类型不存在: " + target.asString(),
							List.of(new ErrorDetail("referenceTarget", target.asString(), attrId)));
				}
			}
		}
		String relationRef = attribute.path("relationRef").asString(null);
		if (relationRef != null) {
			ObjectNode relation = findRelation(doc, relationRef);
			if (relation == null) {
				throw ApiException.badRequest("OBJ_RELATION_REF_MISMATCH", "relationRef 引用关系不存在: " + relationRef,
						List.of(new ErrorDetail("relationRef", relationRef, attrId)));
			}
			if (!object.path("id").asString().equals(relation.path("sourceType").asString())) {
				throw ApiException.badRequest("OBJ_RELATION_REF_MISMATCH", "relationRef 关系源端点与本对象不一致",
						List.of(new ErrorDetail("relationRef", relationRef, attrId)));
			}
		}
		if (attribute.path("nullable").asBoolean(false) && attribute.path("nullMeaning").asString(null) == null) {
			throw ApiException.badRequest("OBJ_ATTR_COMBO_INVALID", "nullable=true 时必须声明 nullMeaning",
					List.of(new ErrorDetail("nullMeaning", null, attrId)));
		}
		String nullMeaning = attribute.path("nullMeaning").asString(null);
		if (nullMeaning != null && !NULL_MEANINGS.contains(nullMeaning)) {
			throw ApiException.badRequest("OBJ_ATTR_COMBO_INVALID", "未知 nullMeaning: " + nullMeaning,
					List.of(new ErrorDetail("nullMeaning", nullMeaning, attrId)));
		}
		validateCardinality(attribute.path("cardinality"), attribute.path("required").asBoolean(false), attrId,
				"cardinality");
		return attribute;
	}

	private ObjectNode buildRelation(ObjectNode doc, String id, JsonNode body, ObjectNode existing) {
		ObjectNode relation = existing == null ? JsonNodeFactory.instance.objectNode() : existing;
		relation.put("id", id);

		String name = body.has("name") ? requireText(body, "name", "OBJ_REQUIRED_MISSING", id)
				: relation.path("name").asString(null);
		if (name == null) {
			throw requiredMissing("name", id);
		}
		relation.put("name", name);
		mergeScalar(relation, body, "definition");
		mergeScalar(relation, body, "inverse");
		mergeScalar(relation, body, "condition");
		mergeScalar(relation, body, "temporalValidity");

		String sourceType = body.has("sourceType") ? optionalText(body, "sourceType")
				: relation.path("sourceType").asString(null);
		if (sourceType == null) {
			throw requiredMissing("sourceType", id);
		}
		if (findObject(doc, sourceType) == null) {
			throw ApiException.badRequest("OBJ_ENDPOINT_MISSING", "关系源端点对象不存在: " + sourceType,
					List.of(new ErrorDetail("sourceType", sourceType, id)));
		}
		relation.put("sourceType", sourceType);

		JsonNode targetType = body.has("targetType") ? body.path("targetType") : relation.path("targetType");
		if (targetType == null || targetType.isMissingNode() || targetType.isNull()) {
			throw requiredMissing("targetType", id);
		}
		List<String> targets = new ArrayList<>();
		if (targetType.isArray()) {
			for (JsonNode t : targetType) {
				targets.add(t.asString());
			}
		}
		else {
			targets.add(targetType.asString());
		}
		if (targets.isEmpty()) throw requiredMissing("targetType", id);
		for (String target : targets) {
			if (findObject(doc, target) == null) {
				throw ApiException.badRequest("OBJ_ENDPOINT_MISSING", "关系目标端点对象不存在: " + target,
						List.of(new ErrorDetail("targetType", target, id)));
			}
		}
		relation.set("targetType", targetType.deepCopy());

		if (body.has("sourceCardinality") && body.path("sourceCardinality").isObject()) {
			relation.set("sourceCardinality", body.path("sourceCardinality").deepCopy());
		}
		if (body.has("targetCardinality") && body.path("targetCardinality").isObject()) {
			relation.set("targetCardinality", body.path("targetCardinality").deepCopy());
		}
		if (!relation.has("sourceCardinality")) {
			ObjectNode cardinality = relation.putObject("sourceCardinality");
			cardinality.put("min", 0);
			cardinality.put("max", "N");
		}
		if (!relation.has("targetCardinality")) {
			ObjectNode cardinality = relation.putObject("targetCardinality");
			cardinality.put("min", 0);
			cardinality.put("max", 1);
		}
		validateCardinality(relation.path("sourceCardinality"), false, id, "sourceCardinality");
		validateCardinality(relation.path("targetCardinality"), false, id, "targetCardinality");

		String compositionKind = body.has("compositionKind") ? optionalText(body, "compositionKind")
				: relation.path("compositionKind").asString("Association");
		if (!COMPOSITION_KINDS.contains(compositionKind)) {
			throw ApiException.badRequest("OBJ_CARDINALITY_INVALID", "未知组合语义: " + compositionKind,
					List.of(new ErrorDetail("compositionKind", compositionKind, id)));
		}
		relation.put("compositionKind", compositionKind);
		if (body.has("wholePart")) {
			relation.set("wholePart", body.path("wholePart").deepCopy());
		}
		if (("Aggregation".equals(compositionKind) || "Composition".equals(compositionKind))
				&& (!relation.path("wholePart").isObject())) {
			throw ApiException.badRequest("OBJ_CARDINALITY_INVALID", "聚合/组合关系必须提供 wholePart 声明",
					List.of(new ErrorDetail("wholePart", null, id)));
		}

		if (body.has("direction")) {
			mergeScalar(relation, body, "direction");
		}
		else if (!relation.has("direction")) {
			relation.put("direction", sourceType + "→" + targets.get(0));
		}
		String domainId = body.has("domainId") ? optionalText(body, "domainId")
				: relation.path("domainId").asString(null);
		if (domainId == null) {
			ObjectNode sourceObject = findObject(doc, sourceType);
			domainId = sourceObject.path("domainId").asString();
		}
		assertDomainExists(doc, domainId);
		relation.put("domainId", domainId);

		// 跨域显式声明（CHK-OBJ-016 简化：源/目标领域不同须声明）
		String sourceDomain = domainOfObject(doc, sourceType);
		boolean crossDomain = targets.stream().anyMatch(t -> {
			String targetDomain = domainOfObject(doc, t);
			return targetDomain != null && !targetDomain.equals(sourceDomain);
		});
		if (body.has("crossDomainDeclared")) {
			relation.put("crossDomainDeclared", body.path("crossDomainDeclared").asBoolean(false));
		}
		if (crossDomain && !relation.path("crossDomainDeclared").asBoolean(false)) {
			throw ApiException.badRequest("OBJ_CROSS_DOMAIN_DECL_REQUIRED", "跨领域关系须显式声明 crossDomainDeclared",
					List.of(new ErrorDetail("crossDomainDeclared", null, id)));
		}

		if (body.has("annotation") && body.path("annotation").isObject()) {
			relation.set("annotation", body.path("annotation").deepCopy());
		}
		else if (!relation.has("annotation")) {
			ObjectNode annotation = relation.putObject("annotation");
			annotation.put("label", name + " " + cardinalityText(relation.path("sourceCardinality")) + " → "
					+ cardinalityText(relation.path("targetCardinality")));
			annotation.put("showLabel", true);
		}
		if (body.has("termRefs") && body.path("termRefs").isArray()) {
			relation.set("termRefs", body.path("termRefs").deepCopy());
		}
		else if (!relation.has("termRefs")) {
			relation.set("termRefs", JsonNodeFactory.instance.arrayNode());
		}

		if (existing == null) {
			relation.put("status", "active");
			relation.put("revision", 1);
			relation.put("changeReason",
					optionalText(body, "changeReason") == null ? "初始创建" : optionalText(body, "changeReason"));
			relation.put("owner", "建模人员");
			relation.set("source", JsonNodeFactory.instance.arrayNode());
			stampCreated(relation);
		}
		return relation;
	}

	private void validateIdentitySpec(ObjectNode object, JsonNode identitySpec, String elementId) {
		Set<String> attrIds = new LinkedHashSet<>();
		for (JsonNode attr : object.path("attributes")) {
			attrIds.add(attr.path("id").asString());
		}
		JsonNode attributes = identitySpec.path("attributes");
		if (attributes.isArray()) {
			for (JsonNode ref : attributes) {
				if (!attrIds.contains(ref.asString())) {
					throw ApiException.badRequest("OBJ_IDENTITY_REF_MISSING", "业务标识引用的属性不存在: " + ref.asString(),
							List.of(new ErrorDetail("identitySpec.attributes", ref.asString(), elementId)));
				}
			}
			if ("SINGLE".equals(identitySpec.path("mode").asString("SINGLE")) && attributes.size() > 1) {
				throw ApiException.badRequest("OBJ_IDENTITY_REF_MISSING", "SINGLE 模式业务标识只能引用一个属性",
						List.of(new ErrorDetail("identitySpec.mode", "SINGLE", elementId)));
			}
		}
	}

	private void validateCardinality(JsonNode cardinality, boolean required, String elementId, String field) {
		if (!cardinality.isObject()) {
			throw ApiException.badRequest("OBJ_CARDINALITY_INVALID", "基数声明缺失",
					List.of(new ErrorDetail(field, null, elementId)));
		}
		int min = cardinality.path("min").asInt(-1);
		JsonNode maxNode = cardinality.path("max");
		boolean maxUnbounded = maxNode.isString() && "N".equalsIgnoreCase(maxNode.asString());
		int max = maxUnbounded ? Integer.MAX_VALUE : maxNode.asInt(-1);
		if (min < 0 || max < 0 || min > max) {
			throw ApiException.badRequest("OBJ_CARDINALITY_INVALID", "基数区间非法（须 0 ≤ min ≤ max）",
					List.of(new ErrorDetail(field, cardinality.toString(), elementId)));
		}
		if (required && min < 1) {
			throw ApiException.badRequest("OBJ_CARDINALITY_INVALID", "required=true 时基数下限须 ≥ 1",
					List.of(new ErrorDetail(field, cardinality.toString(), elementId)));
		}
	}

	// ---------------------------------------------------------------- 工具方法

	private void assertGloballyUnique(ObjectNode doc, String id, String selfId) {
		validateId(id);
		if (id.equals(selfId)) {
			return;
		}
		if (findObject(doc, id) != null || findRelation(doc, id) != null || findDomain(doc, id) != null) {
			throw ApiException.badRequest("OBJ_ID_NOT_UNIQUE", "稳定标识与现有元素重复: " + id,
					List.of(new ErrorDetail("id", id, id)));
		}
		for (JsonNode object : doc.path("objects")) {
			if (findAttribute((ObjectNode) object, id) != null) throw ApiException.badRequest("OBJ_ID_NOT_UNIQUE", "稳定标识与属性重复: " + id);
		}
	}

	private static void validateId(String id) {
		if (!id.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")) {
			throw ApiException.badRequest("OBJ_ID_INVALID", "标识需使用字母、数字、下划线、点或短横线（最多128字符）",
				List.of(new ErrorDetail("id", id, id)));
		}
	}

	private void assertAttributeIdUnique(ObjectNode doc, String attrId) {
		for (JsonNode object : doc.path("objects")) {
			if (findAttribute((ObjectNode) object, attrId) != null) {
				throw ApiException.badRequest("OBJ_ID_NOT_UNIQUE", "属性标识与现有属性重复: " + attrId,
						List.of(new ErrorDetail("id", attrId, attrId)));
			}
		}
	}

	private void rewriteObjectIdReferences(ObjectNode doc, String oldId, String newId) {
		for (JsonNode relation : doc.path("relations")) {
			ObjectNode rel = (ObjectNode) relation;
			if (oldId.equals(rel.path("sourceType").asString())) {
				rel.put("sourceType", newId);
			}
			JsonNode targetType = rel.path("targetType");
			if (targetType.isArray()) {
				ArrayNode updated = JsonNodeFactory.instance.arrayNode();
				for (JsonNode t : targetType) {
					updated.add(oldId.equals(t.asString()) ? newId : t.asString());
				}
				rel.set("targetType", updated);
			}
			else if (oldId.equals(targetType.asString())) {
				rel.put("targetType", newId);
			}
		}
		for (JsonNode object : doc.path("objects")) {
			for (JsonNode attr : object.path("attributes")) {
				JsonNode targets = attr.path("referenceTarget");
				if (targets.isArray()) {
					ArrayNode updated = JsonNodeFactory.instance.arrayNode();
					for (JsonNode t : targets) {
						updated.add(oldId.equals(t.asString()) ? newId : t.asString());
					}
					((ObjectNode) attr).set("referenceTarget", updated);
				}
			}
		}
	}

	private void assertRevision(ObjectNode element, Integer ifMatch, String elementId) {
		if (ifMatch == null) {
			throw ApiException.badRequest("REVISION_REQUIRED", "更新请求必须携带 If-Match 修订号");
		}
		long current = element.path("revision").asLong(1);
		if (current != ifMatch.longValue()) {
			throw ApiException.revisionConflict(elementId, ifMatch, current);
		}
	}

	private void bumpRevision(ObjectNode element, JsonNode body) {
		element.put("revision", element.path("revision").asInt(1) + 1);
		element.put("updatedBy", ACTOR);
		element.put("updatedAt", Instant.now().toString());
		String changeReason = body == null ? null : optionalText(body, "changeReason");
		element.put("changeReason", changeReason == null ? "定义更新" : changeReason);
	}

	private void stampCreated(ObjectNode element) {
		String now = Instant.now().toString();
		element.put("createdBy", ACTOR);
		element.put("createdAt", now);
		element.put("updatedBy", ACTOR);
		element.put("updatedAt", now);
	}

	private void appendRevision(String elementId, String changeType, String changeReason, ObjectNode snapshot) {
		ObjectNode record = JsonNodeFactory.instance.objectNode();
		record.put("revision", snapshot.path("revision").asInt(1));
		record.put("changeType", changeType);
		record.put("changeReason", changeReason == null ? "" : changeReason);
		record.put("changedBy", ACTOR);
		record.put("changedAt", Instant.now().toString());
		record.set("snapshot", snapshot.deepCopy());
		this.store.appendRevision(elementId, record);
	}

	private void assertDomainExists(ObjectNode doc, String domainId) {
		if (findDomain(doc, domainId) == null) {
			throw ApiException.badRequest("DOMAIN_NOT_FOUND", "归属领域不存在: " + domainId,
					List.of(new ErrorDetail("domainId", domainId, null)));
		}
	}

	private ObjectNode requireObject(ObjectNode doc, String objectId) {
		ObjectNode object = findObject(doc, objectId);
		if (object == null) {
			throw ApiException.notFound("OBJECT_TYPE_NOT_FOUND", "对象类型不存在: " + objectId);
		}
		return object;
	}

	private static ObjectNode findObject(ObjectNode doc, String id) {
		return findById(doc.path("objects"), "id", id);
	}

	private static ObjectNode findRelation(ObjectNode doc, String id) {
		return findById(doc.path("relations"), "id", id);
	}

	private static ObjectNode findDomain(ObjectNode doc, String id) {
		return findById(doc.path("domains"), "domainId", id);
	}

	private static ObjectNode findAttribute(ObjectNode object, String attrId) {
		return findById(object.path("attributes"), "id", attrId);
	}

	private static ObjectNode findById(JsonNode array, String idField, String id) {
		if (!array.isArray()) {
			return null;
		}
		for (JsonNode node : array) {
			if (id.equals(node.path(idField).asString(null))) {
				return (ObjectNode) node;
			}
		}
		return null;
	}

	private static boolean endpointsInclude(JsonNode relation, String objectId) {
		if (objectId.equals(relation.path("sourceType").asString())) {
			return true;
		}
		JsonNode targetType = relation.path("targetType");
		if (targetType.isArray()) {
			for (JsonNode t : targetType) {
				if (objectId.equals(t.asString())) {
					return true;
				}
			}
			return false;
		}
		return objectId.equals(targetType.asString());
	}

	private static String domainOfObject(ObjectNode doc, String objectId) {
		ObjectNode object = findObject(doc, objectId);
		return object == null ? null : object.path("domainId").asString(null);
	}

	private static String firstTarget(JsonNode relation) {
		JsonNode targetType = relation.path("targetType");
		if (targetType.isArray() && !targetType.isEmpty()) {
			return targetType.get(0).asString();
		}
		return targetType.asString(null);
	}

	private static int countObjectsOfDomain(ObjectNode doc, String domainId) {
		int count = 0;
		for (JsonNode object : doc.path("objects")) {
			if (domainId.equals(object.path("domainId").asString())) {
				count++;
			}
		}
		return count;
	}

	private ObjectNode toGraphObject(ObjectNode object) {
		ObjectNode item = JsonNodeFactory.instance.objectNode();
		item.put("id", object.path("id").asString());
		item.put("name", object.path("name").asString());
		item.put("domainId", object.path("domainId").asString());
		item.put("attributeCount", object.path("attributes").size());
		ArrayNode identityNames = item.putArray("identityAttrNames");
		Set<String> attrIds = new LinkedHashSet<>();
		for (JsonNode ref : object.path("identitySpec").path("attributes")) {
			attrIds.add(ref.asString());
		}
		for (JsonNode attr : object.path("attributes")) {
			if (attrIds.contains(attr.path("id").asString())) {
				identityNames.add(attr.path("name").asString());
			}
		}
		item.put("completeness", object.path("completeness").asString("draft"));
		item.put("status", object.path("status").asString("active"));
		item.put("revision", object.path("revision").asInt(1));
		ObjectNode layout = item.putObject("layout");
		layout.put("x", object.path("graph").path("x").asInt(100));
		layout.put("y", object.path("graph").path("y").asInt(100));
		return item;
	}

	private ObjectNode toGraphRelation(ObjectNode relation) {
		ObjectNode item = JsonNodeFactory.instance.objectNode();
		item.put("id", relation.path("id").asString());
		item.put("name", relation.path("name").asString());
		item.put("sourceType", relation.path("sourceType").asString());
		item.set("targetType", relation.path("targetType").deepCopy());
		item.set("sourceCardinality", relation.path("sourceCardinality").deepCopy());
		item.set("targetCardinality", relation.path("targetCardinality").deepCopy());
		item.put("compositionKind", relation.path("compositionKind").asString("Association"));
		if (relation.path("inverse").isString()) {
			item.put("inverse", relation.path("inverse").asString());
		}
		if (relation.path("annotation").isObject()) {
			item.set("annotation", relation.path("annotation").deepCopy());
		}
		item.put("status", relation.path("status").asString("active"));
		item.put("revision", relation.path("revision").asInt(1));
		return item;
	}

	private static String cardinalityText(JsonNode cardinality) {
		String max = cardinality.path("max").isString() ? cardinality.path("max").asString()
				: String.valueOf(cardinality.path("max").asInt());
		return cardinality.path("min").asInt() + ".." + max;
	}

	private static void mergeScalar(ObjectNode target, JsonNode body, String field) {
		if (body.has(field)) {
			target.set(field, body.path(field).deepCopy());
		}
	}

	private static void putIfText(ObjectNode target, String field, JsonNode body) {
		String value = optionalText(body, field);
		if (value == null) {
			target.putNull(field);
		}
		else {
			target.put(field, value);
		}
	}

	private static String optionalText(JsonNode body, String field) {
		JsonNode node = body.path(field);
		if (!node.isString()) {
			return null;
		}
		String value = node.asString().trim();
		return value.isEmpty() ? null : value;
	}

	private static String requireText(JsonNode body, String field, String code, String elementId) {
		String value = optionalText(body, field);
		if (value == null) {
			throw ApiException.badRequest(code, "必填字段缺失: " + field,
					List.of(new ErrorDetail(field, null, elementId)));
		}
		return value;
	}

	private static ApiException requiredMissing(String field, String elementId) {
		return ApiException.badRequest("OBJ_REQUIRED_MISSING", "必填字段缺失: " + field,
				List.of(new ErrorDetail(field, null, elementId)));
	}

	private static String generateId(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
	}

}
