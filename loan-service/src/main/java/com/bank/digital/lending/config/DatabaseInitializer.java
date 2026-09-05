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
                // 1. loan_schemes
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'loan_schemes' AND type = 'U')
                BEGIN
                    CREATE TABLE loan_schemes (
                        SCHEME_ID VARCHAR(64) NOT NULL,
                        SCHEME_NAME VARCHAR(255) NOT NULL,
                        DESCRIPTION VARCHAR(1000) NULL,
                        MIN_AMOUNT DECIMAL(15, 2) NOT NULL,
                        MAX_AMOUNT DECIMAL(15, 2) NOT NULL,
                        MIN_INTEREST_RATE DECIMAL(5, 2) NOT NULL,
                        MAX_INTEREST_RATE DECIMAL(5, 2) NOT NULL,
                        MIN_TENURE_MONTHS INT NOT NULL,
                        MAX_TENURE_MONTHS INT NOT NULL,
                        ACTIVE BIT NOT NULL CONSTRAINT DF_LOAN_SCHEMES_IS_ACTIVE DEFAULT 1,
                        CREATED_AT DATETIME2 NOT NULL DEFAULT GETDATE(),
                        CONSTRAINT PK_LOAN_SCHEMES PRIMARY KEY (SCHEME_ID)
                    );
                END
                """,

                // 2. loan_applications
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'loan_applications' AND type = 'U')
                BEGIN
                    CREATE TABLE loan_applications (
                        APPLICATION_ID BIGINT IDENTITY(10001,1) NOT NULL,
                        CUSTOMER_ID VARCHAR(64) NOT NULL,
                        CUSTOMER_NAME VARCHAR(255) NULL,
                        CUSTOMER_EMAIL VARCHAR(255) NULL,
                        SCHEME_ID VARCHAR(64) NULL,
                        SCHEME_NAME VARCHAR(255) NULL,
                        APPLIED_AMOUNT DECIMAL(15, 2) NOT NULL,
                        TENURE_MONTHS INT NOT NULL,
                        INTEREST_RATE DECIMAL(5, 2) NOT NULL,
                        MONTHLY_EMI DECIMAL(15, 2) NOT NULL,
                        PURPOSE VARCHAR(500) NULL,
                        STATUS VARCHAR(34) NOT NULL,
                        ASSIGNED_MANAGER_ID VARCHAR(64) NULL,
                        DECISION_REMARKS VARCHAR(1000) NULL,
                        DOCUMENT_PROVIDED BIT DEFAULT 0,
                        CREATED_BY VARCHAR(100) NULL,
                        CREATED_DATE DATETIME2 NOT NULL DEFAULT GETDATE(),
                        LAST_MODIFIED_BY VARCHAR(100) NULL,
                        LAST_MODIFIED_DATE DATETIME2 NULL,
                        VERSION BIGINT DEFAULT 0,
                        CONSTRAINT PK_LOAN_APPLICATIONS PRIMARY KEY (APPLICATION_ID)
                    );
                END
                """,

                // 3. loan_documents
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'loan_documents' AND type = 'U')
                BEGIN
                    CREATE TABLE loan_documents (
                        DOCUMENT_ID VARCHAR(64) NOT NULL,
                        APPLICATION_ID BIGINT NOT NULL,
                        DOCUMENT_TYPE VARCHAR(100) NOT NULL,
                        FILE_NAME VARCHAR(255) NOT NULL,
                        FILE_PATH VARCHAR(500) NOT NULL,
                        FILE_SIZE_BYTES BIGINT NULL,
                        CONTENT_TYPE VARCHAR(100) NULL,
                        VERIFICATION_STATUS VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                        REVIEWED_BY VARCHAR(100) NULL,
                        REVIEW_REMARKS VARCHAR(500) NULL,
                        REVIEWED_AT DATETIME2 NULL,
                        UPLOADED_AT DATETIME2 NOT NULL DEFAULT GETDATE(),
                        CONSTRAINT PK_LOAN_DOCUMENTS PRIMARY KEY (DOCUMENT_ID)
                    );
                END
                """,

                // 4. loan_audit_logs
                """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'loan_audit_logs' AND type = 'U')
                BEGIN
                    CREATE TABLE loan_audit_logs (
                        LOG_ID BIGINT IDENTITY(1,1) NOT NULL,
                        APPLICATION_ID BIGINT NOT NULL,
                        PREVIOUS_STATUS VARCHAR(34) NULL,
                        NEW_STATUS VARCHAR(34) NOT NULL,
                        ACTION_BY VARCHAR(100) NOT NULL,
                        ACTION_ROLE VARCHAR(50) NOT NULL,
                        REMARKS VARCHAR(1000) NULL,
                        TIMESTAMP DATETIME2 NOT NULL DEFAULT GETDATE(),
                        CONSTRAINT PK_LOAN_AUDIT_LOGS PRIMARY KEY (LOG_ID)
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

