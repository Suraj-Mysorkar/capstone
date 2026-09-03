package com.bank.digital.lending.controller;

import com.bank.digital.lending.model.dto.NotificationDTO;
import com.bank.digital.lending.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Live Notifications", description = "Real-time SSE event stream and notification management for loan officers and managers")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to Real-time Notification Stream (SSE)",
               description = "Establishes a persistent Server-Sent Events stream for instant notification push without page refresh")
    public SseEmitter streamNotifications(@RequestParam(name = "username", defaultValue = "markj") String username) {
        return notificationService.registerClient(username);
    }

    @GetMapping
    @Operation(summary = "Get Notifications History",
               description = "Retrieves recent notifications list for a specific employee / manager")
    public ResponseEntity<List<NotificationDTO>> getNotifications(
            @RequestParam(name = "username", defaultValue = "markj") String username) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(username));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark Notification as Read")
    public ResponseEntity<Map<String, Boolean>> markAsRead(@PathVariable("id") String id) {
        boolean success = notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark All Notifications as Read")
    public ResponseEntity<Map<String, Boolean>> markAllAsRead(
            @RequestParam(name = "username", defaultValue = "markj") String username) {
        notificationService.markAllAsRead(username);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/test")
    @Operation(summary = "Send Test Notification (for validation)")
    public ResponseEntity<NotificationDTO> triggerTestNotification(
            @RequestParam(name = "username", defaultValue = "markj") String username,
            @RequestParam(name = "customerName", defaultValue = "Rahul Sharma") String customerName,
            @RequestParam(name = "customerId", defaultValue = "CUST-12") String customerId,
            @RequestParam(name = "activity", defaultValue = "uploaded Aadhaar KYC Document") String activity) {
        
        NotificationDTO notif = new NotificationDTO(
                username,
                "Customer Activity: " + customerName,
                "Customer " + customerName + " (" + customerId + ") has " + activity + ".",
                "DOCUMENT_UPLOADED",
                customerId,
                customerName,
                "APP-TEST-" + System.currentTimeMillis() % 10000
        );
        notificationService.sendNotification(notif);
        return ResponseEntity.ok(notif);
    }
}
