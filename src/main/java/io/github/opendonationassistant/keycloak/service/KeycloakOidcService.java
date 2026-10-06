package io.github.opendonationassistant.keycloak.service;

import static java.util.Objects.requireNonNull;

import io.github.opendonationassistant.commons.logging.ODALogger;
import io.github.opendonationassistant.keycloak.dto.ClientRepresentation;
import io.github.opendonationassistant.keycloak.dto.OidcApplication;
import io.github.opendonationassistant.keycloak.dto.OidcClientRegistrationResult;
import io.github.opendonationassistant.keycloak.dto.RegisterOidcClientCommand;
import io.github.opendonationassistant.keycloak.http.KeycloakAdminClient;
import io.github.opendonationassistant.repository.OidcMapping;
import io.github.opendonationassistant.repository.OidcMappingRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.zalando.problem.ProblemBuilder;

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
      .exceptionally(error -> fail(error, "register OpenID Connect application")
      );
  }

  private CompletableFuture<OidcClientRegistrationResult> registerWithToken(
    String token,
    RegisterOidcClientCommand command,
    String ownerId
  ) {
    final var clientId = command.clientId();
    if (clientId == null || clientId.isBlank()) {
      return CompletableFuture.failedFuture(
        new RuntimeException("Missing clientId")
      );
    }
    return createClient(token, command).thenCompose(response -> {
      String clientInternalId = KeycloakAdminClient.parseClientInternalId(
        response
      );
      log.debug(
        "Generating client secret",
        Map.of("clientId", clientId, "clientInternalId", clientInternalId)
      );
      return addStompDefaultScope(token, clientInternalId)
        .thenCompose(_ ->
          oidcMappings.create(new OidcMapping(clientInternalId, ownerId, false))
        )
        .thenApply(_ -> new OidcClientRegistrationResult(clientInternalId));
    });
  }

  private static final String STOMP_CLIENT_SCOPE = "stomp";

  private CompletableFuture<Void> addStompDefaultScope(
    String token,
    String clientInternalId
  ) {
    return keycloak
      .getClientScopes("Bearer " + token, realm)
      .thenCompose(scopes -> {
        var stompScope = scopes
          .stream()
          .filter(scope -> STOMP_CLIENT_SCOPE.equals(scope.name()))
          .findFirst()
          .orElseThrow(() ->
            new IllegalStateException(
              "Keycloak client scope '" +
              STOMP_CLIENT_SCOPE +
              "' not found in realm " +
              realm
            )
          );
        log.debug(
          "Adding default client scope",
          Map.of(
            "clientInternalId",
            clientInternalId,
            "scope",
            STOMP_CLIENT_SCOPE
          )
        );
        return keycloak.addDefaultClientScope(
          "Bearer " + token,
          realm,
          clientInternalId,
          stompScope.id()
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
      .exceptionally(error ->
        fail(error, "deregister OpenID Connect application")
      );
  }

  /**
   * Regenerates the client secret of an existing OpenID Connect application
   * identified by its internal UUID and returns the new secret.
   */
  public CompletableFuture<
    KeycloakAdminClient.ClientSecretResponse
  > refreshClientSecret(String clientInternalId) {
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
      .exceptionally(error ->
        fail(error, "refresh OpenID Connect client secret")
      );
  }

  /**
   * Updates the settings of an existing OpenID Connect application: name,
   * description and redirect URIs. Fields that are null are left unchanged.
   */
  public CompletableFuture<Void> changeSettings(
    String clientInternalId,
    @Nullable String name,
    @Nullable String description,
    @Nullable List<String> redirectUris
  ) {
    return getAdminAccessToken()
      .thenCompose(token ->
        keycloak
          .getClient("Bearer " + token, realm, clientInternalId)
          .thenApply(client ->
            applySettingsChanges(client, name, description, redirectUris)
          )
          .thenCompose(updated ->
            keycloak.updateClient(
              "Bearer " + token,
              realm,
              clientInternalId,
              updated
            )
          )
      )
      .exceptionally(error ->
        fail(error, "update OpenID Connect application settings")
      );
  }

  private ClientRepresentation applySettingsChanges(
    ClientRepresentation current,
    @Nullable String name,
    @Nullable String description,
    @Nullable List<String> redirectUris
  ) {
    return new ClientRepresentation(
      current.id(),
      current.clientId(),
      name != null ? name : current.name(),
      description != null ? description : current.description(),
      current.protocol(),
      current.publicClient(),
      current.standardFlowEnabled(),
      current.implicitFlowEnabled(),
      current.directAccessGrantsEnabled(),
      current.serviceAccountsEnabled(),
      redirectUris != null ? List.copyOf(redirectUris) : current.redirectUris(),
      current.webOrigins(),
      current.secret()
    );
  }

  /**
   * Lists the OpenID Connect applications owned by the given user. The
   * ownership mappings are stored locally, while the application details are
   * fetched from Keycloak. Deregistered applications are excluded.
   */
  public CompletableFuture<List<OidcApplication>> listApplications(
    String ownerId
  ) {
    return getAdminAccessToken()
      .thenCompose(token ->
        oidcMappings
          .findByOwnerId(ownerId)
          .thenCompose(mappings -> fetchApplications(token, mappings))
      )
      .exceptionally(error -> fail(error, "list OpenID Connect applications"));
  }

  private CompletableFuture<List<OidcApplication>> fetchApplications(
    String token,
    List<OidcMapping> mappings
  ) {
    // Each mapping is resolved independently: a single broken or deleted
    // client must not fail the whole listing for the owner.
    List<CompletableFuture<Optional<OidcApplication>>> futures = mappings
      .stream()
      .map(mapping ->
        fetchApplication(token, mapping)
          .thenApply(Optional::of)
          .exceptionally(error -> {
            log.warn(
              "Skipping unavailable OpenID Connect application",
              Map.of(
                "clientInternalId",
                mapping.id(),
                "error",
                String.valueOf(error.getMessage())
              )
            );
            return Optional.empty();
          })
      )
      .toList();
    return CompletableFuture.allOf(
      futures.toArray(CompletableFuture[]::new)
    ).thenApply(ignored ->
      futures
        .stream()
        .map(CompletableFuture::join)
        .flatMap(Optional::stream)
        .toList()
    );
  }

  private CompletableFuture<OidcApplication> fetchApplication(
    String token,
    OidcMapping mapping
  ) {
    return keycloak
      .getClient("Bearer " + token, realm, mapping.id())
      .thenCompose(client ->
        fetchSecretSuffix(token, mapping.id()).thenApply(secret ->
          new OidcApplication(
            requireNonNull(client.clientId(), "Keycloak returned no client id"),
            requireNonNull(
              client.id(),
              "Keycloak returned no client internal id"
            ),
            client.name(),
            client.description(),
            secret.isPresent() ? secret.get() : null,
            client.redirectUris()
          )
        )
      );
  }

  /**
   * Reads the client secret from Keycloak's dedicated {@code client-secret}
   * endpoint (the client representation no longer carries it) and reduces it
   * to its suffix. Public clients, or clients the admin account may not read,
   * simply yield {@code null}.
   */
  private CompletableFuture<Optional<String>> fetchSecretSuffix(
    String token,
    String clientInternalId
  ) {
    return keycloak
      .getClientSecret("Bearer " + token, realm, clientInternalId)
      .thenApply(response -> Optional.ofNullable(secretSuffix(response.value()))
      )
      .exceptionally(error -> {
        log.debug(
          "Client secret is not available",
          Map.of("clientInternalId", clientInternalId)
        );
        return Optional.empty();
      });
  }

  private static final int SECRET_SUFFIX_LENGTH = 6;

  /**
   * Keycloak returns this placeholder instead of the secret when the caller
   * lacks the {@code manage-clients} permission. It must never be exposed as
   * if it were a real secret suffix.
   */
  private static final String MASKED_SECRET = "**********";

  @Nullable
  private static String secretSuffix(@Nullable String secret) {
    if (secret == null || secret.isBlank() || MASKED_SECRET.equals(secret)) {
      return null;
    }
    return secret.substring(
      Math.max(0, secret.length() - SECRET_SUFFIX_LENGTH)
    );
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

  private CompletableFuture<HttpResponse<Void>> createClient(
    String token,
    RegisterOidcClientCommand command
  ) {
    @Nullable
    List<String> uris = Optional.ofNullable(command.redirectUris())
      .map(it ->
        it.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toList())
      )
      .orElse(null);
    var representation = new ClientRepresentation(
      command.clientId(),
      command.clientId(),
      command.clientName(),
      command.description(),
      "openid-connect",
      false,
      Boolean.TRUE.equals(command.standardFlowEnabled()),
      Boolean.TRUE.equals(command.implicitFlowEnabled()),
      Boolean.TRUE.equals(command.directAccessGrantsEnabled()),
      Boolean.TRUE.equals(command.serviceAccountsEnabled()),
      uris,
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

  private <T> T fail(Throwable error, String operation) {
    log.error("Failed to " + operation, errorToException(error));
    if (error instanceof CompletionException completionError) {
      throw completionError;
    }
    throw new CompletionException(error);
  }

  private Exception errorToException(Throwable error) {
    return error instanceof Exception e ? e : new IllegalStateException(error);
  }
}
