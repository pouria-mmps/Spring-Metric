package com.example.springmetric.task;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService service;

	public TaskController(TaskService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
		TaskResponse task = service.createTask(request.title());
		return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
	}

	@GetMapping("/{id}")
	public TaskResponse findTask(@PathVariable long id) {
		return service.findTask(id);
	}

	public record CreateTaskRequest(@NotBlank @Size(max = 200) String title) {
	}
}
