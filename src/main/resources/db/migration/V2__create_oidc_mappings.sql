-- Create oidc_mappings table
CREATE TABLE oidc (
    id VARCHAR(255) PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    deregistered BOOLEAN NOT NULL DEFAULT FALSE
);
