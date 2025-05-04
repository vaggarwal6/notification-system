package com.system.notification.controller;

import com.system.notification.model.Notification;
import com.system.notification.service.NotificationProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    
    private final NotificationProducerService producerService;
    
    @Autowired
    public NotificationController(NotificationProducerService producerService) {
        this.producerService = producerService;
    }
    
    @PostMapping
    public ResponseEntity<String> sendNotification(@RequestBody Notification notification) {
        // Generate ID if not provided
        if (notification.getId() == null || notification.getId().isEmpty()) {
            notification.setId(UUID.randomUUID().toString());
        }
        
        try {
            // Send to Kafka topic
            producerService.sendNotification("notifications", notification.getId(), notification);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body("Notification queued with ID: " + notification.getId());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to queue notification: " + e.getMessage());
        }
    }
    
    @PostMapping("/transactional")
    public ResponseEntity<String> sendTransactionalNotification(@RequestBody Notification notification) {
        // Generate ID if not provided
        if (notification.getId() == null || notification.getId().isEmpty()) {
            notification.setId(UUID.randomUUID().toString());
        }
        
        try {
            // Send to Kafka topic within a transaction
            producerService.sendNotificationInTransaction("notifications", notification.getId(), notification);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body("Notification queued with ID: " + notification.getId());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to queue notification: " + e.getMessage());
        }
    }
}
