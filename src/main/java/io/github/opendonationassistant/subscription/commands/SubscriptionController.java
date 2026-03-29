package io.github.opendonationassistant.subscription.commands;

import com.nimbusds.jose.shaded.gson.reflect.TypeToken;
import io.github.opendonationassistant.commons.micronaut.BaseController;
import io.github.opendonationassistant.repository.Subscription;
import io.github.opendonationassistant.repository.SubscriptionData;
import io.github.opendonationassistant.repository.SubscriptionRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Controller
public class SubscriptionController extends BaseController {

  private final SubscriptionRepository subscriptionRepository;

  @Inject
  public SubscriptionController(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @Operation(
    summary = "Get subscription by ID",
    description = "Retrieves a subscription by its ID"
  )
  @ApiResponse(
    responseCode = "200",
    description = "Subscription found",
    content = @Content(
      mediaType = "application/json",
      schema = @Schema(implementation = SubscriptionData.class)
    )
  )
  @ApiResponse(
    responseCode = "404",
    description = "Subscription not found",
    content = @Content
  )
  @Get("/subscriptions/{id}")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<SubscriptionData>> getSubscription(
    Authentication auth,
    @PathVariable String id
  ) {
    Optional<String> ownerId = getOwnerId(auth);
    if (ownerId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    return subscriptionRepository
      .findById(id)
      .thenApply(optionalSubscription ->
        optionalSubscription
          .filter(subscription ->
            subscription.data().recipientId().equals(ownerId.get())
          )
          .map(subscription -> HttpResponse.ok(subscription.data()))
          .orElseGet(() -> HttpResponse.notFound())
      );
  }

  @Operation(
    summary = "Get all subscriptions for the authenticated recipient",
    description = "Retrieves all subscriptions for the currently authenticated recipient"
  )
  @ApiResponse(
    responseCode = "200",
    description = "Subscriptions found",
    content = @Content(
      mediaType = "application/json",
      schema = @Schema(implementation = SubscriptionData.class)
    )
  )
  @ApiResponse(
    responseCode = "401",
    description = "Unauthorized",
    content = @Content
  )
  @Get("/subscriptions")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<
    HttpResponse<List<SubscriptionData>>
  > getSubscriptionsByRecipient(Authentication auth) {
    Optional<String> ownerId = getOwnerId(auth);
    if (ownerId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    return subscriptionRepository
      .all()
      .thenApply(subscriptions ->
        subscriptions
          .stream()
          .filter(subscription ->
            subscription.data().recipientId().equals(ownerId.get())
          )
          .map(Subscription::data)
          .toList()
      )
      .thenApply(HttpResponse::ok);
  }
}

