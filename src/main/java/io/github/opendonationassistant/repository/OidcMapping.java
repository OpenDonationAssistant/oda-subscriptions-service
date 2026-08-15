package io.github.opendonationassistant.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("oidc")
public record OidcMapping(
  @Id String id,
  String ownerId,
  boolean deregistered
) {}
