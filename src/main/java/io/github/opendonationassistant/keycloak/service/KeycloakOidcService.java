package io.github.opendonationassistant.keycloak.service;

import static java.util.Objects.requireNonNull;

import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.keycloak.dto.ClientRepresentation;
import io.github.opendonationassistant.keycloak.dto.OidcClientRegistrationResult;
import io.github.opendonationassistant.keycloak.dto.RegisterOidcClientCommand;
import io.github.opendonationassistant.keycloak.http.KeycloakAdminClient;
import io.github.opendonationassistant.repository.OidcMapping;
import io.github.opendonationassistant.repository.OidcMappingRepository;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

@Singleton
public class KeycloakOidcService {

  private final ODALogger log = new ODALogger(this);

  private final KeycloakAdminClient keycloak;
  private final OidcMappingRepository oidcMappings;
  private final String realm;
  private final String adminRealm;
  private final String adminClientId;
  private final String adminClientSecret;

  @Inject
  public KeycloakOidcService(
    KeycloakAdminClient keycloak,
    OidcMappingRepository oidcMappings,
    @Value("${keycloak.realm}") String realm,
    @Value("${keycloak.admin.realm}") String adminRealm,
    @Value("${keycloak.admin.client-id}") String adminClientId,
    @Value("${keycloak.admin.client-secret}") String adminClientSecret
  ) {
    this.keycloak = keycloak;
    this.oidcMappings = oidcMappings;
    this.realm = realm;
    this.adminRealm = adminRealm;
    this.adminClientId = adminClientId;
    this.adminClientSecret = adminClientSecret;
  }

  /**
   * Registers a new OpenID Connect application in Keycloak. When the client is
   * confidential ({@code publicClient=false}) and a secret was requested, a
   * client secret is generated and returned.
   */
  public CompletableFuture<OidcClientRegistrationResult> register(
    RegisterOidcClientCommand command,
    String ownerId
  ) {
    return getAdminAccessToken()
      .thenCompose(token -> registerWithToken(token, command, ownerId))
      .exceptionally(this::rethrow);
  }

  private CompletableFuture<OidcClientRegistrationResult> registerWithToken(
    String token,
    RegisterOidcClientCommand command,
    String ownerId
  ) {
    return createClient(token, command).thenCompose(client -> {
      String clientInternalId = requireNonNull(
        client.id(),
        "Keycloak returned no client internal id"
      );
      boolean confidential = !client.publicClient();
      CompletableFuture<@Nullable String> secret;
      if (confidential) {
        log.debug(
          "Generating client secret",
          Map.of("clientId", String.valueOf(client.clientId()))
        );
        secret = keycloak
          .regenerateClientSecret("Bearer " + token, realm, clientInternalId)
          .thenApply(KeycloakAdminClient.ClientSecretResponse::value);
      } else {
        secret = CompletableFuture.completedFuture(null);
      }
      return secret
        .thenApply(value ->
          new OidcClientRegistrationResult(
            requireNonNull(client.clientId(), "Keycloak returned no client id"),
            clientInternalId,
            realm,
            value
          )
        )
        .thenCompose(result ->
          oidcMappings
            .create(new OidcMapping(result.clientInternalId(), ownerId, false))
            .thenApply(ignored -> result)
        );
    });
  }

  /**
   * Deregisters (deletes) an existing OpenID Connect application from Keycloak
   * by its internal UUID.
   */
  public CompletableFuture<Void> deregister(String clientInternalId) {
    return getAdminAccessToken()
      .thenCompose(token -> {
        log.debug(
          "Deleting OpenID Connect client",
          Map.of("clientInternalId", clientInternalId, "realm", realm)
        );
        return keycloak.deleteClient(
          "Bearer " + token,
          realm,
          clientInternalId
        );
      })
      .thenCompose(ignored -> oidcMappings.markDeregistered(clientInternalId))
      .exceptionally(this::rethrowVoid);
  }

  /**
   * Regenerates the client secret of an existing OpenID Connect application
   * identified by its internal UUID and returns the new secret.
   */
  public CompletableFuture<KeycloakAdminClient.ClientSecretResponse> refreshClientSecret(
    String clientInternalId
  ) {
    return getAdminAccessToken()
      .thenCompose(token -> {
        log.debug(
          "Refreshing client secret",
          Map.of("clientInternalId", clientInternalId, "realm", realm)
        );
        return keycloak.regenerateClientSecret(
          "Bearer " + token,
          realm,
          clientInternalId
        );
      })
      .exceptionally(this::rethrowVoid);
  }

  private <T> T rethrowVoid(Throwable error) {
    log.error(
      "Failed to deregister OpenID Connect application",
      errorToException(error)
    );
    if (error instanceof CompletionException completionError) {
      throw completionError;
    }
    throw new CompletionException(error);
  }

  private CompletableFuture<String> getAdminAccessToken() {
    if (adminClientId.isEmpty()) {
      return CompletableFuture.failedFuture(
        new IllegalStateException(
          "Keycloak admin client id is not configured. " +
          "Set the KEYCLOAK_ADMIN_CLIENT_ID environment variable."
        )
      );
    }
    if (adminClientSecret.isEmpty()) {
      return CompletableFuture.failedFuture(
        new IllegalStateException(
          "Keycloak admin client secret is not configured. " +
          "Set the KEYCLOAK_ADMIN_CLIENT_SECRET environment variable."
        )
      );
    }
    if (adminRealm.isEmpty()) {
      return CompletableFuture.failedFuture(
        new IllegalStateException(
          "Keycloak admin realm is not configured. " +
          "Set the KEYCLOAK_ADMIN_REALM environment variable."
        )
      );
    }
    Map<String, String> params = Map.of(
      "grant_type",
      "client_credentials",
      "client_id",
      adminClientId,
      "client_secret",
      adminClientSecret
    );
    return keycloak
      .getAccessToken(adminRealm, params)
      .thenApply(response ->
        requireNonNull(
          response.accessToken(),
          "Keycloak returned no access token"
        )
      );
  }

  private CompletableFuture<ClientRepresentation> createClient(
    String token,
    RegisterOidcClientCommand command
  ) {
    var representation = new ClientRepresentation(
      null,
      command.clientId(),
      command.clientName(),
      command.description(),
      "openid-connect",
      false,
      Boolean.TRUE.equals(command.standardFlowEnabled()),
      Boolean.TRUE.equals(command.implicitFlowEnabled()),
      Boolean.TRUE.equals(command.directAccessGrantsEnabled()),
      Boolean.TRUE.equals(command.serviceAccountsEnabled()),
      List.copyOf(command.redirectUris()),
      null,
      null
    );
    log.debug(
      "Creating OpenID Connect client",
      Map.of(
        "clientId",
        String.valueOf(representation.clientId()),
        "realm",
        realm
      )
    );
    return keycloak.createClient("Bearer " + token, realm, representation);
  }

  private <T> T rethrow(Throwable error) {
    log.error(
      "Failed to register OpenID Connect application",
      errorToException(error)
    );
    if (error instanceof CompletionException completionError) {
      throw completionError;
    }
    throw new CompletionException(error);
  }

  private Exception errorToException(Throwable error) {
    return error instanceof Exception e ? e : new IllegalStateException(error);
  }
}

