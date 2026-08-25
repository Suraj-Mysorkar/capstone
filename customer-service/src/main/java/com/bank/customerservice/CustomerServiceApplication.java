package com.bank.customerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Customer Service
 * <p>
 * Owns Profile Management and Onboarding Status Tracking for the loan
 * origination platform. Exposed behind Azure API Management at /api/customers.
 * <p>
 * Responsibilities:
 *  - CRUD + search on customer profiles (Azure SQL Database)
 *  - Onboarding status lifecycle tracking
 *  - Publishes CustomerRegisteredEvent / CustomerStatusChangedEvent to Azure Event Grid
 *    for downstream consumers (Notification Service, Event-Driven Services)
 */
@SpringBootApplication
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
