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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Command controller for updating the settings of an OpenID Connect
 * application in Keycloak.
 */
@Controller
public class ChangeOidcAppSettings extends BaseController {

  private final KeycloakOidcService keycloakOidcService;
  private final OidcMappingRepository oidcMappingRepository;

  @Inject
  public ChangeOidcAppSettings(
    KeycloakOidcService keycloakOidcService,
    OidcMappingRepository oidcMappingRepository
  ) {
    this.keycloakOidcService = keycloakOidcService;
    this.oidcMappingRepository = oidcMappingRepository;
  }

  @Operation(
    summary = "Change OpenID Connect application settings",
    description = "Updates the name, description and redirect URIs of an existing " +
    "OpenID Connect client application in Keycloak"
  )
  @ApiResponse(
    responseCode = "200",
    description = "OpenID Connect application settings updated",
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
  @Post("/apps/commands/change-oidc-app-settings")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<Void>> changeOidcAppSettings(
    Authentication auth,
    @Body ChangeOidcAppSettingsCommand command
  ) {
    Optional<String> ownerId = getOwnerId(auth);
    if (ownerId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    // Verify that the OpenID Connect application is mapped to the authenticated
    // user before changing its settings.
    return oidcMappingRepository
      .findById(command.id())
      .thenCompose(optionalMapping -> {
        boolean ownedByUser =
          optionalMapping.isPresent() &&
          optionalMapping.get().ownerId().equals(ownerId.get()) &&
          !optionalMapping.get().deregistered();
        if (ownedByUser) {
          return keycloakOidcService
            .changeSettings(
              command.id(),
              command.name(),
              command.description(),
              command.redirectUris()
            )
            .thenApply(ignore -> HttpResponse.ok());
        } else {
          return CompletableFuture.completedFuture(HttpResponse.unauthorized());
        }
      });
  }

  @Serdeable
  public record ChangeOidcAppSettingsCommand(
    String id,
    @Nullable String name,
    @Nullable String description,
    @Nullable List<String> redirectUris
  ) {}
}
