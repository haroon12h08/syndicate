package com.syndicate.notification;

import com.syndicate.notification.dto.NotificationDto;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/notifications")
    public List<NotificationDto> listMine(@AuthenticationPrincipal User currentUser) {
        return notificationService.listMine(currentUser.getId());
    }

    @PostMapping("/api/notifications/{id}/read")
    public NotificationDto markRead(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return notificationService.markRead(id, currentUser.getId());
    }
}
