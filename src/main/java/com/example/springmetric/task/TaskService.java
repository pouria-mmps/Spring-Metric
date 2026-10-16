package com.example.springmetric.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private final TaskRepository repository;

	public TaskService(TaskRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public TaskResponse createTask(String title) {
		Task task = repository.save(new Task(title));
		return new TaskResponse(task.getId(), task.getTitle());
	}

	@Transactional(readOnly = true)
	public TaskResponse findTask(long id) {
		Task task = repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
		return new TaskResponse(task.getId(), task.getTitle());
	}
}
