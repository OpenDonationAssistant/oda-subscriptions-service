package io.github.opendonationassistant.repository;

public class Subscription {

  private final SubscriptionData data;

  public Subscription(SubscriptionData data) {
    this.data = data;
  }

  public SubscriptionData data() {
    return data;
  }
}
