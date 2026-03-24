package io.github.opendonationassistant.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@MappedEntity("subscriptions")
public record SubscriptionData(
  @Id String id,
  String recipientId,
  String subscriberId,
  List<String> events
) {}
