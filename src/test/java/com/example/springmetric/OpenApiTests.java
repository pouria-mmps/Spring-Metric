package com.example.springmetric;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTests {

	@Test
	void apiDocs_whenRequested_describesApplicationEndpoints(@Autowired MockMvc mockMvc) throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/hello'].get").exists())
				.andExpect(jsonPath("$.paths['/api/tasks'].post").exists())
				.andExpect(jsonPath("$.paths['/api/tasks/{id}'].get").exists())
				.andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.CreateTaskRequest.properties.title.maxLength").value(200));
	}

	@Test
	void swaggerUi_whenRequested_servesUiAndLocalConfiguration(@Autowired MockMvc mockMvc) throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Swagger UI")));
		mockMvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
	}
}
