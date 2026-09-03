-- Loan manager assignment ledger, owned by customer-service.
--
-- When a customer applies for a loan, one of the loan managers in the shared
-- `Users` table (user_role = 'manager', seeded at startup by LoanManagerSeeder)
-- is assigned to them and the customer is notified via the notification service.
-- This table records each assignment.
--
-- Guarded so it is a no-op if the table already exists. Only runs where Flyway
-- is enabled (the default / dev / prod / azuredb profiles — all Azure SQL);
-- under the `local` profile Hibernate ddl-auto builds it from the entity.

IF OBJECT_ID(N'dbo.loan_manager_assignments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.loan_manager_assignments (
        id              UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_loan_manager_assignments PRIMARY KEY,
        customer_id     UNIQUEIDENTIFIER NULL,
        customer_email  NVARCHAR(255)    NULL,
        customer_name   NVARCHAR(200)    NULL,
        application_id  NVARCHAR(64)     NULL,
        loan_type       NVARCHAR(40)     NULL,
        loan_amount     DECIMAL(18, 2)   NULL,
        manager_user_id BIGINT           NULL,
        manager_login   NVARCHAR(20)     NOT NULL,
        manager_name    NVARCHAR(150)    NULL,
        manager_email   NVARCHAR(255)    NULL,
        notified        BIT              NOT NULL CONSTRAINT DF_lma_notified DEFAULT 0,
        assigned_at     DATETIME2        NOT NULL CONSTRAINT DF_lma_assigned_at DEFAULT SYSUTCDATETIME()
    );

    CREATE INDEX idx_lma_customer_id ON dbo.loan_manager_assignments(customer_id);
    CREATE INDEX idx_lma_application_id ON dbo.loan_manager_assignments(application_id);
END
