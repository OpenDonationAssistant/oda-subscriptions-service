package io.github.opendonationassistant.listener;

import io.github.opendonationassistant.repository.SubscriptionRepository;
import io.micronaut.messaging.annotation.MessageHeader;
import io.micronaut.rabbitmq.annotation.Binding;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitClient;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.rabbitmq.bind.RabbitAcknowledgement;
import jakarta.inject.Inject;

@RabbitListener
public class EventsListener {

  private static final String EXCHANGE_NAME = "subscriptions";

  private final EventPublisher eventPublisher;
  private final SubscriptionRepository repository;

  @Inject
  public EventsListener(
    EventPublisher eventPublisher,
    SubscriptionRepository repository
  ) {
    this.eventPublisher = eventPublisher;
    this.repository = repository;
  }

  @Queue("subscriptions.events")
  public void receive(
    byte[] data,
    @MessageHeader("type") String type,
    RabbitAcknowledgement acknowledgement
  ) {
    repository
      .all()
      .thenAccept(subscriptions -> {
        subscriptions
          .stream()
          .filter(subscription -> subscription.data().events().contains(type))
          .forEach(subscription -> {
            eventPublisher.publish(
              subscription.data().subscriberId(),
              type,
              data
            );
          });
        acknowledgement.ack();
      })
      .join();
  }

  @RabbitClient(EXCHANGE_NAME)
  public interface EventPublisher {
    void publish(@Binding String routingKey, String type, byte[] data);
  }
}
