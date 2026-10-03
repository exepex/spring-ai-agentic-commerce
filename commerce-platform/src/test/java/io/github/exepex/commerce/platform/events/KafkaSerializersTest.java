package io.github.exepex.commerce.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class KafkaSerializersTest {

    @SuppressWarnings("unchecked")
    private final ProducerFactory<String, Object> producers = mock(ProducerFactory.class);

    @Test
    void aFactoryWithoutASupplierGetsTheSerializerItsConfigurationNames() {
        when(producers.getValueSerializerSupplier()).thenReturn(null);
        when(producers.getConfigurationProperties()).thenReturn(Map.of(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class.getName(),
                "spring.json.add.type.headers", false));

        assertThat(KafkaSerializers.valueSerializerOf(producers)).isInstanceOf(JacksonJsonSerializer.class);
    }

    @Test
    void aFactoryWhoseSupplierGivesNothingGetsTheConfiguredSerializerClass() {
        when(producers.getValueSerializerSupplier()).thenReturn(() -> null);
        when(producers.getConfigurationProperties()).thenReturn(Map.of(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));

        assertThat(KafkaSerializers.valueSerializerOf(producers)).isInstanceOf(StringSerializer.class);
    }

    @Test
    void aSuppliedSerializerIsUsedAsIs() {
        var serializer = new JacksonJsonSerializer<Object>();
        Supplier<Serializer<Object>> supplier = () -> serializer;
        when(producers.getValueSerializerSupplier()).thenReturn(supplier);

        assertThat(KafkaSerializers.valueSerializerOf(producers)).isSameAs(serializer);
    }
}
