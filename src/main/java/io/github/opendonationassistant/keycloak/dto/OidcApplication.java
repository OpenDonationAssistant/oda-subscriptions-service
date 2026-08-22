package io.github.opendonationassistant.keycloak.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Summary of an OpenID Connect application owned by a user.
 *
 * <p>
 *   {@code name}, {@code description} and {@code redirectUris} are optional
 *   Keycloak client attributes and may be absent. {@code clientSecret} holds
 *   the last 6 characters of the application's client secret and is absent for
 *   public clients or when Keycloak does not expose the secret.
 * </p>
 */
@Serdeable
public record OidcApplication(
  String id,
  String clientId,
  @Nullable String name,
  @Nullable String description,
  @Nullable String clientSecret,
  @Nullable List<String> redirectUris
) {}
