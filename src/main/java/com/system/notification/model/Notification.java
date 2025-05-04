package com.system.notification.model;

import java.time.LocalDateTime;
import java.util.Map;

public class Notification {
    private String id;
    private String type;
    private String recipient;
    private String subject;
    private String content;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private NotificationPriority priority;
    
    public enum NotificationPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    // Default constructor needed for JSON deserialization
    public Notification() {
        this.createdAt = LocalDateTime.now();
    }
    
    public Notification(String id, String type, String recipient, String subject, 
                        String content, NotificationPriority priority) {
        this.id = id;
        this.type = type;
        this.recipient = recipient;
        this.subject = subject;
        this.content = content;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public NotificationPriority getPriority() {
        return priority;
    }
    
    public void setPriority(NotificationPriority priority) {
        this.priority = priority;
    }
    
    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", recipient='" + recipient + '\'' +
                ", subject='" + subject + '\'' +
                ", priority=" + priority +
                ", createdAt=" + createdAt +
                '}';
    }
}
