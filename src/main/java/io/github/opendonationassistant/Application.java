package io.github.opendonationassistant;

import static io.github.opendonationassistant.rabbit.Exchange.Exchange;

import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.rabbit.AMQPConfiguration;
import io.github.opendonationassistant.rabbit.Queue;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.annotation.Factory;
import io.micronaut.rabbitmq.connect.ChannelInitializer;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@OpenAPIDefinition(info = @Info(title = "oda-subscriptions-service"))
@Factory
public class Application {

  private ODALogger log = new ODALogger(this);

  public static void main(String[] args) {
    Micronaut.build(args).banner(false).classes(Application.class).start();
  }

  @ContextConfigurer
  public static class Configurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContextBuilder builder) {
      builder.defaultEnvironments("standalone");
    }
  }

  @Bean
  @Singleton
  public ChannelInitializer rabbitConfiguration() {
    var eventsQueue = new Queue("subscriptions.events");
    log.debug("Initializing RabbitMQ", Map.of());
    return new AMQPConfiguration(
      List.of(
        Exchange("history", Map.of("event.HistoryItemEvent", eventsQueue)),
        Exchange("payments", Map.of("event.PaymentEvent", eventsQueue)),
        Exchange(
          "subscriptions",
          Map.of("natix", new Queue("subscriptions.natix"))
        )
      )
    );
  }
}
