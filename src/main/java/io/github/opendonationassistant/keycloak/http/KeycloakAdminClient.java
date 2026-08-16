package io.github.opendonationassistant.keycloak.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.opendonationassistant.keycloak.dto.ClientRepresentation;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Declarative Micronaut HTTP client for the Keycloak Admin REST API.
 *
 * <p>
 *   The base URL is resolved from the {@code keycloak.url} configuration
 *   property. Every admin operation is authenticated with a bearer token
 *   obtained from the token endpoint first.
 * </p>
 */
@Client("${keycloak.url}")
public interface KeycloakAdminClient {

  /**
   * Fetches an OAuth2 access token (client_credentials grant) used to
   * authenticate subsequent admin requests.
   */
  @Post(
    value = "/realms/{realm}/protocol/openid-connect/token",
    consumes = MediaType.APPLICATION_FORM_URLENCODED,
    produces = MediaType.APPLICATION_JSON
  )
  CompletableFuture<AccessTokenResponse> getAccessToken(
    @PathVariable("realm") String realm,
    @Body Map<String, String> params
  );

  /** Creates a new client in the given realm. */
  @Post(
    value = "/admin/realms/{realm}/clients",
    consumes = MediaType.APPLICATION_JSON
  )
  CompletableFuture<ClientRepresentation> createClient(
    @Header("Authorization") String bearer,
    @PathVariable("realm") String realm,
    @Body ClientRepresentation client
  );

  /**
   * Regenerates (or on first call simply returns) the secret of an existing
   * confidential client.
   */
  @Post(
    value = "/admin/realms/{realm}/clients/{clientId}/client-secret",
    consumes = MediaType.APPLICATION_JSON
  )
  CompletableFuture<ClientSecretResponse> regenerateClientSecret(
    @Header("Authorization") String bearer,
    @PathVariable("realm") String realm,
    @PathVariable("clientId") String clientId
  );

  /** Returns the current (auto generated) secret of an existing client. */
  @Get(value = "/admin/realms/{realm}/clients/{clientId}/client-secret")
  CompletableFuture<ClientSecretResponse> getClientSecret(
    @Header("Authorization") String bearer,
    @PathVariable("realm") String realm,
    @PathVariable("clientId") String clientId
  );

  /**
   * Returns a single client by its internal UUID.
   */
  @Get(value = "/admin/realms/{realm}/clients/{clientUuid}")
  CompletableFuture<ClientRepresentation> getClient(
    @Header("Authorization") String bearer,
    @PathVariable("realm") String realm,
    @PathVariable("clientUuid") String clientUuid
  );

  /** Deletes an existing client by its internal UUID. */
  @Delete("/admin/realms/{realm}/clients/{clientId}")
  CompletableFuture<Void> deleteClient(
    @Header("Authorization") String bearer,
    @PathVariable("realm") String realm,
    @PathVariable("clientId") String clientId
  );

  @Serdeable
  record AccessTokenResponse(
    @JsonProperty("access_token") String accessToken
  ) {}

  @Serdeable
  record ClientSecretResponse(
    @JsonProperty("type") String type,
    @JsonProperty("value") String value
  ) {}
}