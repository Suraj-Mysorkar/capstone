package com.capstone.document.config;

import com.capstone.document.entity.DocumentTypeMaster;
import com.capstone.document.repository.DocumentTypeMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MasterDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterDataInitializer.class);

    private final DocumentTypeMasterRepository repository;

    public MasterDataInitializer(DocumentTypeMasterRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            log.info("[DOCUMENT-SERVICE] Initializing Master Document Types...");

            List<DocumentTypeMaster> types = List.of(
                    new DocumentTypeMaster(null, "IDENTITY_PROOF", "Identity Proof", "Aadhar Card, PAN Card, Passport", true, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "INCOME_PROOF", "Income Proof", "Recent 3 months salary slips or ITR Form 16", true, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "ADDRESS_PROOF", "Address Proof", "Electricity bill, Telephone bill, Rent Agreement", true, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "BANK_STATEMENT", "Bank Statement", "Latest 6 months bank statement", true, 15, "pdf", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "PHOTOGRAPH", "Photograph", "Recent color passport size photograph", true, 5, "jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "EMPLOYMENT_PROOF", "Employment Proof", "Official employee ID card or offer letter", false, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "PAN_CARD", "PAN Card", "Permanent Account Number Card", true, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "AADHAAR_CARD", "Aadhaar Card", "UIDAI Aadhaar Card", true, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "PASSPORT", "Passport", "Valid Government Passport", true, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now()),
                    new DocumentTypeMaster(null, "OTHER", "Other Document", "Additional supporting documents", false, 10, "pdf,jpg,jpeg,png", java.time.LocalDateTime.now())
            );

            repository.saveAll(types);
            log.info("[DOCUMENT-SERVICE] Seeded {} master document types successfully.", types.size());
        }
    }
}
