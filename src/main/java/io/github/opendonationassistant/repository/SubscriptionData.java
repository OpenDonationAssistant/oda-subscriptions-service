package io.github.opendonationassistant.repository;

import io.github.opendonationassistant.commons.StringListConverter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@MappedEntity("subscriptions")
public record SubscriptionData(
  @Id String id,
  String recipientId,
  String subscriberId,
  @MappedProperty(converter = StringListConverter.class) List<String> events
) {}
