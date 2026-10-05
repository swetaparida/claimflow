package com.insurer.claimflow.shared.config;

import com.insurer.claimflow.shared.messaging.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /** Declares all claim topics (and their dead-letter topics) so they exist with the intended layout. */
    @Bean
    public KafkaAdmin.NewTopics claimTopics(@Value("${claimflow.kafka.partitions:3}") int partitions,
                                            @Value("${claimflow.kafka.replication-factor:1}") short replicas) {
        return new KafkaAdmin.NewTopics(Topics.ALL.stream()
                .flatMap(topic -> java.util.stream.Stream.of(
                        TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build(),
                        TopicBuilder.name(topic + "-dlt").partitions(partitions).replicas(replicas).build()))
                .toArray(org.apache.kafka.clients.admin.NewTopic[]::new));
    }

    /**
     * Retries a failing record with exponential back-off, then parks it on {@code <topic>-dlt}
     * so one poison message cannot block the partition. Picked up automatically by Spring Boot's
     * listener container factory.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(10_000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Retry {} for record {}-{}@{}: {}", attempt, record.topic(), record.partition(), record.offset(),
                        ex.getMessage()));
        return handler;
    }
}
