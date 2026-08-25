package com.bank.digital.lending.config;

import com.bank.digital.lending.model.entity.LoanScheme;
import com.bank.digital.lending.model.enums.LoanType;
import com.bank.digital.lending.repository.LoanSchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class H2DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(H2DataInitializer.class);

    private final LoanSchemeRepository schemeRepository;

    public H2DataInitializer(LoanSchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    @Override
    public void run(String... args) {
        if (schemeRepository.count() == 0) {
            log.info("Seeding default Loan Schemes Catalog into H2 Database...");

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
                            84,
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
            log.info("Successfully seeded {} loan schemes into H2 Database.", schemes.size());
        }
    }
}
