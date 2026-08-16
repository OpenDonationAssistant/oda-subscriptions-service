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
 * Command controller for regenerating the client secret of an OpenID Connect
 * application in Keycloak.
 */
@Controller
public class RefreshClientSecret extends BaseController {

  private final KeycloakOidcService keycloakOidcService;
  private final OidcMappingRepository oidcMappingRepository;

  @Inject
  public RefreshClientSecret(
    KeycloakOidcService keycloakOidcService,
    OidcMappingRepository oidcMappingRepository
  ) {
    this.keycloakOidcService = keycloakOidcService;
    this.oidcMappingRepository = oidcMappingRepository;
  }

  @Operation(
    summary = "Refresh OpenID Connect client secret",
    description =
      "Regenerates the client secret of an existing OpenID Connect client " +
      "application in Keycloak"
  )
  @ApiResponse(
    responseCode = "200",
    description = "Client secret regenerated",
    content = @Content(
      mediaType = "application/json",
      schema = @Schema(implementation = RefreshedClientSecret.class)
    )
  )
  @ApiResponse(
    responseCode = "401",
    description = "Unauthorized",
    content = @Content
  )
  @ApiResponse(
    responseCode = "404",
    description = "OpenID Connect application not mapped to user",
    content = @Content
  )
  @Post("/apps/commands/refresh-client-secret")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<RefreshedClientSecret>> refreshClientSecret(
    Authentication auth,
    @Body RefreshClientSecretCommand command
  ) {
    Optional<String> ownerId = getOwnerId(auth);
    if (ownerId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    // Verify that the OpenID Connect application is mapped to the authenticated
    // user before refreshing its secret.
    return oidcMappingRepository
      .findById(command.clientInternalId())
      .thenCompose(optionalMapping -> {
        boolean ownedByUser =
          optionalMapping.isPresent() &&
          optionalMapping.get().ownerId().equals(ownerId.get()) &&
          !optionalMapping.get().deregistered();
        if (ownedByUser) {
          return keycloakOidcService
            .refreshClientSecret(command.clientInternalId())
            .thenApply(response ->
              HttpResponse.ok(
                new RefreshedClientSecret(
                  command.clientInternalId(),
                  response.value()
                )
              )
            );
        } else {
          return CompletableFuture.completedFuture(HttpResponse.unauthorized());
        }
      });
  }

  @Serdeable
  public record RefreshClientSecretCommand(String clientInternalId) {}

  @Serdeable
  public record RefreshedClientSecret(
    String clientInternalId,
    String clientSecret
  ) {}
}
