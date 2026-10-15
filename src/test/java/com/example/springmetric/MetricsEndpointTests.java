package com.example.springmetric;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetricsEndpointTests {

	@Test
	void scrape_whenApplicationIsRunning_exposesRuntimeAndHttpMetrics(@Autowired MockMvc mockMvc) throws Exception {
		mockMvc.perform(get("/api/hello")).andExpect(status().isOk());

		String scrape = mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(scrape).contains("jvm_memory_used_bytes", "jvm_threads_live_threads",
				"process_uptime_seconds", "process_cpu_usage", "http_server_requests_seconds_count");
		assertThat(scrape.lines().filter(line -> line.startsWith("http_server_requests_seconds_count{")))
				.anySatisfy(line -> assertThat(line).contains("application=\"SpringMetric\"",
						"uri=\"/api/hello\"", "method=\"GET\"", "status=\"200\""));
	}

	@Test
	void health_whenApplicationIsRunning_returnsUpWithoutDetails(@Autowired MockMvc mockMvc) throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void metrics_whenEndpointIsNotExposed_returnsNotFound(@Autowired MockMvc mockMvc) throws Exception {
		mockMvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
	}
}
