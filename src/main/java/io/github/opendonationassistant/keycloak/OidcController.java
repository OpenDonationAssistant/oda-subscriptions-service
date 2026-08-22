package io.github.opendonationassistant.keycloak;

import io.github.opendonationassistant.commons.micronaut.BaseController;
import io.github.opendonationassistant.keycloak.dto.OidcApplication;
import io.github.opendonationassistant.keycloak.service.KeycloakOidcService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
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
 * Query controller for OpenID Connect applications owned by the authenticated
 * user.
 */
@Controller
public class OidcController extends BaseController {

  private final KeycloakOidcService keycloakOidcService;

  @Inject
  public OidcController(KeycloakOidcService keycloakOidcService) {
    this.keycloakOidcService = keycloakOidcService;
  }

  @Operation(
    summary = "List OpenID Connect applications",
    description = "Returns all OpenID Connect applications for the authenticated user"
  )
  @ApiResponse(
    responseCode = "200",
    description = "OpenID Connect applications found",
    content = @Content(
      mediaType = "application/json",
      schema = @Schema(implementation = GetAppsResponse.class)
    )
  )
  @ApiResponse(responseCode = "401", description = "Unauthorized")
  @Get("/apps")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public CompletableFuture<HttpResponse<Page<OidcApplication>>> getApps(
    Authentication auth
  ) {
    Optional<String> ownerId = getOwnerId(auth);
    if (ownerId.isEmpty()) {
      return CompletableFuture.completedFuture(HttpResponse.unauthorized());
    }
    return keycloakOidcService
      .listApplications(ownerId.get())
      .thenApply(it ->
        HttpResponse.ok(Page.of(it, Pageable.from(0), (long) it.size()))
      );
  }

  @Serdeable
  public static interface GetAppsResponse extends Page<OidcApplication> {}
}
