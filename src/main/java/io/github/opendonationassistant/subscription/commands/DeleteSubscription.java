package io.github.opendonationassistant.subscription.commands;

import io.github.opendonationassistant.commons.micronaut.BaseController;
import io.github.opendonationassistant.repository.SubscriptionRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Controller
public class DeleteSubscription extends BaseController {

    private final SubscriptionRepository subscriptionRepository;

    @Inject
    public DeleteSubscription(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Operation(
        summary = "Delete subscription by ID",
        description = "Deletes a subscription by its ID"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Subscription successfully deleted",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = Void.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "Subscription not found or not owned by user",
        content = @Content
    )
    @Post("/subscriptions/commands/delete")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public CompletableFuture<HttpResponse<Void>> deleteSubscription(
            Authentication auth,
            @Body DeleteSubscriptionCommand command
    ) {
        Optional<String> ownerId = getOwnerId(auth);
        if (ownerId.isEmpty()) {
            return CompletableFuture.completedFuture(HttpResponse.unauthorized());
        }
        // First check if exists and belongs to user, then delete if it does
        return subscriptionRepository.findById(command.subscriptionId())
                .thenCompose(optionalSubscription -> {
                    if (optionalSubscription.isPresent() &&
                            optionalSubscription.get().data().recipientId().equals(ownerId.get())) {
                        return subscriptionRepository.deleteById(command.subscriptionId())
                                .thenApply(ignore -> HttpResponse.ok());
                    } else {
                        return CompletableFuture.completedFuture(HttpResponse.notFound());
                    }
                });
    }

    @Serdeable
    public record DeleteSubscriptionCommand(String subscriptionId) {}
}