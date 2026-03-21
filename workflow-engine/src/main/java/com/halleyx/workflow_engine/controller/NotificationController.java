package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Notification;
import com.halleyx.workflow_engine.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * GET /notifications — all notifications newest first
     */
    @GetMapping
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * GET /notifications/unread — unread only, newest first
     */
    @GetMapping("/unread")
    public List<Notification> getUnreadNotifications() {
        return notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();
    }

    /**
     * PUT /notifications/{id}/read — mark single notification as read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
        notification.setIsRead(true);
        return ResponseEntity.ok(notificationRepository.save(notification));
    }

    /**
     * PUT /notifications/read-all — mark all unread as read
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        List<Notification> unread =
                notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }
}