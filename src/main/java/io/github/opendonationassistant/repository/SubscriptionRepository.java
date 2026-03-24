package io.github.opendonationassistant.repository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class SubscriptionRepository {

  private final SubscriptionDataRepository repository;

  @Inject
  public SubscriptionRepository(SubscriptionDataRepository repository) {
    this.repository = repository;
  }

  public CompletableFuture<Subscription> create(SubscriptionData data) {
    return CompletableFuture.supplyAsync(() -> convert(repository.save(data)));
  }

  private Subscription convert(SubscriptionData data) {
    return new Subscription(data);
  }
}
