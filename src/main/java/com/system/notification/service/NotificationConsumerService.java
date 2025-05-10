package com.system.notification.service;

import com.system.notification.model.Notification;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for consuming and processing notifications from Kafka.
 * Implements optimized batch processing with high concurrency.
 */
@Service
public class NotificationConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumerService.class);

    private final int threadPoolSize;
    private final ExecutorService executorService;
    private final int batchSize;

    public NotificationConsumerService(
            @Value("${notification.processing.thread-pool-size:50}") int threadPoolSize,
            @Value("${spring.kafka.consumer.max-poll-records:1000}") int batchSize) {
        this.threadPoolSize = threadPoolSize;
        this.batchSize = batchSize;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        logger.info("Initialized notification consumer with thread pool size: {} and batch size: {}",
                threadPoolSize, batchSize);
    }

    /**
     * Optimized batch listener that processes notifications in sub-batches for maximum throughput.
     *
     * @param records        List of Kafka consumer records containing notifications
     * @param acknowledgment The acknowledgment object
     */
    @KafkaListener(
            topics = "${notification.kafka.topic:notifications}",
            groupId = "${spring.kafka.consumer.group-id:notification-group}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumeBatchNotifications(
            @Payload List<ConsumerRecord<String, Notification>> records,
            Acknowledgment acknowledgment) {

        if (records.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }

        int totalSize = records.size();
        logger.info("Received batch of {} notifications", totalSize);
        long startTime = System.currentTimeMillis();

        try {
            // Calculate optimal sub-batch size based on thread pool size
            int subBatchSize = Math.max(1, totalSize / threadPoolSize);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Process in sub-batches for better parallelism
            for (int i = 0; i < totalSize; i += subBatchSize) {
                int endIndex = Math.min(i + subBatchSize, totalSize);
                List<ConsumerRecord<String, Notification>> subBatch = records.subList(i, endIndex);

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        processNotificationBatch(subBatch);
                    } catch (Exception e) {
                        logger.error("Error processing sub-batch: {}", e.getMessage(), e);
                    }
                }, executorService));
            }

            // Wait for all processing to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Acknowledge the entire batch
            acknowledgment.acknowledge();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed batch of {} notifications in {} ms (avg {} ms per message)",
                    totalSize, processingTime, processingTime / totalSize);

        } catch (Exception e) {
            logger.error("Error processing notification batch: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * Process a batch of notifications efficiently.
     *
     * @param records The batch of Kafka records to process
     */
    private void processNotificationBatch(List<ConsumerRecord<String, Notification>> records) {
        try {
            // Process each notification in the sub-batch
            for (ConsumerRecord<String, Notification> record : records) {
                processNotification(record);
            }
        } catch (Exception e) {
            logger.error("Error in batch processing: {}", e.getMessage(), e);
        }
    }

    /**
     * Process a single notification with Kafka metadata.
     * Note: In a real implementation, you would replace the sleep with actual business logic.
     *
     * @param record The Kafka consumer record containing the notification and metadata
     */
    private void processNotification(ConsumerRecord<String, Notification> record) {
        try {
            Notification notification = record.value();
            String key = record.key();
            long offset = record.offset();
            int partition = record.partition();
            long timestamp = record.timestamp();
            String topic = record.topic();

            logger.info("Processing notification: [Topic: {}, Partition: {}, Offset: {}, Key: {}, Timestamp: {}] {}",
                    topic, partition, offset, key, timestamp, notification);

            // Your notification processing logic here
            // Replace this sleep with your actual business logic
            // For testing, you might want to reduce this sleep time to simulate faster processing
            Thread.sleep(2000);

            // Add your business logic here

            logger.info("Completed notification: [Topic: {}, Partition: {}, Offset: {}, Key: {}] {}",
                    topic, partition, offset, key, notification);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Processing interrupted: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Properly shut down the executor service when the application stops.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down notification consumer executor service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}