package com.system.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.util.StringUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    // Concurrency settings
    @Value("${notification.kafka.listener.concurrency:10}")
    private int listenerConcurrency;

    @Value("${notification.kafka.batch.listener.concurrency:10}")
    private int batchListenerConcurrency;

    // Performance tuning parameters
    @Value("${notification.kafka.consumer.max-poll-interval-ms:300000}")
    private int maxPollIntervalMs;

    @Value("${notification.kafka.consumer.fetch-max-bytes:52428800}")
    private int fetchMaxBytes;

    @Value("${notification.kafka.consumer.fetch-min-bytes:1024}")
    private int fetchMinBytes;

    @Value("${notification.kafka.consumer.fetch-max-wait-ms:500}")
    private int fetchMaxWaitMs;

    @Value("${spring.kafka.listener.poll-timeout:5000}")
    private long pollTimeout;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Bootstrap and client settings
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaProperties.getBootstrapServers()));
        configProps.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId());

        // Serializers
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getKeySerializer());
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getValueSerializer());

        // Producer reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, kafkaProperties.getProducer().getAcks());
        configProps.put(ProducerConfig.RETRIES_CONFIG, kafkaProperties.getProducer().getRetries());
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                kafkaProperties.getProducer().getMaxInFlightRequestsPerConnection());
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                kafkaProperties.getProducer().isEnableIdempotence());

        // Add additional properties
        kafkaProperties.getProperties().forEach(configProps::put);

        // Create producer factory with transaction support if transaction ID prefix is set
        if (kafkaProperties.getTemplate() != null &&
                StringUtils.hasText(kafkaProperties.getTemplate().getTransactionIdPrefix())) {
            DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(configProps);
            factory.setTransactionIdPrefix(kafkaProperties.getTemplate().getTransactionIdPrefix());
            return factory;
        } else {
            return new DefaultKafkaProducerFactory<>(configProps);
        }
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());

        // Configure transaction support if transaction ID prefix is set
        if (kafkaProperties.getTemplate() != null &&
                StringUtils.hasText(kafkaProperties.getTemplate().getTransactionIdPrefix())) {
            template.setTransactionIdPrefix(kafkaProperties.getTemplate().getTransactionIdPrefix());
        }

        return template;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Bootstrap settings
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaProperties.getBootstrapServers()));
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());

        // Deserializers
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getKeyDeserializer());
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getValueDeserializer());

        // Consumer behavior settings
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Set to false for manual commits
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaProperties.getConsumer().getMaxPollRecords());
        configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, kafkaProperties.getConsumer().getIsolationLevel());

        // Performance tuning settings
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        configProps.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, fetchMaxBytes);
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, fetchMinBytes);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);

        // Add trusted packages for JsonDeserializer
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.system.notification.model");
        configProps.put(JsonDeserializer.TYPE_MAPPINGS, "notification:com.system.notification.model.Notification");

        // Add additional properties
        kafkaProperties.getProperties().forEach(configProps::put);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Standard listener container factory for processing individual messages
     * with concurrency support
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Configure for manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Set poll timeout
        factory.getContainerProperties().setPollTimeout(pollTimeout);

        // Configure concurrency - number of consumer threads
        factory.setConcurrency(listenerConcurrency);

        // Configure error handling with backoff
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 10) // 1 second delay, max 10 attempts
        ));

        return factory;
    }

    /**
     * Batch listener container factory for processing multiple messages at once
     * with concurrency support
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> batchKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Enable batch listening
        factory.setBatchListener(true);

        // Configure for manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Set poll timeout
        factory.getContainerProperties().setPollTimeout(pollTimeout);

        // Configure concurrency - number of consumer threads
        factory.setConcurrency(batchListenerConcurrency);

        // Configure error handling with backoff
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 10) // 1 second delay, max 10 attempts
        ));

        return factory;
    }

    /**
     * Create KafkaTransactionManager only if transaction ID prefix is set
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka.template", name = "transaction-id-prefix")
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }
}