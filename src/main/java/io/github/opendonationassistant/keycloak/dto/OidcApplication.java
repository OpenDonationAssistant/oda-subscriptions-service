package io.github.opendonationassistant.keycloak.dto;

import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

/**
 * Summary of an OpenID Connect application owned by a user.
 *
 * <p>
 *   {@code name} and {@code description} are optional Keycloak client
 *   attributes and may be absent.
 * </p>
 */
@Serdeable
public record OidcApplication(
  String clientId,
  String clientInternalId,
  @Nullable String name,
  @Nullable String description
) {}
