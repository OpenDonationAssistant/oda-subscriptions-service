package io.github.opendonationassistant.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * <p>
 *   Subset of Keycloak's {@code ClientRepresentation} that is relevant for
 *   registering a new OpenID Connect application. It is both deserialized when
 *   a client is created (to capture the auto generated internal id) and used as
 *   the request payload when creating a client.
 * </p>
 *
 * <p>
 *   Keycloak assigns a random {@code secret} on creation for confidential
 *   clients ({@code publicClient=false}); it can be read back via the
 *   {@code client-secret} admin endpoint.
 * </p>
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientRepresentation(
  @Nullable @JsonProperty("id") String id,
  @Nullable @JsonProperty("clientId") String clientId,
  @Nullable @JsonProperty("name") String name,
  @Nullable @JsonProperty("description") String description,
  @JsonProperty("protocol") String protocol,
  @JsonProperty("publicClient") boolean publicClient,
  @JsonProperty("standardFlowEnabled") boolean standardFlowEnabled,
  @JsonProperty("implicitFlowEnabled") boolean implicitFlowEnabled,
  @JsonProperty("directAccessGrantsEnabled") boolean directAccessGrantsEnabled,
  @JsonProperty("serviceAccountsEnabled") boolean serviceAccountsEnabled,
  @Nullable @JsonProperty("redirectUris") List<String> redirectUris,
  @Nullable @JsonProperty("webOrigins") List<String> webOrigins,
  @Nullable @JsonProperty("secret") String secret
) {
  public ClientRepresentation {
    protocol = protocol == null ? "openid-connect" : protocol;
  }
}
