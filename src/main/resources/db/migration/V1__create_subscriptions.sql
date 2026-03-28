-- Create subscriptions table
CREATE TABLE subscriptions (
    id VARCHAR(255) PRIMARY KEY,
    recipient_id VARCHAR(255) NOT NULL,
    subscriber_id VARCHAR(255) NOT NULL,
    events TEXT NOT NULL DEFAULT '[]'
);
