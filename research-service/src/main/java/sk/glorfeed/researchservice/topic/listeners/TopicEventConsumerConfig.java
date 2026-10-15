package sk.glorfeed.researchservice.topic.listeners;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class TopicEventConsumerConfig {

    @Bean
    DefaultErrorHandler topicEventErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Retry every listener exception indefinitely; malformed business records must not be recovered/skipped.
        errorHandler.setClassifications(Map.of(), true);
        return errorHandler;
    }
}
