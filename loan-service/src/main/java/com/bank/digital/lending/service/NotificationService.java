package com.bank.digital.lending.service;

import com.bank.digital.lending.model.dto.NotificationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@EnableScheduling
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    // username -> list of active SSE emitters (multiple tabs/devices supported)
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    
    // In-memory persistent history of recent notifications
    private final Deque<NotificationDTO> notificationHistory = new ConcurrentLinkedDeque<>();

    public NotificationService() {
        // Seed initial notifications for managers / credit officers
        NotificationDTO seed = new NotificationDTO(
                "all",
                "Credit Assessment Queue Active",
                "Welcome to Digital Lending Portal. Credit assessment and verification queues are active.",
                "QUEUE_READY",
                "CUST-1",
                "All Managers",
                "APP-SYSTEM"
        );
        notificationHistory.addFirst(seed);
    }

    public SseEmitter registerClient(String username) {
        String user = (username != null && !username.isBlank()) ? username.trim().toLowerCase() : "all";
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        userEmitters.computeIfAbsent(user, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(user, emitter));
        emitter.onTimeout(() -> removeEmitter(user, emitter));
        emitter.onError(e -> removeEmitter(user, emitter));

        // Send immediate connection established event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "user", user,
                            "timestamp", java.time.LocalDateTime.now().toString()
                    ), MediaType.APPLICATION_JSON));
            
            // Send unread notifications for this user
            List<NotificationDTO> unread = getNotificationsForUser(user).stream()
                    .filter(n -> !n.isRead())
                    .limit(10)
                    .collect(Collectors.toList());
            
            for (NotificationDTO n : unread) {
                emitter.send(SseEmitter.event()
                        .name("NOTIFICATION")
                        .id(n.getId())
                        .data(n, MediaType.APPLICATION_JSON));
            }
        } catch (IOException e) {
            log.warn("Failed to send initial SSE payload to user {}: {}", user, e.getMessage());
            removeEmitter(user, emitter);
        }

        log.info("Registered SSE notification emitter for user: {}", user);
        return emitter;
    }

    private void removeEmitter(String user, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = userEmitters.get(user);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                userEmitters.remove(user);
            }
        }
        log.debug("Removed SSE emitter for user: {}", user);
    }

    public void sendNotification(NotificationDTO notification) {
        if (notification == null) return;
        
        notificationHistory.addFirst(notification);
        // Trim history to 100 items
        while (notificationHistory.size() > 100) {
            notificationHistory.removeLast();
        }

        String recipient = notification.getRecipientUsername() != null 
                ? notification.getRecipientUsername().trim().toLowerCase() 
                : "all";

        log.info("Broadcasting notification [{}] to recipient [{}] (Event: {})", 
                notification.getTitle(), recipient, notification.getEventType());

        dispatchToUser(recipient, notification);
        // If recipient is a manager or 'all', also broadcast to all active manager emitters and 'all'
        boolean isMgr = recipient.startsWith("mgr") || "markj".equalsIgnoreCase(recipient) || "all".equalsIgnoreCase(recipient);
        if (isMgr) {
            for (String activeUser : userEmitters.keySet()) {
                if (!activeUser.equalsIgnoreCase(recipient) && (activeUser.startsWith("mgr") || "markj".equalsIgnoreCase(activeUser) || "all".equalsIgnoreCase(activeUser))) {
                    dispatchToUser(activeUser, notification);
                }
            }
        } else if (!"all".equalsIgnoreCase(recipient)) {
            dispatchToUser("all", notification);
        }
    }

    private void dispatchToUser(String user, NotificationDTO notification) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(user);
        if (emitters != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("NOTIFICATION")
                            .id(notification.getId())
                            .data(notification, MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    log.debug("Error sending SSE to emitter for {}: {}", user, e.getMessage());
                    deadEmitters.add(emitter);
                }
            }
            if (!deadEmitters.isEmpty()) {
                emitters.removeAll(deadEmitters);
            }
        }
    }

    public List<NotificationDTO> getNotificationsForUser(String username) {
        String user = (username != null && !username.isBlank()) ? username.trim().toLowerCase() : "all";
        boolean isManager = user.startsWith("mgr") || "markj".equalsIgnoreCase(user) || "admin".equalsIgnoreCase(user) || "all".equalsIgnoreCase(user);
        return notificationHistory.stream()
                .filter(n -> {
                    String recipient = n.getRecipientUsername() != null ? n.getRecipientUsername().trim().toLowerCase() : "all";
                    if ("all".equalsIgnoreCase(recipient) || "all".equalsIgnoreCase(user)) return true;
                    if (user.equalsIgnoreCase(recipient)) return true;
                    if (isManager && (recipient.startsWith("mgr") || "markj".equalsIgnoreCase(recipient))) return true;
                    return false;
                })
                .collect(Collectors.toList());
    }

    public boolean markAsRead(String notificationId) {
        if (notificationId == null) return false;
        for (NotificationDTO n : notificationHistory) {
            if (notificationId.equalsIgnoreCase(n.getId())) {
                n.setRead(true);
                return true;
            }
        }
        return false;
    }

    public void markAllAsRead(String username) {
        String user = (username != null && !username.isBlank()) ? username.trim().toLowerCase() : "all";
        for (NotificationDTO n : notificationHistory) {
            if ("all".equalsIgnoreCase(user) || user.equalsIgnoreCase(n.getRecipientUsername())) {
                n.setRead(true);
            }
        }
    }

    /**
     * Heartbeat every 20 seconds to prevent Azure App Service / gateway timeouts on idle connections
     */
    @Scheduled(fixedRate = 20000)
    public void sendHeartbeat() {
        if (userEmitters.isEmpty()) return;

        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : userEmitters.entrySet()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            if (!deadEmitters.isEmpty()) {
                entry.getValue().removeAll(deadEmitters);
            }
        }
    }
}
