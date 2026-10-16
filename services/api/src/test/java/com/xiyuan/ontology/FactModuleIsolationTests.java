package com.xiyuan.ontology;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties = { "logging.level.root=OFF", "debug=false" })
@AutoConfigureMockMvc
class FactModuleIsolationTests {
	private static final Path WORKSPACE = Path.of("target/test-workspaces/fact-disabled-" + UUID.randomUUID()).toAbsolutePath();
	@DynamicPropertySource static void workspace(DynamicPropertyRegistry registry) throws Exception {
		Files.createDirectories(WORKSPACE.resolve(".facts-store")); Files.writeString(WORKSPACE.resolve(".facts-store/current.json"), "corrupt authority");
		registry.add("xiyuan.workspace.dir", WORKSPACE::toString);
	}
	@Autowired MockMvc http;
	@Test void failedObjectFactSelfCheckLeavesModelingAndIntegrationReadableAndWritable() throws Exception {
		http.perform(get("/api/v1/facts/instances")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OF_MODULE_DISABLED"));
		http.perform(get("/api/v1/object-model/graph")).andExpect(status().isOk());
		http.perform(post("/api/v1/domains").contentType(APPLICATION_JSON).content("{\"name\":\"Isolation test\"}")).andExpect(status().isOk());
		http.perform(get("/api/v1/integrations/api-services")).andExpect(status().isOk());
		http.perform(post("/api/v1/integrations/api-services").contentType(APPLICATION_JSON).content("{\"id\":\"API-FACT-ISOLATION\"}")).andExpect(status().isOk());
	}
}
