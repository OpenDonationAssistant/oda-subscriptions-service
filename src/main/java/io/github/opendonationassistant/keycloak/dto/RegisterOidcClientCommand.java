package io.github.opendonationassistant.keycloak.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Payload accepted by the OIDC app registration command endpoint.
 *
 * <p>
 *   All flow related boolean flags are nullable objects so that an omitted
 *   value can be distinguished from an explicit {@code false} and sensible
 *   defaults can be applied.
 * </p>
 */
@Serdeable
public record RegisterOidcClientCommand(
  String clientName,
  List<String> redirectUris,
  @Nullable String description,
  @Nullable String clientId,
  @Nullable Boolean standardFlowEnabled,
  @Nullable Boolean implicitFlowEnabled,
  @Nullable Boolean directAccessGrantsEnabled,
  @Nullable Boolean serviceAccountsEnabled
) {

  public RegisterOidcClientCommand {
    if (redirectUris == null) {
      redirectUris = List.of();
    }
    if (standardFlowEnabled == null) {
      standardFlowEnabled = true;
    }
    if (implicitFlowEnabled == null) {
      implicitFlowEnabled = false;
    }
    if (directAccessGrantsEnabled == null) {
      directAccessGrantsEnabled = true;
    }
    if (serviceAccountsEnabled == null) {
      serviceAccountsEnabled = true;
    }
    clientId = normalizeClientId(clientId);
  }

  private static String normalizeClientId(@Nullable String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return clientId;
  }
}
