package com.example.springmetric.task;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class TaskNotFoundException extends RuntimeException {

	TaskNotFoundException(long id) {
		super("Task " + id + " does not exist");
	}
}
