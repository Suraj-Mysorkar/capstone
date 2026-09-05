package com.bank.customerservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures required table columns and tables exist in Azure SQL Database on startup.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[DB-SCHEMA-INIT] Verifying database schema compatibility...");

        try {
            // 1. Ensure customer_id column in users table
            jdbcTemplate.execute("""
                IF EXISTS (SELECT * FROM sys.tables WHERE name = 'users')
                BEGIN
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[users]') AND name = 'customer_id')
                    BEGIN
                        ALTER TABLE [dbo].[users] ADD [customer_id] UNIQUEIDENTIFIER NULL;
                    END
                END
            """);
            log.info("[DB-SCHEMA-INIT] ✅ 'users' table schema verified (customer_id column present).");
        } catch (Exception ex) {
            log.warn("[DB-SCHEMA-INIT] Could not update 'users' schema: {}", ex.getMessage());
        }

        try {
            // 2. Ensure all unified columns in customers table
            jdbcTemplate.execute("""
                IF EXISTS (SELECT * FROM sys.tables WHERE name = 'customers')
                BEGIN
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'id')
                    BEGIN
                        IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'customer_id')
                            EXEC sp_rename 'dbo.customers.customer_id', 'id', 'COLUMN';
                        ELSE
                            ALTER TABLE [dbo].[customers] ADD [id] UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID();
                    END

                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'first_name')
                        ALTER TABLE [dbo].[customers] ADD [first_name] NVARCHAR(100) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'last_name')
                        ALTER TABLE [dbo].[customers] ADD [last_name] NVARCHAR(100) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'email')
                        ALTER TABLE [dbo].[customers] ADD [email] NVARCHAR(255) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'phone_number')
                        ALTER TABLE [dbo].[customers] ADD [phone_number] NVARCHAR(20) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'address_line1')
                        ALTER TABLE [dbo].[customers] ADD [address_line1] NVARCHAR(255) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'address_line2')
                        ALTER TABLE [dbo].[customers] ADD [address_line2] NVARCHAR(255) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'city')
                        ALTER TABLE [dbo].[customers] ADD [city] NVARCHAR(100) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'state')
                        ALTER TABLE [dbo].[customers] ADD [state] NVARCHAR(50) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'postal_code')
                        ALTER TABLE [dbo].[customers] ADD [postal_code] NVARCHAR(20) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'country_code')
                        ALTER TABLE [dbo].[customers] ADD [country_code] NVARCHAR(2) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'identity_provider_subject')
                        ALTER TABLE [dbo].[customers] ADD [identity_provider_subject] NVARCHAR(255) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'DOB')
                        ALTER TABLE [dbo].[customers] ADD [DOB] DATE NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'National_ID')
                        ALTER TABLE [dbo].[customers] ADD [National_ID] NVARCHAR(50) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'Employment_Details')
                        ALTER TABLE [dbo].[customers] ADD [Employment_Details] NVARCHAR(255) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'Income_Details')
                        ALTER TABLE [dbo].[customers] ADD [Income_Details] DECIMAL(18,2) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'onboarding_status')
                        ALTER TABLE [dbo].[customers] ADD [onboarding_status] NVARCHAR(30) NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'version')
                        ALTER TABLE [dbo].[customers] ADD [version] BIGINT NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'created_at')
                        ALTER TABLE [dbo].[customers] ADD [created_at] DATETIME2 NULL;
                    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[customers]') AND name = 'updated_at')
                        ALTER TABLE [dbo].[customers] ADD [updated_at] DATETIME2 NULL;
                END
            """);
            log.info("[DB-SCHEMA-INIT] ✅ 'customers' table schema verified with all columns.");
        } catch (Exception ex) {
            log.warn("[DB-SCHEMA-INIT] Could not update 'Customers' schema: {}", ex.getMessage());
        }
    }
}
