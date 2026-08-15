package io.github.opendonationassistant.keycloak.command;

import io.github.opendonationassistant.commons.micronaut.BaseController;
import io.github.opendonationassistant.keycloak.service.KeycloakOidcService;
import io.github.opendonationassistant.repository.OidcMappingRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Command controller for deregistering (deleting) an OpenID Connect application
 * in Keycloak.
 */
@Controller
public class DeregisterOidcApplication extends BaseController {

  private final KeycloakOidcService keycloakOidcService;
  private final OidcMappingRepository oidcMappingRepository;

  @Inject
  public DeregisterOidcApplication(
    KeycloakOidcService keycloakOidcService,
    OidcMappingRepository oidcMappingRepository
  ) {
    this.keycloakOidcService = keycloakOidcService;
    this.oidcMappingRepository = oidcMappingRepository;
  }

  @Operation(
    summary = "Deregister OpenID Connect application",
    description = "Deletes an existing OpenID Connect client application from Keycloak"
  )
  @ApiResponse(
    responseCode = "200",
    description = "OpenID Connect application deregistered",
    content = @Content(
      mediaType = "application/json",
      schema = @Schema(implementation = Void.class)
    )
  )
  @ApiResponse(
    responseCode = "401",
    description = "Unauthorized",
    content = @Content
  )
  @Post("/keycloak/commands/deregister-oidc-client")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<Void>> deregisterOidcApplication(
    Authentication auth,
    @Body DeregisterOidcClientCommand command
  ) {
    Optional<String> ownerId = getOwnerId(auth);
    if (ownerId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    return oidcMappingRepository
      .findById(command.clientId())
      .thenCompose(optionalMapping -> {
        boolean ownedByUser =
          optionalMapping.isPresent() &&
          optionalMapping.get().ownerId().equals(ownerId.get()) &&
          !optionalMapping.get().deregistered();
        if (ownedByUser) {
          return keycloakOidcService
            .deregister(command.clientId())
            .thenApply(ignore -> HttpResponse.ok());
        } else {
          return CompletableFuture.completedFuture(HttpResponse.unauthorized());
        }
      });
  }

  @Serdeable
  public record DeregisterOidcClientCommand(String clientId) {}
}
