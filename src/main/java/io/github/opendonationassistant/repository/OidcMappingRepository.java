package io.github.opendonationassistant.repository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class OidcMappingRepository {

  private final OidcMappingDataRepository repository;

  @Inject
  public OidcMappingRepository(OidcMappingDataRepository repository) {
    this.repository = repository;
  }

  public CompletableFuture<OidcMapping> create(OidcMapping mapping) {
    return CompletableFuture.supplyAsync(() -> repository.save(mapping));
  }

  public CompletableFuture<Optional<OidcMapping>> findById(String id) {
    return CompletableFuture.supplyAsync(() -> repository.findById(id));
  }

  public CompletableFuture<List<OidcMapping>> findByOwnerId(String ownerId) {
    return CompletableFuture.supplyAsync(() ->
      repository.findByOwnerIdAndDeregisteredFalse(ownerId)
    );
  }

  /**
   * Finds a mapping only when it belongs to the given owner and has not been
   * deregistered. Centralises the ownership rule shared by the command
   * controllers so it cannot drift between endpoints.
   */
  public CompletableFuture<Optional<OidcMapping>> findOwnedActive(
    String id,
    String ownerId
  ) {
    return findById(id).thenApply(mapping ->
      mapping.filter(it -> it.ownerId().equals(ownerId) && !it.deregistered())
    );
  }

  public CompletableFuture<Void> markDeregistered(String id) {
    return CompletableFuture.runAsync(() ->
      repository
        .findById(id)
        .ifPresent(mapping ->
          repository.update(new OidcMapping(mapping.id(), mapping.ownerId(), true))
        )
    );
  }
}
