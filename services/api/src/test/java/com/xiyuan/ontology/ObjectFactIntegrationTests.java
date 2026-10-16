package com.xiyuan.ontology;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.xiyuan.ontology.modeling.ModelingReadFacade;
import com.xiyuan.ontology.modeling.codec.ModelJsonCodec;
import com.xiyuan.ontology.modeling.model.LayoutState;
import com.xiyuan.ontology.modeling.model.ModelDefinition.Kind;
import com.xiyuan.ontology.modeling.storage.ModelStorage;
import com.xiyuan.ontology.objectfact.storage.FactStorage;
import com.xiyuan.ontology.objectfact.service.FactQueries;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties = { "logging.level.root=WARN", "debug=false" })
@AutoConfigureMockMvc
class ObjectFactIntegrationTests {
	private static final Path WORKSPACE = Path.of("target/test-workspaces/objectfact-" + UUID.randomUUID()).toAbsolutePath();
	@DynamicPropertySource static void workspace(DynamicPropertyRegistry registry) { registry.add("xiyuan.workspace.dir", WORKSPACE::toString); }
	@Autowired MockMvc http;
	@Autowired ModelStorage models;
	@Autowired FactStorage facts;
	@Autowired FactQueries queries;
	@Autowired ModelingReadFacade facade;
	private final JsonMapper mapper = JsonMapper.builder().build();
	private final ModelJsonCodec codec = new ModelJsonCodec();

	@BeforeEach void reset() throws Exception {
		ObjectNode fixture = resource("model.json");
		models.write("ENV-OM-001", session -> {
			session.relations().findAll().forEach(value -> session.relations().delete(value.elementKey()));
			session.objects().findAll().forEach(value -> session.objects().delete(value.elementKey()));
			session.domains().findAll().forEach(value -> session.domains().delete(value.elementKey()));
			session.layout().save(new LayoutState(session.layout().get().revision() + 1, Map.of(), Map.of()), session.layout().get().revision());
			fixture.path("domains").forEach(value -> session.domains().save(codec.definition(UUID.randomUUID(), Kind.DOMAIN, (ObjectNode) value)));
			fixture.path("objects").forEach(value -> session.objects().save(codec.definition(UUID.randomUUID(), Kind.OBJECT, (ObjectNode) value)));
			fixture.path("relations").forEach(value -> session.relations().save(codec.definition(UUID.randomUUID(), Kind.RELATION, (ObjectNode) value)));
			return null;
		});
		facts.write(null, state -> { state.putArray("instances"); state.putArray("relationInstances"); state.putArray("records"); state.remove("lastCheck"); return null; });
	}

	@Test void cases001_008_009_015_021_022_023_026_027_importDetailReferencesAndLedger() throws Exception {
		JsonNode result = imported(); assertEquals(11, result.path("instanceCount").asInt()); assertEquals(7, result.path("relationCount").asInt());
		JsonNode service = data(get("/api/v1/facts/instances/" + id("svc1")), 200);
		assertEquals("订单服务", service.path("propertyValues").path("name").asString()); assertEquals(3, service.path("inbound").size());
		JsonNode alert = data(get("/api/v1/facts/instances/" + id("alt1")), 200);
		assertEquals("UNKNOWN", alert.path("propertyValues").path("maintenanceRelated").path("nullReason").asString());
		assertEquals(id("svc1"), alert.path("referenceValues").path("service").get(0).path("instanceId").asString());
		assertFalse(alert.has("binding"));
		JsonNode workorder = data(get("/api/v1/facts/instances/" + id("wo1")), 200); assertEquals("NOT_PROVIDED", workorder.path("propertyValues").path("assignee").path("nullReason").asString());
		JsonNode references = data(get("/api/v1/facts/instances/" + id("svc1") + "/references"), 200); assertEquals(1, references.path("outbound").size());
		JsonNode graph = data(get("/api/v1/facts/graph"), 200); assertEquals(11, graph.path("nodes").size()); assertEquals(7, graph.path("edges").size());
		JsonNode ledger = data(get("/api/v1/facts/batches"), 200).path("items").get(0); assertEquals("IMPORT", ledger.path("recordKind").asString()); assertEquals("fixture-modeler", ledger.path("operatedBy").asString());
		assertEquals("FIX-OF-001/topo-base", ledger.path("sourceRef").asString()); assertTrue(ledger.has("importedAt"));
	}

	@Test void cases002_003_004_005_006_007_010_011_012_013_014_collectAllErrorsWithoutPartialWrites() throws Exception {
		ObjectNode body = resource("topo-base.json"); body.remove("relations"); data(write(post("/api/v1/facts/import"), body), 400); assertEquals(0, count());
		body = resource("topo-base.json"); ((ObjectNode) body.path("instances").get(0)).put("objectTypeRef", "UNKNOWN");
		((ObjectNode) body.path("instances").get(1).path("propertyValues")).put("criticality", "Extreme");
		((ObjectNode) body.path("instances").get(2).path("propertyValues")).remove("name");
		((ObjectNode) body.path("relations").get(0)).put("relationTypeRef", "UNKNOWN");
		((ObjectNode) body.path("relations").get(1)).put("targetKey", "missing");
		JsonNode result = data(write(post("/api/v1/facts/import"), body), 200); assertEquals("REJECTED", result.path("result").asString());
		for (String code : List.of("TYPE_NOT_FOUND", "ENUM_OUT_OF_RANGE", "PROPERTY_REQUIRED_MISSING", "RELATION_NOT_FOUND", "REFERENCE_DANGLING", "CARDINALITY_OUT_OF_RANGE")) assertTrue(result.path("errors").toString().contains(code), code);
		assertEquals(0, count());
		ObjectNode duplicate = resource("topo-base.json"); ((ObjectNode) duplicate.path("instances").get(1).path("propertyValues")).put("serviceCode", "SVC-001");
		JsonNode conflict = data(write(post("/api/v1/facts/import"), duplicate), 200); assertTrue(conflict.path("errors").toString().contains("IDENTITY_DUPLICATE")); assertEquals(2, conflict.path("errors").get(0).path("conflicts").size());
		imported(); result = data(write(post("/api/v1/facts/import"), resource("topo-base.json")), 200); assertEquals("REJECTED", result.path("result").asString()); assertEquals(11, count());
	}

	@Test void cases012_013_relationUpperAndLowerBounds() throws Exception {
		ObjectNode body = resource("topo-base.json"); ((ArrayNode) body.path("relations")).addObject().put("relationTypeRef", "REL-OF-ALERT-SERVICE").put("sourceKey", "alt1").put("targetKey", "svc2");
		assertTrue(data(write(post("/api/v1/facts/import"), body), 200).path("errors").toString().contains("CARDINALITY_OUT_OF_RANGE"));
		body = resource("topo-base.json"); ((ArrayNode) body.path("relations")).remove(2); assertTrue(data(write(post("/api/v1/facts/import"), body), 200).path("errors").toString().contains("CARDINALITY_OUT_OF_RANGE")); assertEquals(0, count());
	}

	@Test void cases016_purgeRequiresConfirmationRetainsLedgerAndNeverReusesIds() throws Exception {
		imported(); String first = id("svc1"); long modelVersion = facade.snapshot().path("version").asLong();
		data(write(delete("/api/v1/facts/data"), json("{\"confirm\":false}")), 400); assertEquals(11, count());
		data(write(delete("/api/v1/facts/data"), json("{\"confirm\":true}")), 200); assertEquals(0, count());
		assertEquals(modelVersion, facade.snapshot().path("version").asLong()); assertEquals(2, data(get("/api/v1/facts/batches"), 200).path("total").asInt());
		imported(); assertNotEquals(first, id("svc1"));
	}

	@Test void cases017_018_modelChangesMarkAffectedInstancesAndRestorationClearsMarks() throws Exception {
		imported(); String alertId = id("alt1"), incidentId = id("inc1"), workorderId = id("wo1");
		changeObject("OBJ-OF-ALERT", object -> { ArrayNode attributes = (ArrayNode) object.path("attributes"); for (int i = 0; i < attributes.size(); i++) if ("feature".equals(attributes.get(i).path("name").asString())) { attributes.remove(i); break; } });
		JsonNode check = data(write(post("/api/v1/facts/check"), json("{}")), 200); assertTrue(check.path("pendingReview").toString().contains(alertId));
		assertEquals(3, data(get("/api/v1/facts/instances").param("mark", "pendingReview"), 200).path("total").asInt());
		changeObject("OBJ-OF-WORKORDER", object -> object.put("status", "deprecated"));
		check = data(write(post("/api/v1/facts/check"), json("{}")), 200); assertTrue(check.path("invalidRef").toString().contains(workorderId)); assertTrue(check.path("invalidRef").toString().contains(incidentId));
		JsonNode model = resource("model.json"); for (JsonNode object : model.path("objects")) if ("OBJ-OF-ALERT".equals(object.path("id").asString())) changeObject("OBJ-OF-ALERT", existing -> existing.set("attributes", object.path("attributes")));
		changeObject("OBJ-OF-WORKORDER", object -> object.put("status", "active"));
		check = data(write(post("/api/v1/facts/check"), json("{}")), 200); assertTrue(check.path("pendingReview").isEmpty()); assertTrue(check.path("invalidRef").isEmpty());
	}

	@ParameterizedTest @ValueSource(strings = { "/import", "/instances", "/check", "/data" })
	void cases019_020_034_viewersCannotWrite(String path) throws Exception {
		MockHttpServletRequestBuilder request = "/data".equals(path) ? delete("/api/v1/facts" + path) : post("/api/v1/facts" + path);
		data(request.header("X-Xiyuan-Role", "VIEWER").contentType(APPLICATION_JSON).content("{}"), 403); assertEquals(0, count());
	}

	@Test void cases024_025_searchDomainsFocusAndTruncation() throws Exception {
		imported(); JsonNode result = data(get("/api/v1/facts/instances").param("q", "SVC-001"), 200); assertEquals(1, result.path("total").asInt());
		assertEquals(3, data(get("/api/v1/facts/instances").param("type", "OBJ-OF-ALERT"), 200).path("total").asInt());
		assertEquals(11, data(get("/api/v1/facts/instances").param("domain", "DOMAIN-OF-OPS"), 200).path("total").asInt());
		assertEquals(0, data(get("/api/v1/facts/instances").param("q", "no-match"), 200).path("total").asInt());
		assertEquals(3, data(get("/api/v1/facts/graph").param("types", "OBJ-OF-SERVICE"), 200).path("nodes").size());
		assertEquals(4, data(get("/api/v1/facts/graph").param("focus", id("svc1")).param("hops", "1"), 200).path("nodes").size());
		assertTrue(data(get("/api/v1/facts/graph").param("limit", "2"), 200).path("truncated").asBoolean());
		data(get("/api/v1/facts/graph").param("limit", "501"), 400);
	}

	@Test void graphSearchAndMarksUseTheSameFiltersAsInstanceQueries() throws Exception {
		imported();
		JsonNode graph = data(get("/api/v1/facts/graph").param("q", "svc-001"), 200);
		assertEquals(1, graph.path("total").asInt()); assertEquals(id("svc1"), graph.path("nodes").get(0).path("instanceId").asString());
		assertTrue(graph.path("edges").isEmpty());
		assertEquals(0, data(get("/api/v1/facts/graph").param("q", "SVC-001").param("types", "OBJ-OF-ALERT"), 200).path("total").asInt());
		changeObject("OBJ-OF-ALERT", object -> {
			ArrayNode attributes = (ArrayNode) object.path("attributes");
			for (int i = 0; i < attributes.size(); i++) if ("feature".equals(attributes.get(i).path("name").asString())) { attributes.remove(i); break; }
		});
		data(write(post("/api/v1/facts/check"), json("{}")), 200);
		assertEquals(3, data(get("/api/v1/facts/graph").param("mark", "pendingReview"), 200).path("total").asInt());
		graph = data(get("/api/v1/facts/graph").param("mark", "pendingReview").param("q", "ALT-001"), 200);
		assertEquals(1, graph.path("nodes").size()); assertEquals(id("alt1"), graph.path("nodes").get(0).path("instanceId").asString());
		assertEquals(0, data(get("/api/v1/facts/graph").param("mark", "invalidRef"), 200).path("total").asInt());
	}

	@Test void cases028_029_030_editReadOnlyEnumAtomicityAndStaleRevision() throws Exception {
		imported(); String service = id("svc2"); long stale = revision();
		data(write(patch("/api/v1/facts/instances/" + service), json("{\"propertyValues\":{\"criticality\":\"Low\"}}")), 200);
		assertEquals("Low", data(get("/api/v1/facts/instances/" + service), 200).path("propertyValues").path("criticality").asString());
		JsonNode failure = data(write(patch("/api/v1/facts/instances/" + service), json("{\"propertyValues\":{\"serviceCode\":\"CHANGED\",\"criticality\":\"Extreme\"}}")), 422);
		assertTrue(failure.path("details").toString().contains("EDIT_READ_ONLY")); assertTrue(failure.path("details").toString().contains("ENUM_OUT_OF_RANGE"));
		assertEquals("SVC-002", data(get("/api/v1/facts/instances/" + service), 200).path("propertyValues").path("serviceCode").asString());
		data(patch("/api/v1/facts/instances/" + service).header("X-Xiyuan-Role", "MODELER").header("If-Match", stale).contentType(APPLICATION_JSON).content("{}"), 409);
		data(patch("/api/v1/facts/instances/" + service).header("X-Xiyuan-Role", "VIEWER").contentType(APPLICATION_JSON).content("{}"), 403);
		data(delete("/api/v1/facts/instances/" + service).header("X-Xiyuan-Role", "VIEWER"), 403);
	}

	@Test void cases031_032_037_relationEditCanvasPathAndNetCardinality() throws Exception {
		imported(); String alert = id("alt2"), service1 = id("svc1"), service2 = id("svc2");
		data(write(patch("/api/v1/facts/instances/" + alert), addRelation("REL-OF-ALERT-SERVICE", service1)), 200);
		JsonNode detail = data(get("/api/v1/facts/instances/" + alert), 200); assertEquals(service1, detail.path("referenceValues").path("service").get(0).path("instanceId").asString());
		data(write(patch("/api/v1/facts/instances/" + alert), addRelation("REL-OF-ALERT-SERVICE", service2)), 422);
		String relationId = ""; for (JsonNode edge : detail.path("outbound")) if ("REL-OF-ALERT-SERVICE".equals(edge.path("relationTypeRef").asString())) relationId = edge.path("relationId").asString();
		ObjectNode swap = addRelation("REL-OF-ALERT-SERVICE", service2); ((ArrayNode) swap.path("relationOps")).addObject().put("op", "remove").put("relationId", relationId);
		data(write(patch("/api/v1/facts/instances/" + alert), swap), 200);
		assertEquals(service2, data(get("/api/v1/facts/instances/" + alert), 200).path("referenceValues").path("service").get(0).path("instanceId").asString());
	}

	@Test void cases033_deleteProtectedAndExplicitUnlinkDeleteValidatesRemainingTopology() throws Exception {
		imported(); String workorder = id("wo1"); data(write(delete("/api/v1/facts/instances/" + workorder), null), 409);
		JsonNode refs = data(get("/api/v1/facts/instances/" + workorder + "/references"), 200); String relation = refs.path("inbound").get(0).path("relationId").asString();
		ObjectNode body = json("{\"deleteInstance\":true,\"relationOps\":[]}"); body.withArray("relationOps").addObject().put("op", "remove").put("relationId", relation);
		data(write(patch("/api/v1/facts/instances/" + workorder), body), 200); assertEquals(10, count()); data(get("/api/v1/facts/instances/" + workorder), 404);
		assertEquals("INSTANCE_DELETE", data(get("/api/v1/facts/batches"), 200).path("items").get(0).path("recordKind").asString());
	}

	@Test void cases035_036_manualCreateDuplicateRejectAndCanLinkImmediately() throws Exception {
		imported(); ObjectNode create = json("{\"objectTypeRef\":\"OBJ-OF-SERVICE\",\"propertyValues\":{\"serviceCode\":\"SVC-004\",\"name\":\"新服务\",\"criticality\":\"Medium\"}}");
		JsonNode created = data(write(post("/api/v1/facts/instances"), create), 200); String id = created.path("instanceId").asString(); assertEquals("MANUAL", created.path("sourceRef").asString()); assertFalse(created.has("batchId"));
		assertEquals(12, count()); data(write(post("/api/v1/facts/instances"), create), 422); assertEquals(12, count());
		data(write(patch("/api/v1/facts/instances/" + id), json("{\"propertyValues\":{\"criticality\":\"Low\"}}")), 200);
		data(write(patch("/api/v1/facts/instances/" + id("alt2")), addRelation("REL-OF-ALERT-SERVICE", id)), 200);
		assertEquals(8, data(get("/api/v1/facts/graph"), 200).path("edges").size());
	}

	@Test void singlePointCreationCanBuildRequiredRelationsThenCheckBecomesClean() throws Exception {
		imported(); ObjectNode create = json("{\"objectTypeRef\":\"OBJ-OF-ALERT\",\"propertyValues\":{\"sourceAlertId\":\"ALT-NEW\",\"severity\":\"Info\",\"occurredAt\":\"2026-10-15T10:00:00Z\",\"feature\":\"新告警\"}}");
		String id = data(write(post("/api/v1/facts/instances"), create), 200).path("instanceId").asString();
		assertTrue(data(write(post("/api/v1/facts/check"), json("{}")), 200).path("pendingReview").toString().contains(id));
		data(write(patch("/api/v1/facts/instances/" + id), addRelation("REL-OF-ALERT-POINTSTO", id("svc1"))), 200);
		assertFalse(data(write(post("/api/v1/facts/check"), json("{}")), 200).path("pendingReview").toString().contains(id));
	}

	@Test void removingAnObsoletePropertyIsExplicitAuditedAndClearsItsReviewMark() throws Exception {
		imported(); String alert = id("alt1");
		changeObject("OBJ-OF-ALERT", type -> { ArrayNode attributes = (ArrayNode) type.path("attributes"); for (int index = 0; index < attributes.size(); index++) if ("feature".equals(attributes.get(index).path("name").asString())) { attributes.remove(index); break; } });
		data(write(post("/api/v1/facts/check"), json("{}")), 200);
		data(write(patch("/api/v1/facts/instances/" + alert), json("{\"unsetProperties\":[\"feature\"]}")), 200);
		assertFalse(data(get("/api/v1/facts/instances/" + alert), 200).path("propertyValues").has("feature"));
		data(write(post("/api/v1/facts/check"), json("{}")), 200);
		assertTrue(data(get("/api/v1/facts/instances/" + alert), 200).path("marks").isEmpty());
		data(write(patch("/api/v1/facts/instances/" + id("svc1")), json("{\"unsetProperties\":[\"serviceCode\"]}")), 422);
	}

	@Test void nullMeaningTypeDuplicateRelationAndReferenceDualWriteAreValidated() throws Exception {
		ObjectNode body = resource("topo-base.json"); ObjectNode props = (ObjectNode) body.path("instances").get(6).path("propertyValues");
		props.set("maintenanceRelated", json("{\"value\":null,\"nullReason\":\"NOT_PROVIDED\"}")); props.put("service", "SVC-001"); props.put("occurredAt", "invalid-date");
		JsonNode result = data(write(post("/api/v1/facts/import"), body), 200); assertTrue(result.path("errors").toString().contains("NULL_MEANING_MISMATCH")); assertTrue(result.path("errors").toString().contains("PROPERTY_TYPE_MISMATCH")); assertTrue(result.path("errors").toString().contains("REFERENCE_PROPERTY_DUAL_WRITE"));
		imported(); data(write(patch("/api/v1/facts/instances/" + id("alt1")), addRelation("REL-OF-ALERT-POINTSTO", id("svc1"))), 422);
	}

	@Test void emptyImportIsAuditedAndReadQueriesRejectInvalidPagination() throws Exception {
		JsonNode result = data(write(post("/api/v1/facts/import"), json("{\"schemaVersion\":\"1.0\",\"kind\":\"instance-topology\",\"sourceRef\":\"empty\",\"instances\":[],\"relations\":[]}")), 200); assertEquals("ACCEPTED", result.path("result").asString());
		assertEquals(1, data(get("/api/v1/facts/batches"), 200).path("total").asInt()); data(get("/api/v1/facts/instances").param("page", "0"), 400);
	}

	@Test void acNfr001_searchP95At5000InstancesAnd10000Relations() throws Exception {
		models.write("ENV-OM-001", session -> {
			ObjectNode relation = json("{\"id\":\"REL-OF-SERVICE-LOAD\",\"name\":\"load link\",\"sourceType\":\"OBJ-OF-SERVICE\",\"targetType\":\"OBJ-OF-SERVICE\",\"sourceCardinality\":{\"min\":0,\"max\":\"N\"},\"targetCardinality\":{\"min\":0,\"max\":\"N\"},\"domainId\":\"DOMAIN-OF-OPS\",\"status\":\"active\"}");
			session.relations().save(codec.definition(UUID.randomUUID(), Kind.RELATION, relation)); return null;
		});
		facts.write(null, state -> {
			long startInstance = state.path("meta").path("idSequence").path("instance").asLong();
			long startRelation = state.path("meta").path("idSequence").path("relation").asLong();
			for (int index = 0; index < 5000; index++) {
				ObjectNode instance = state.withArray("instances").addObject();
				instance.put("instanceId", "INST-" + String.format("%06d", startInstance + index + 1)); instance.put("objectTypeRef", "OBJ-OF-SERVICE"); instance.put("sourceRef", "PERFORMANCE-FIXTURE");
				instance.putObject("businessIdentity").put("serviceCode", "LOAD-" + index);
				instance.putObject("propertyValues").put("serviceCode", "LOAD-" + index).put("name", "Load service " + index).put("criticality", "Low"); instance.putArray("marks");
			}
			for (int index = 0; index < 10000; index++) {
				ObjectNode edge = state.withArray("relationInstances").addObject();
				edge.put("relationId", "RELI-" + String.format("%06d", startRelation + index + 1)); edge.put("relationTypeRef", "REL-OF-SERVICE-LOAD");
				edge.put("sourceInstanceId", "INST-" + String.format("%06d", startInstance + index % 5000 + 1)); edge.put("targetInstanceId", "INST-" + String.format("%06d", startInstance + (index + 1 + index / 5000) % 5000 + 1));
			}
			state.withObject("meta").withObject("idSequence").put("instance", startInstance + 5000).put("relation", startRelation + 10000); return null;
		});
		List<Double> milliseconds = new ArrayList<>();
		for (int sample = 0; sample < 105; sample++) { long start = System.nanoTime(); assertEquals(1, data(get("/api/v1/facts/instances").param("type", "OBJ-OF-SERVICE").param("domain", "DOMAIN-OF-OPS").param("q", "LOAD-4999"), 200).path("total").asInt()); if (sample >= 5) milliseconds.add((System.nanoTime() - start) / 1_000_000.0); }
		milliseconds.sort(Double::compare); double p95 = milliseconds.get(94); System.out.printf("OF_SEARCH_P95_MS=%.2f samples=100 instances=5000 relations=10000%n", p95); assertTrue(p95 <= 2000, "P95=" + p95);
		assertEquals(300, queries.graph("", "", "", 1, 300).path("nodes").size()); assertTrue(queries.graph("", "", "", 1, 300).path("truncated").asBoolean());
		JsonNode searched = data(get("/api/v1/facts/graph").param("q", "LOAD-4999"), 200);
		assertEquals(1, searched.path("nodes").size()); assertEquals(1, searched.path("total").asInt()); assertFalse(searched.path("truncated").asBoolean());
	}

	private ObjectNode resource(String name) throws Exception { try (var input = getClass().getResourceAsStream("/fix-of-001/" + name)) { return (ObjectNode) mapper.readTree(input); } }
	private ObjectNode json(String value) { return (ObjectNode) mapper.readTree(value); }
	private long revision() { return facts.read(state -> state.path("revision").asLong()); }
	private int count() { return facts.read(state -> state.path("instances").size()); }
	private String id(String key) { return facts.read(state -> { for (JsonNode instance : state.path("instances")) if (key.equals(instance.path("instanceKey").asString())) return instance.path("instanceId").asString(); throw new IllegalArgumentException(key); }); }
	private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, ObjectNode body) {
		builder.header("X-Xiyuan-Role", "MODELER").header("X-Xiyuan-Actor", "fixture-modeler").header("If-Match", revision());
		if (body != null) builder.contentType(APPLICATION_JSON).content(body.toString()); return builder;
	}
	private JsonNode data(MockHttpServletRequestBuilder builder, int status) throws Exception {
		var result = http.perform(builder).andReturn().getResponse(); String body = result.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertEquals(status, result.getStatus(), body); JsonNode envelope = mapper.readTree(body); assertTrue(envelope.has("requestId"), body);
		return status == 200 ? envelope.path("data") : envelope;
	}
	private JsonNode imported() throws Exception { JsonNode result = data(write(post("/api/v1/facts/import"), resource("topo-base.json")), 200); assertEquals("ACCEPTED", result.path("result").asString(), result.toPrettyString()); return result; }
	private ObjectNode addRelation(String type, String target) { ObjectNode value = mapper.createObjectNode(); value.putArray("relationOps").addObject().put("op", "add").put("relationTypeRef", type).put("targetInstanceId", target); return value; }
	private void changeObject(String id, Consumer<ObjectNode> change) { models.write("ENV-OM-001", session -> { var current = session.objects().findById(id).orElseThrow(); ObjectNode node = codec.json(current); change.accept(node); node.put("revision", current.revision() + 1); session.objects().save(codec.definition(current.elementKey(), Kind.OBJECT, node)); return null; }); }
}
