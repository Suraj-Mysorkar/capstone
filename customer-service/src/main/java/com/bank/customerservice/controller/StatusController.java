package com.bank.customerservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class StatusController {

    @GetMapping("/api/customers/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "customer-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
