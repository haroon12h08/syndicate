package com.syndicate.task;

import com.syndicate.issue.IssueSeverity;
import com.syndicate.task.dto.CreateTaskRequest;
import com.syndicate.task.dto.TaskDto;
import com.syndicate.task.dto.UpdateTaskRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/api/transactions/{id}/tasks")
    public List<TaskDto> list(@PathVariable UUID id,
                               @RequestParam(required = false) UUID workstreamId,
                               @RequestParam(required = false) TaskStatus status,
                               @RequestParam(required = false) IssueSeverity severity,
                               @RequestParam(required = false) UUID assignedUserId,
                               @AuthenticationPrincipal User currentUser) {
        return taskService.list(id, workstreamId, status, severity, assignedUserId, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@PathVariable UUID id, @Valid @RequestBody CreateTaskRequest request,
                           @AuthenticationPrincipal User currentUser) {
        return taskService.create(id, request, currentUser);
    }

    @PatchMapping("/api/tasks/{id}")
    public TaskDto update(@PathVariable UUID id, @RequestBody UpdateTaskRequest request,
                           @AuthenticationPrincipal User currentUser) {
        return taskService.update(id, request, currentUser);
    }
}
