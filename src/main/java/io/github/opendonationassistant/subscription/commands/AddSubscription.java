package io.github.opendonationassistant.subscription.commands;

import io.github.opendonationassistant.repository.SubscriptionData;
import io.github.opendonationassistant.repository.SubscriptionRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
public class AddSubscription {

  private final SubscriptionRepository subscriptionRepository;

  @Inject
  public AddSubscription(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @Post("/subscriptions/commands/add")
  public CompletableFuture<HttpResponse<Void>> addSubscription(
    @Body AddSubscriptionCommand command
  ) {
    return subscriptionRepository
      .create(
        new SubscriptionData(
          java.util.UUID.randomUUID().toString(),
          command.recipientId(),
          command.subscriberId(),
          command.events()
        )
      )
      .thenApply(ignore -> HttpResponse.ok());
  }

  @Serdeable
  public record AddSubscriptionCommand(
    String recipientId,
    String subscriberId,
    List<String> events
  ) {}
}

