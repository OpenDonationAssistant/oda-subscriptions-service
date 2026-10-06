package io.github.opendonationassistant.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.opendonationassistant.repository.Subscription;
import io.github.opendonationassistant.repository.SubscriptionData;
import io.github.opendonationassistant.repository.SubscriptionRepository;
import io.micronaut.rabbitmq.bind.RabbitAcknowledgement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventsListenerTest {

  private static final byte[] PAYLOAD = { 1, 2, 3 };

  private final SubscriptionRepository repository = mock(
    SubscriptionRepository.class
  );
  private final EventsListener.EventPublisher publisher = mock(
    EventsListener.EventPublisher.class
  );
  private final RabbitAcknowledgement acknowledgement = mock(
    RabbitAcknowledgement.class
  );
  private final EventsListener listener = new EventsListener(
    publisher,
    repository
  );

  @Test
  void publishesOnlyToMatchingRecipientAndEventType() {
    givenSubscriptions(
      subscription("recipient-1", "app-1", "PaymentEvent"),
      subscription("recipient-2", "app-2", "PaymentEvent"),
      subscription("recipient-1", "app-3", "HistoryItemEvent")
    );

    listener.receive(PAYLOAD, "PaymentEvent", "recipient-1", acknowledgement);

    assertEquals(List.of("app-1"), publishedRoutingKeys());
    verify(acknowledgement).ack();
  }

  @Test
  void publishesToEveryMatchingSubscriptionOfRecipient() {
    givenSubscriptions(
      subscription("recipient-1", "app-1", "PaymentEvent"),
      subscription("recipient-1", "app-2", "PaymentEvent")
    );

    listener.receive(PAYLOAD, "PaymentEvent", "recipient-1", acknowledgement);

    assertEquals(List.of("app-1", "app-2"), publishedRoutingKeys());
    verify(acknowledgement).ack();
  }

  @Test
  void doesNotPublishWhenEventTypeNotSubscribed() {
    givenSubscriptions(
      subscription("recipient-1", "app-1", "HistoryItemEvent")
    );

    listener.receive(PAYLOAD, "PaymentEvent", "recipient-1", acknowledgement);

    verifyNoInteractions(publisher);
    verify(acknowledgement).ack();
  }

  private void givenSubscriptions(Subscription... subscriptions) {
    when(repository.all()).thenReturn(
      CompletableFuture.completedFuture(List.of(subscriptions))
    );
  }

  private List<String> publishedRoutingKeys() {
    ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
    verify(publisher, atLeastOnce()).publish(
      keys.capture(),
      eq("PaymentEvent"),
      eq(PAYLOAD)
    );
    return keys.getAllValues();
  }

  private static Subscription subscription(
    String recipientId,
    String subscriberId,
    String... events
  ) {
    return new Subscription(
      new SubscriptionData(
        recipientId + ":" + subscriberId,
        recipientId,
        subscriberId,
        List.of(events)
      )
    );
  }
}
