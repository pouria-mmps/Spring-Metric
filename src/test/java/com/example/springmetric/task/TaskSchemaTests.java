package com.example.springmetric.task;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TaskSchemaTests {

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "   "})
	void insert_whenTitleIsNullOrBlank_databaseRejectsRow(String title, @Autowired JdbcTemplate jdbc) {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO tasks (title) VALUES (?)", title))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
