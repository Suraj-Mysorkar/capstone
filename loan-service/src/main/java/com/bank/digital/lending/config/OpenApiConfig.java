package com.bank.digital.lending.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Azure-Powered Digital Lending & Loan Application Service API")
                        .version("1.0.0")
                        .description("Microservice for retail loan application processing, automated credit risk assessment, " +
                                "Azure Function EMI calculations, Azure Blob document ingestion, Azure Durable Functions stateful orchestration, " +
                                "Azure Logic Apps human manager approvals, and Azure Service Bus completion event publishing.")
                        .contact(new Contact()
                                .name("Retail Banking Engineering Team")
                                .email("dev-support@digital-bank.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")));
    }
}
