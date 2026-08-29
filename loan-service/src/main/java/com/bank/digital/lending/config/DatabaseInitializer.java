package com.bank.digital.lending.config;

import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.repository.LoanSchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

@Component
@Order(1)
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final LoanSchemeRepository schemeRepository;

    public DatabaseInitializer(DataSource dataSource, LoanSchemeRepository schemeRepository) {
        this.dataSource = dataSource;
        this.schemeRepository = schemeRepository;
    }

    @Override
    public void run(String... args) {
        ensureSchemaExists();
        seedSchemes();
    }

    private void ensureSchemaExists() {
        log.info("Checking and ensuring MSSQL schema objects exist...");
        String[] ddlStatements = {
                // 0. Drop legacy Loan_Applications table if Application_ID was created as bigint/numeric
                """
                IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME LIKE '%loan_applications%' AND COLUMN_NAME LIKE '%application_id%' AND DATA_TYPE IN ('bigint', 'int', 'numeric', 'decimal'))
                BEGIN
                    IF EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_Documents_LoanApplication')
                        ALTER TABLE Documents DROP CONSTRAINT FK_Documents_LoanApplication;

                    IF EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_LoanApplications_Customers')
                        ALTER TABLE Loan_Applications DROP CONSTRAINT FK_LoanApplications_Customers;

                    -- Drop any other FK on or referencing loan_applications
                    DECLARE @drop_fks NVARCHAR(MAX) = N'';
                    SELECT @drop_fks += N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(parent_object_id))
                        + '.' + QUOTENAME(OBJECT_NAME(parent_object_id)) 
                        + ' DROP CONSTRAINT ' + QUOTENAME(name) + ';'
                    FROM sys.foreign_keys
                    WHERE referenced_object_id = OBJECT_ID('Loan_Applications')
                       OR parent_object_id = OBJECT_ID('Loan_Applications')
                       OR referenced_object_id = OBJECT_ID('LOAN_APPLICATIONS')
                       OR parent_object_id = OBJECT_ID('LOAN_APPLICATIONS');

                    IF @drop_fks <> N'' EXEC sp_executesql @drop_fks;

                    DROP TABLE IF EXISTS Loan_Applications;
                    DROP TABLE IF EXISTS LOAN_APPLICATIONS;
                END
                """,

                // 1. LOAN_SCHEMES
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'LOAN_SCHEMES' AND type = 'U')
                BEGIN
                    CREATE TABLE LOAN_SCHEMES (
                        SCHEME_ID VARCHAR(36) NOT NULL,
                        LOAN_TYPE VARCHAR(30) NOT NULL,
                        SCHEME_NAME VARCHAR(100) NOT NULL,
                        MIN_AMOUNT DECIMAL(18, 2) NOT NULL,
                        MAX_AMOUNT DECIMAL(18, 2) NOT NULL,
                        MIN_TENURE_MONTHS INT NOT NULL,
                        MAX_TENURE_MONTHS INT NOT NULL,
                        BASE_INTEREST_RATE DECIMAL(5, 2) NOT NULL,
                        IS_ACTIVE BIT NOT NULL CONSTRAINT DF_LOAN_SCHEMES_IS_ACTIVE DEFAULT 1,
                        CONSTRAINT PK_LOAN_SCHEMES PRIMARY KEY (SCHEME_ID)
                    );
                END
                """,

                // 2. LOAN_APPLICATIONS
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'LOAN_APPLICATIONS' AND type = 'U')
                BEGIN
                    CREATE TABLE LOAN_APPLICATIONS (
                        APPLICATION_ID VARCHAR(36) NOT NULL,
                        CUSTOMER_ID VARCHAR(36) NOT NULL,
                        CUSTOMER_NAME VARCHAR(100) NOT NULL,
                        CUSTOMER_EMAIL VARCHAR(100) NOT NULL,
                        CUSTOMER_PHONE VARCHAR(20) NOT NULL,
                        MONTHLY_INCOME DECIMAL(18, 2) NOT NULL,
                        EXISTING_LIABILITIES DECIMAL(18, 2) CONSTRAINT DF_LOAN_APPS_EXISTING_LIAB DEFAULT 0.00,
                        EMPLOYMENT_TYPE VARCHAR(50) NOT NULL,
                        SCHEME_ID VARCHAR(36) NOT NULL,
                        LOAN_TYPE VARCHAR(30) NOT NULL,
                        LOAN_AMOUNT DECIMAL(18, 2) NOT NULL,
                        DOCUMENT_PROVIDED BIT NOT NULL CONSTRAINT DF_LOAN_APPS_DOC_PROVIDED DEFAULT 0,
                        TENURE_MONTHS INT NOT NULL,
                        INTEREST_RATE DECIMAL(5, 2) NOT NULL,
                        CALCULATED_EMI DECIMAL(18, 2) NOT NULL,
                        STATUS VARCHAR(30) NOT NULL,
                        RISK_SCORE INT NULL,
                        DTI_RATIO DECIMAL(5, 2) NULL,
                        ORCHESTRATION_INSTANCE_ID VARCHAR(100) NULL,
                        ASSIGNED_MANAGER VARCHAR(100) NULL,
                        DECISION_REMARKS VARCHAR(1000) NULL,
                        CREATED_AT DATETIME2 NOT NULL CONSTRAINT DF_LOAN_APPS_CREATED_AT DEFAULT SYSUTCDATETIME(),
                        UPDATED_AT DATETIME2 NOT NULL CONSTRAINT DF_LOAN_APPS_UPDATED_AT DEFAULT SYSUTCDATETIME(),
                        CREATED_BY VARCHAR(100) NOT NULL CONSTRAINT DF_LOAN_APPS_CREATED_BY DEFAULT 'SYSTEM',
                        CREATED_DATE DATETIME2 NOT NULL CONSTRAINT DF_LOAN_APPS_CREATED_DATE DEFAULT SYSUTCDATETIME(),
                        LAST_MODIFIED_BY VARCHAR(100) NULL,
                        LAST_MODIFIED_DATE DATETIME2 NULL,
                        CONSTRAINT PK_LOAN_APPLICATIONS PRIMARY KEY (APPLICATION_ID)
                    );
                END
                """,

                // 2b. Add missing columns to LOAN_APPLICATIONS if already existing
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'SCHEME_ID') ALTER TABLE LOAN_APPLICATIONS ADD SCHEME_ID VARCHAR(36) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'CUSTOMER_NAME') ALTER TABLE LOAN_APPLICATIONS ADD CUSTOMER_NAME VARCHAR(100) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'CUSTOMER_EMAIL') ALTER TABLE LOAN_APPLICATIONS ADD CUSTOMER_EMAIL VARCHAR(100) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'CUSTOMER_PHONE') ALTER TABLE LOAN_APPLICATIONS ADD CUSTOMER_PHONE VARCHAR(20) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'MONTHLY_INCOME') ALTER TABLE LOAN_APPLICATIONS ADD MONTHLY_INCOME DECIMAL(18, 2) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'EXISTING_LIABILITIES') ALTER TABLE LOAN_APPLICATIONS ADD EXISTING_LIABILITIES DECIMAL(18, 2) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'EMPLOYMENT_TYPE') ALTER TABLE LOAN_APPLICATIONS ADD EMPLOYMENT_TYPE VARCHAR(50) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'DOCUMENT_PROVIDED') ALTER TABLE LOAN_APPLICATIONS ADD DOCUMENT_PROVIDED BIT NOT NULL CONSTRAINT DF_LOAN_APPS_DOC_PROV_AUTO DEFAULT 0",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'CALCULATED_EMI') ALTER TABLE LOAN_APPLICATIONS ADD CALCULATED_EMI DECIMAL(18, 2) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'RISK_SCORE') ALTER TABLE LOAN_APPLICATIONS ADD RISK_SCORE INT NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'DTI_RATIO') ALTER TABLE LOAN_APPLICATIONS ADD DTI_RATIO DECIMAL(5, 2) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'ORCHESTRATION_INSTANCE_ID') ALTER TABLE LOAN_APPLICATIONS ADD ORCHESTRATION_INSTANCE_ID VARCHAR(100) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'ASSIGNED_MANAGER') ALTER TABLE LOAN_APPLICATIONS ADD ASSIGNED_MANAGER VARCHAR(100) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'DECISION_REMARKS') ALTER TABLE LOAN_APPLICATIONS ADD DECISION_REMARKS VARCHAR(1000) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'CREATED_AT') ALTER TABLE LOAN_APPLICATIONS ADD CREATED_AT DATETIME2 NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'UPDATED_AT') ALTER TABLE LOAN_APPLICATIONS ADD UPDATED_AT DATETIME2 NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'CREATED_BY') ALTER TABLE LOAN_APPLICATIONS ADD CREATED_BY VARCHAR(100) NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'CREATED_DATE') ALTER TABLE LOAN_APPLICATIONS ADD CREATED_DATE DATETIME2 NULL",
                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('LOAN_APPLICATIONS') AND name = 'LAST_MODIFIED_BY') ALTER TABLE LOAN_APPLICATIONS ADD LAST_MODIFIED_BY VARCHAR(100) NULL",
                // 2c. Drop restrictive check constraints on LOAN_DOCUMENTS if any
                """
                DECLARE @drop_ck NVARCHAR(MAX) = N'';
                SELECT @drop_ck += N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(parent_object_id))
                    + '.' + QUOTENAME(OBJECT_NAME(parent_object_id)) 
                    + ' DROP CONSTRAINT ' + QUOTENAME(name) + ';'
                FROM sys.check_constraints
                WHERE parent_object_id IN (OBJECT_ID('LOAN_DOCUMENTS'), OBJECT_ID('loan_documents'), OBJECT_ID('LOAN_DOCUMENTS_AUD'), OBJECT_ID('loan_documents_aud'));
                IF @drop_ck <> N'' EXEC sp_executesql @drop_ck;
                """,

                // 3. LOAN_DOCUMENTS
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'LOAN_DOCUMENTS' AND type = 'U')
                BEGIN
                    CREATE TABLE LOAN_DOCUMENTS (
                        DOCUMENT_ID VARCHAR(36) NOT NULL,
                        APPLICATION_ID VARCHAR(36) NULL,
                        CUSTOMER_ID VARCHAR(36) NOT NULL,
                        DOC_TYPE VARCHAR(50) NOT NULL,
                        FILE_NAME VARCHAR(255) NOT NULL,
                        CONTENT_TYPE VARCHAR(100) NOT NULL,
                        BLOB_STORAGE_PATH VARCHAR(500) NOT NULL,
                        FILE_SIZE_BYTES BIGINT NOT NULL,
                        UPLOADED_AT DATETIME2 NOT NULL CONSTRAINT DF_LOAN_DOCS_UPLOADED_AT DEFAULT SYSUTCDATETIME(),
                        CONSTRAINT PK_LOAN_DOCUMENTS PRIMARY KEY (DOCUMENT_ID)
                    );
                END
                """,

                // 4. LOAN_AUDIT_LOGS
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'LOAN_AUDIT_LOGS' AND type = 'U')
                BEGIN
                    CREATE TABLE LOAN_AUDIT_LOGS (
                        LOG_ID BIGINT IDENTITY(1,1) NOT NULL,
                        APPLICATION_ID VARCHAR(36) NOT NULL,
                        PREVIOUS_STATUS VARCHAR(30) NULL,
                        NEW_STATUS VARCHAR(30) NOT NULL,
                        CHANGED_BY VARCHAR(100) NOT NULL,
                        COMMENTS VARCHAR(1000) NULL,
                        TIMESTAMP DATETIME2 NOT NULL CONSTRAINT DF_LOAN_AUDIT_TIMESTAMP DEFAULT SYSUTCDATETIME(),
                        CONSTRAINT PK_LOAN_AUDIT_LOGS PRIMARY KEY (LOG_ID)
                    );
                END
                """,

                // 5. REVINFO (Hibernate Envers)
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'REVINFO' AND type = 'U')
                BEGIN
                    CREATE TABLE REVINFO (
                        REV INT IDENTITY(1,1) NOT NULL,
                        REVTSTMP BIGINT NULL,
                        CONSTRAINT PK_REVINFO PRIMARY KEY (REV)
                    );
                END
                """,

                // 6. LOAN_APPLICATIONS_AUD (Hibernate Envers)
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'LOAN_APPLICATIONS_AUD' AND type = 'U')
                BEGIN
                    CREATE TABLE LOAN_APPLICATIONS_AUD (
                        APPLICATION_ID VARCHAR(36) NOT NULL,
                        REV INT NOT NULL,
                        REVTYPE TINYINT NOT NULL,
                        CUSTOMER_ID VARCHAR(36) NULL,
                        CUSTOMER_NAME VARCHAR(100) NULL,
                        CUSTOMER_EMAIL VARCHAR(100) NULL,
                        CUSTOMER_PHONE VARCHAR(20) NULL,
                        MONTHLY_INCOME DECIMAL(18, 2) NULL,
                        EXISTING_LIABILITIES DECIMAL(18, 2) NULL,
                        EMPLOYMENT_TYPE VARCHAR(50) NULL,
                        SCHEME_ID VARCHAR(36) NULL,
                        LOAN_TYPE VARCHAR(30) NULL,
                        LOAN_AMOUNT DECIMAL(18, 2) NULL,
                        DOCUMENT_PROVIDED BIT NULL,
                        TENURE_MONTHS INT NULL,
                        INTEREST_RATE DECIMAL(5, 2) NULL,
                        CALCULATED_EMI DECIMAL(18, 2) NULL,
                        STATUS VARCHAR(30) NULL,
                        RISK_SCORE INT NULL,
                        DTI_RATIO DECIMAL(5, 2) NULL,
                        ORCHESTRATION_INSTANCE_ID VARCHAR(100) NULL,
                        ASSIGNED_MANAGER VARCHAR(100) NULL,
                        DECISION_REMARKS VARCHAR(1000) NULL,
                        CREATED_AT DATETIME2 NULL,
                        UPDATED_AT DATETIME2 NULL,
                        CREATED_BY VARCHAR(100) NULL,
                        CREATED_DATE DATETIME2 NULL,
                        LAST_MODIFIED_BY VARCHAR(100) NULL,
                        LAST_MODIFIED_DATE DATETIME2 NULL,
                        CONSTRAINT PK_LOAN_APPLICATIONS_AUD PRIMARY KEY (APPLICATION_ID, REV)
                    );
                END
                """,

                // 7. LOAN_DOCUMENTS_AUD (Hibernate Envers)
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'LOAN_DOCUMENTS_AUD' AND type = 'U')
                BEGIN
                    CREATE TABLE LOAN_DOCUMENTS_AUD (
                        DOCUMENT_ID VARCHAR(36) NOT NULL,
                        REV INT NOT NULL,
                        REVTYPE TINYINT NOT NULL,
                        APPLICATION_ID VARCHAR(36) NULL,
                        CUSTOMER_ID VARCHAR(36) NULL,
                        DOC_TYPE VARCHAR(50) NULL,
                        FILE_NAME VARCHAR(255) NULL,
                        CONTENT_TYPE VARCHAR(100) NULL,
                        BLOB_STORAGE_PATH VARCHAR(500) NULL,
                        FILE_SIZE_BYTES BIGINT NULL,
                        UPLOADED_AT DATETIME2 NULL,
                        CONSTRAINT PK_LOAN_DOCUMENTS_AUD PRIMARY KEY (DOCUMENT_ID, REV)
                    );
                END
                """
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : ddlStatements) {
                try {
                    stmt.execute(sql);
                } catch (Exception ex) {
                    log.warn("DDL execution note for statement [{}]: {}", sql.trim().replaceAll("\\s+", " "), ex.getMessage());
                }
            }
            // Log current columns of LOAN_APPLICATIONS
            try (java.sql.ResultSet rs = stmt.executeQuery("""
                    SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'LOAN_APPLICATIONS'
                    ORDER BY ORDINAL_POSITION
                    """)) {
                while (rs.next()) {
                    log.info("LOAN_APPLICATIONS column: {} | type: {} | max_len: {}",
                            rs.getString("COLUMN_NAME"),
                            rs.getString("DATA_TYPE"),
                            rs.getString("CHARACTER_MAXIMUM_LENGTH"));
                }
            }

            log.info("MSSQL schema objects verified and updated successfully.");
        } catch (Exception e) {
            log.error("Failed to ensure schema objects exist in MSSQL: {}", e.getMessage(), e);
        }
    }

    private void seedSchemes() {
        try {
            if (schemeRepository.count() == 0) {
                log.info("Seeding default Loan Schemes Catalog into Database...");

                List<LoanScheme> schemes = List.of(
                        new LoanScheme(
                                "SCHEME-PL-01",
                                LoanType.PERSONAL_LOAN,
                                "Prime Flexi Personal Loan",
                                new BigDecimal("10000.00"),
                                new BigDecimal("1000000.00"),
                                6,
                                60,
                                new BigDecimal("11.50"),
                                true
                        ),
                        new LoanScheme(
                                "SCHEME-HL-01",
                                LoanType.HOME_LOAN,
                                "Dream Home Mortgage Scheme",
                                new BigDecimal("500000.00"),
                                new BigDecimal("50000000.00"),
                                36,
                                360,
                                new BigDecimal("8.40"),
                                true
                        ),
                        new LoanScheme(
                                "SCHEME-VL-01",
                                LoanType.VEHICLE_LOAN,
                                "DriveSmart Auto Loan",
                                new BigDecimal("100000.00"),
                                new BigDecimal("5000000.00"),
                                12,
                                60,
                                new BigDecimal("9.20"),
                                true
                        ),
                        new LoanScheme(
                                "SCHEME-EL-01",
                                LoanType.EDUCATION_LOAN,
                                "Global Scholar Education Loan",
                                new BigDecimal("50000.00"),
                                new BigDecimal("7500000.00"),
                                12,
                                180,
                                new BigDecimal("8.75"),
                                true
                        )
                );

                schemeRepository.saveAll(schemes);
                log.info("Successfully seeded {} loan schemes into Database.", schemes.size());
            }
        } catch (Exception e) {
            log.error("Failed to seed loan schemes: {}", e.getMessage(), e);
        }
    }
}

