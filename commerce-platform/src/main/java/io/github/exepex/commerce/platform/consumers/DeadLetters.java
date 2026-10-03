package io.github.exepex.commerce.platform.consumers;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

/**
 * Parks an event on {@code <topic>.DLT} exactly as it arrived: raw bytes stay bytes, text stays text, and an event that
 * could not even be read keeps its original bytes, so it can be inspected and replayed. Kafka picks the dead-letter
 * partition from the event's key, so the dead-letter topic needs no more partitions than it has.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DeadLetters {

    static final String SUFFIX = ".DLT";

    static DeadLetterPublishingRecoverer recoverer(ProducerFactory<Object, Object> producers,
            KafkaTemplate<Object, Object> objects) {
        var templates = new LinkedHashMap<Class<?>, KafkaOperations<?, ?>>();
        templates.put(byte[].class, templateWith(producers, ByteArraySerializer.class));
        templates.put(String.class, templateWith(producers, StringSerializer.class));
        templates.put(Object.class, objects);
        return new DeadLetterPublishingRecoverer(templates,
                (event, failure) -> new TopicPartition(event.topic() + SUFFIX, -1));
    }

    private static KafkaTemplate<Object, Object> templateWith(ProducerFactory<Object, Object> producers,
            Class<?> valueSerializer) {
        return new KafkaTemplate<>(producers, Map.of(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer));
    }
}
