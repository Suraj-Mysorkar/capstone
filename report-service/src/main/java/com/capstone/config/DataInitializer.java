package com.capstone.config;
//package com.example.demo.config;
//
//import com.example.demo.model.Customer;
//import com.example.demo.model.LoanApplication;
//import com.example.demo.model.Document;
//import com.example.demo.repository.CustomerRepository;
//import com.example.demo.repository.LoanApplicationRepository;
//import com.example.demo.repository.DocumentRepository;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//
//@Configuration
//public class DataInitializer {
//
//    @Bean
//    public CommandLineRunner loadData(
//            CustomerRepository customerRepo,
//            LoanApplicationRepository loanRepo,
//            DocumentRepository docRepo) {
//        
//        return args -> {
//            // 1. Create and save a Customer
//            Customer customer = new Customer();
//            customer.setCustomerId("c8f3a1b2-1111-4aaa-bbbb-cccccccccccc");
//            customer.setFullName("Alice Smith");
//            customer.setDob(LocalDate.of(1990, 4, 12));
//            customer.setNationalId("ENC_AKV_NID_8837192");
//            customer.setMobileNumber("+15550192834");
//            customer.setEmail("alice.smith@example.com");
//            customer.setAddress("742 Evergreen Terrace, Springfield");
//            customer.setEmploymentDetails("Senior Product Manager at TechCorp");
//            customer.setIncomeDetails(new BigDecimal("115000.00"));
//            customer.setOnboardingStatus("APPROVED");
//            
//            customerRepo.save(customer);
//
//            // 2. Create and save a Loan Application for Alice
//            LoanApplication loan = new LoanApplication();
//            loan.setApplicationId("l9e8d7c6-3333-4aaa-bbbb-cccccccccccc");
//            loan.setCustomer(customer); // Link to customer object
//            loan.setLoanType("Home");
//            loan.setLoanAmount(new BigDecimal("320000.00"));
//            loan.setTenureMonths(240);
//            loan.setInterestRate(new BigDecimal("5.75"));
//            loan.setStatus("APPROVED");
//            loan.setAssignedToManager("Manager_Sarah");
//            
//            loanRepo.save(loan);
//
//            // 3. Create and save a Document for the Loan
//            Document doc = new Document();
//            doc.setDocumentId("d1a2b3c4-5555-4aaa-bbbb-cccccccccccc");
//            doc.setLoanApplication(loan); // Link to loan application object
//            doc.setDocType("Identity Proof");
//            doc.setBlobStoragePath("https://windows.net");
//            doc.setVersion(1);
//            
//            docRepo.save(doc);
//            
//            System.out.println("✅ Sample records successfully created via Java Code!");
//        };
//    }
//}