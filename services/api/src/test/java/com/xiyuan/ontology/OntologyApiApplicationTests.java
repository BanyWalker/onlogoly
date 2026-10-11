package com.xiyuan.ontology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "xiyuan.workspace.dir=target/test-workspace")
@AutoConfigureMockMvc
class OntologyApiApplicationTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void healthIsAvailableWithoutDatabaseOrAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void objectModelGraphLoadsFromFileWorkspace() throws Exception {
		// MVP 阶段 /api/** 放行（安全后续版本补充）；工作空间使用测试目录，不污染源码目录
		mockMvc.perform(get("/api/v1/object-model/graph"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestId").exists())
			.andExpect(jsonPath("$.data.workspace").doesNotExist())
			.andExpect(jsonPath("$.data.objects").isArray())
			.andExpect(jsonPath("$.data.objects[0].attributeCount").isNumber())
			.andExpect(jsonPath("$.data.relations").isArray())
			.andExpect(jsonPath("$.data.domains").isArray())
			.andExpect(jsonPath("$.data.layoutRevision").isNumber());
	}

	@Test
	void moduleBoundariesAreValid() {
		ApplicationModules.of(OntologyApiApplication.class).verify();
	}

	@Test
	void relationshipDetailsAreReadableForDrawerEditing() throws Exception {
		mockMvc.perform(get("/api/v1/relationships/REL-APP-SUPPORTS-SVC"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value("REL-APP-SUPPORTS-SVC"))
			.andExpect(jsonPath("$.data.sourceCardinality.min").value(1))
			.andExpect(jsonPath("$.data.targetType").value("OBJ-SERVICE"));
	}

	@Test
	void objectCreationRejectsMissingMvpFields() throws Exception {
		mockMvc.perform(post("/api/v1/object-types")
				.contentType(APPLICATION_JSON)
				.content("{\"id\":\"OBJ-INVALID\",\"name\":\"Invalid\",\"domainId\":\"DOMAIN-OPS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OBJ_REQUIRED_MISSING"))
			.andExpect(jsonPath("$.details[0].field").value("definition"));
	}

}
