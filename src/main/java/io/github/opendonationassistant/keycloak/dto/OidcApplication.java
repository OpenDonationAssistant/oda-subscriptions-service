package io.github.opendonationassistant.keycloak.dto;

import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

@Serdeable
public record OidcApplication(
  String id,
  String clientId,
  @Nullable String name,
  @Nullable String description,
  @Nullable String clientSecret
) {}
