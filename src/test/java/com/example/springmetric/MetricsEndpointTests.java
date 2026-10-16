package com.example.springmetric;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MetricsEndpointTests {

	@Test
	void scrape_whenPoolIsExhausted_recordsTimeoutAndRecovers(@Autowired DataSource dataSource,
			@Autowired MockMvc mockMvc) throws Exception {
		List<Connection> borrowed = new ArrayList<>();
		try {
			for (int i = 0; i < 5; i++) {
				borrowed.add(dataSource.getConnection());
			}
			assertThatThrownBy(() -> dataSource.getConnection()).isInstanceOf(SQLException.class);
			String scrape = mockMvc.perform(get("/actuator/prometheus"))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(scrape.lines().filter(line -> line.startsWith("hikaricp_connections_timeout_total{")))
					.anySatisfy(line -> assertThat(Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
							.isGreaterThanOrEqualTo(1.0));
		}
		finally {
			for (Connection connection : borrowed) {
				connection.close();
			}
		}
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(1)).isTrue();
		}
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void scrape_whenDatabaseIsConnected_exposesPoolMetrics(@Autowired MockMvc mockMvc) throws Exception {
		String scrape = mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(scrape).contains("hikaricp_connections_active", "hikaricp_connections_idle",
				"hikaricp_connections_pending", "hikaricp_connections_max",
				"hikaricp_connections_timeout_total", "hikaricp_connections_acquire_seconds_count");
		assertThat(scrape.lines().filter(line -> line.startsWith("hikaricp_connections_max{")))
				.anySatisfy(line -> assertThat(line).contains("pool=\"SpringMetricPool\"",
						"application=\"SpringMetric\"").endsWith(" 5.0"));
	}

	@Test
	void scrape_whenRequestCompletes_exposesBoundedDurationBuckets(@Autowired MockMvc mockMvc) throws Exception {
		mockMvc.perform(get("/api/hello")).andExpect(status().isOk());
		String scrape = mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(scrape.lines().filter(line -> line.startsWith("http_server_requests_seconds_bucket{")
				&& line.contains("uri=\"/api/hello\"")))
				.anySatisfy(line -> assertThat(line).contains("le=\"0.001\""))
				.anySatisfy(line -> assertThat(line).contains("le=\"5.0\""))
				.anySatisfy(line -> assertThat(line).contains("le=\"+Inf\""));
		assertThat(scrape).doesNotContain("http_server_requests_active_seconds_bucket");
	}

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
