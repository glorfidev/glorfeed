package sk.glorfeed.researchservice.topic.listeners;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.messaging.converter.MessageConversionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TopicEventConsumerConfigTest {

    private final TopicEventConsumerConfig config = new TopicEventConsumerConfig();
    private final Consumer<String, String> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final ConsumerRecord<String, String> record =
            new ConsumerRecord<>("outbox.event.topic", 0, 12L, "topic-id", "{}");

    @Test
    void retriesExceptionsThatSpringKafkaNormallyTreatsAsFatal() {
        DefaultErrorHandler handler = config.topicEventErrorHandler();

        assertThat(handler.handleOne(new ClassCastException("bad record"), record, consumer, container)).isFalse();
        assertThat(handler.handleOne(new MessageConversionException("bad payload"), record, consumer, container))
                .isFalse();
    }

    @Test
    void doesNotRecoverOrSkipAfterRepeatedListenerFailures() {
        DefaultErrorHandler handler = config.topicEventErrorHandler();
        IllegalArgumentException failure = new IllegalArgumentException("malformed business event");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(handler.handleOne(failure, record, consumer, container))
                    .as("attempt %s remains retryable", attempt + 1)
                    .isFalse();
        }
    }
}
