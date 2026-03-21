package com.halleyx.workflow_engine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halleyx.workflow_engine.entity.Notification;
import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender         mailSender;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper           objectMapper = new ObjectMapper();

    /**
     * Dispatches a NOTIFICATION step.
     * 1. Saves a Notification record to the DB (visible in the bell dropdown).
     * 2. Sends an email if the step config contains an assignee_email.
     */
    public void sendNotification(Step step, Map<String, Object> executionInput) {
        if (step == null) {
            log.warn("sendNotification called with null step, skipping.");
            return;
        }

        // ── Parse step configuration ──────────────────────────────────────
        Map<String, Object> config = Map.of();
        if (step.getConfiguration() != null && !step.getConfiguration().isBlank()) {
            try {
                config = objectMapper.readValue(
                        step.getConfiguration(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Could not parse step configuration JSON for step {}: {}",
                        step.getId(), e.getMessage());
            }
        }

        String channel = (String) config.getOrDefault("notification_channel", "EMAIL");
        String message = buildMessage(config, step, executionInput);

        // ── Persist Notification record ───────────────────────────────────
        Notification notification = Notification.builder()
                .executionId((UUID) executionInput.getOrDefault("__executionId", null))
                .stepName(step.getStepName())
                .message(message)
                .channel(channel)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
        log.info("Saved notification for step '{}'", step.getStepName());

        // ── Send via channel ──────────────────────────────────────────────
        switch (channel.toUpperCase()) {
            case "EMAIL" -> sendEmail(config, step, message);
            default      -> log.info("Channel '{}' logged only (no external dispatch).", channel);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildMessage(Map<String, Object> config,
                                Step step,
                                Map<String, Object> executionInput) {
        String template = (String) config.getOrDefault(
                "template",
                "Notification from workflow step: " + step.getStepName());

        // Simple {{variableName}} substitution from execution input
        for (Map.Entry<String, Object> entry : executionInput.entrySet()) {
            if (entry.getValue() != null) {
                template = template.replace(
                        "{{" + entry.getKey() + "}}",
                        String.valueOf(entry.getValue()));
            }
        }
        return template;
    }

    private void sendEmail(Map<String, Object> config, Step step, String body) {
        String to = (String) config.get("assignee_email");
        if (to == null || to.isBlank()) {
            log.info("No assignee_email configured for step '{}' — email skipped.",
                    step.getStepName());
            return;
        }
        String subject = (String) config.getOrDefault(
                "subject", "Workflow Notification: " + step.getStepName());
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent to '{}' for step '{}'", to, step.getStepName());
        } catch (Exception e) {
            // Log but don't crash the workflow — notification is already persisted
            log.error("Email send failed for step '{}': {}", step.getStepName(), e.getMessage());
        }
    }
}