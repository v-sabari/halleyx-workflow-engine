package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Notification;
import com.halleyx.workflow_engine.exception.ResourceNotFoundException;
import com.halleyx.workflow_engine.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * NotificationController
 *
 * IMPROVEMENTS vs original:
 * - markAsRead throws ResourceNotFoundException (404) via typed exception.
 * - @RequiredArgsConstructor replaces manual constructor.
 * - @CrossOrigin updated to allow all origins (consistent with other controllers).
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    /** GET /notifications — all notifications newest first */
    @GetMapping
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    /** GET /notifications/unread — unread only, newest first */
    @GetMapping("/unread")
    public List<Notification> getUnreadNotifications() {
        return notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();
    }

    /** PUT /notifications/{id}/read — mark single notification as read */
    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notification.setIsRead(true);
        return ResponseEntity.ok(notificationRepository.save(notification));
    }

    /** PUT /notifications/read-all — mark all unread as read */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        List<Notification> unread =
                notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }
}
