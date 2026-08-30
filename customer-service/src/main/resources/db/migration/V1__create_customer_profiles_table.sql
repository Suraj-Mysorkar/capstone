-- Customer Service owns its own table. It is deliberately NOT the "Customers"
-- table used by loan-service / report-service (different key type + columns);
-- on SQL Server's case-insensitive collation "customers" would collide with it.
CREATE TABLE customer_profiles (
    id                          UNIQUEIDENTIFIER   NOT NULL PRIMARY KEY,
    first_name                  NVARCHAR(100)       NOT NULL,
    last_name                   NVARCHAR(100)       NOT NULL,
    email                       NVARCHAR(255)       NOT NULL,
    phone_number                NVARCHAR(20)        NULL,
    address_line1               NVARCHAR(255)       NULL,
    address_line2               NVARCHAR(255)       NULL,
    city                        NVARCHAR(100)       NULL,
    state                       NVARCHAR(50)        NULL,
    postal_code                 NVARCHAR(20)        NULL,
    country_code                NVARCHAR(2)         NULL,
    identity_provider_subject   NVARCHAR(255)       NULL,
    onboarding_status           NVARCHAR(30)        NOT NULL DEFAULT 'REGISTERED',
    version                     BIGINT              NOT NULL DEFAULT 0,
    created_at                  DATETIME2           NOT NULL,
    updated_at                  DATETIME2           NOT NULL
);

CREATE UNIQUE INDEX idx_customer_profiles_email ON customer_profiles(email);
CREATE UNIQUE INDEX idx_customer_profiles_identity_subject ON customer_profiles(identity_provider_subject) WHERE identity_provider_subject IS NOT NULL;
CREATE INDEX idx_customer_profiles_status ON customer_profiles(onboarding_status);
