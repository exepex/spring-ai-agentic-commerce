package io.github.exepex.commerce.platform.events;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.BeanUtils;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.util.ClassUtils;

/** The value serializer a service's producer uses, set up exactly as Kafka sets it up. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class KafkaSerializers {

    @SuppressWarnings("unchecked")
    static Serializer<Object> valueSerializerOf(ProducerFactory<String, Object> producers) {
        var supplied = producers.getValueSerializerSupplier().get();
        if (supplied != null) {
            return supplied;
        }
        Map<String, Object> configuration = producers.getConfigurationProperties();
        var serializer = (Serializer<Object>) BeanUtils.instantiateClass(
                serializerClass(configuration.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)));
        serializer.configure(configuration, false);
        return serializer;
    }

    private static Class<?> serializerClass(Object configured) {
        if (configured instanceof Class<?> type) {
            return type;
        }
        return ClassUtils.resolveClassName(String.valueOf(configured), KafkaSerializers.class.getClassLoader());
    }
}
