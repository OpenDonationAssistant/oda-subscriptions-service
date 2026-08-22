package io.github.opendonationassistant.keycloak.dto;

import io.micronaut.serde.annotation.Serdeable;
@Serdeable
public record OidcClientRegistrationResult(String id) {}
