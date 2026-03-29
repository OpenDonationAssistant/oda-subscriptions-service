package io.github.opendonationassistant;

import static io.github.opendonationassistant.rabbit.Exchange.Exchange;

import io.github.opendonationassistant.rabbit.AMQPConfiguration;
import io.github.opendonationassistant.rabbit.Queue;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.rabbitmq.connect.ChannelInitializer;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@OpenAPIDefinition(info = @Info(title = "oda-subscriptions-service"))
public class Application {

  public static void main(String[] args) {
    Micronaut.build(args).banner(false).start();
  }

  @ContextConfigurer
  public static class Configurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContextBuilder builder) {
      builder.defaultEnvironments("allinone");
    }
  }

  @Singleton
  public ChannelInitializer rabbitConfiguration() {
    var eventsQueue = new Queue("subscriptions.events");
    return new AMQPConfiguration(
      List.of(
        Exchange("history", Map.of("event.HistoryItemEvent", eventsQueue)),
        Exchange("payments", Map.of("event.PaymentEvent", eventsQueue))
      )
    );
  }
}
