package com.system.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class NotificationProducerService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationProducerService.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    public NotificationProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Sends a notification message to the specified Kafka topic
     * 
     * @param topic The Kafka topic to send the message to
     * @param key The message key (used for partitioning)
     * @param message The notification message object
     * @return CompletableFuture of the send operation
     */
    public CompletableFuture<SendResult<String, Object>> sendNotification(String topic, String key, Object message) {
        logger.info("Sending message to topic {}: {}", topic, message);
        
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, message);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Message sent successfully to topic {}, partition {}, offset {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to send message to topic {}", topic, ex);
            }
        });
        
        return future;
    }
    
    /**
     * Sends a notification message within a transaction
     * 
     * @param topic The Kafka topic to send the message to
     * @param key The message key
     * @param message The notification message object
     */
    public void sendNotificationInTransaction(String topic, String key, Object message) {
        kafkaTemplate.executeInTransaction(operations -> {
            operations.send(topic, key, message);
            logger.info("Message sent in transaction to topic {}", topic);
            return null;
        });
    }
}
