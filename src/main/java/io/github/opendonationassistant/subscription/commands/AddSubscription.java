package io.github.opendonationassistant.subscription.commands;

import io.github.opendonationassistant.commons.micronaut.BaseController;
import io.github.opendonationassistant.repository.SubscriptionData;
import io.github.opendonationassistant.repository.SubscriptionRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Controller
public class AddSubscription extends BaseController {

  private final SubscriptionRepository subscriptionRepository;

  @Inject
  public AddSubscription(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @Post("/subscriptions/commands/add")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<Void>> addSubscription(
    Authentication auth,
    @Body AddSubscriptionCommand command
  ) {
    var recipientId = getOwnerId(auth);
    if (recipientId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    var subscriberId = Optional.ofNullable(auth)
      .map(Authentication::getAttributes)
      .map(it -> it.get("aud"))
      .map(String::valueOf);
    if (subscriberId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    return subscriptionRepository
      .create(
        new SubscriptionData(
          java.util.UUID.randomUUID().toString(),
          recipientId.get(),
          subscriberId.get(),
          command.events()
        )
      )
      .thenApply(ignore -> HttpResponse.ok());
  }

  @Serdeable
  public record AddSubscriptionCommand(List<String> events) {}
}
