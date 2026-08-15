package io.github.opendonationassistant.keycloak.command;

import io.github.opendonationassistant.commons.micronaut.BaseController;
import io.github.opendonationassistant.keycloak.dto.OidcClientRegistrationResult;
import io.github.opendonationassistant.keycloak.dto.RegisterOidcClientCommand;
import io.github.opendonationassistant.keycloak.service.KeycloakOidcService;
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

/**
 * Command controller for registering an OpenID Connect application in Keycloak.
 */
@Controller("/keycloak")
public class KeycloakOidcController extends BaseController {

  private final KeycloakOidcService keycloakOidcService;

  @Inject
  public KeycloakOidcController(KeycloakOidcService keycloakOidcService) {
    this.keycloakOidcService = keycloakOidcService;
  }

  @Operation(
    summary = "Register OpenID Connect application",
    description = "Creates a new OpenID Connect client application in Keycloak and, generates its client secret"
  )
  @ApiResponse(
    responseCode = "200",
    description = "OpenID Connect application registered",
    content = @Content(
      mediaType = "application/json",
      schema = @Schema(implementation = OidcClientRegistrationResult.class)
    )
  )
  @ApiResponse(
    responseCode = "401",
    description = "Unauthorized",
    content = @Content
  )
  @Post("/commands/register-oidc-client")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<
    HttpResponse<OidcClientRegistrationResult>
  > registerOidcApplication(
    Authentication auth,
    @Body RegisterOidcClientCommand command
  ) {
    Optional<String> ownerId = getOwnerId(auth);
    if (ownerId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    return keycloakOidcService
      .register(command, ownerId.get())
      .thenApply(HttpResponse::ok);
  }
}

