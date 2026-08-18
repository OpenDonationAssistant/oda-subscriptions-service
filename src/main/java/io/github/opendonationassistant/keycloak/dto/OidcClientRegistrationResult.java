package io.github.opendonationassistant.keycloak.dto;

import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

/**
 * Result of a successful OpenID Connect application registration.
 *
 * <p>
 *   {@code clientSecret} is only populated when the registered application is
 *   confidential and a secret was requested/generated.
 * </p>
 */
@Serdeable
public record OidcClientRegistrationResult(
  String clientId,
  String clientInternalId,
  String realm,
  @Nullable String clientSecret
) {}
