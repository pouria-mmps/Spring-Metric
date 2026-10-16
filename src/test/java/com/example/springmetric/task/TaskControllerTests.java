package com.example.springmetric.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void createTask_whenTitleIsValid_persistsTaskAndReturnsLocation() throws Exception {
		String location = mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Learn connection pools\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("Learn connection pools"))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location)).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Learn connection pools"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"title\":null}", "{\"title\":\"\"}", "{\"title\":\"   \"}", "{"})
	void createTask_whenRequestIsInvalid_returnsBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createTask_whenTitleHas200Characters_returnsCreated() throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + "a".repeat(200) + "\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void createTask_whenTitleExceeds200Characters_returnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + "a".repeat(201) + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void findTask_whenTaskDoesNotExist_returnsNotFound() throws Exception {
		mockMvc.perform(get("/api/tasks/9223372036854775807")).andExpect(status().isNotFound());
	}

	@Test
	void findTask_whenIdIsNotNumeric_returnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/tasks/invalid")).andExpect(status().isBadRequest());
	}
}
