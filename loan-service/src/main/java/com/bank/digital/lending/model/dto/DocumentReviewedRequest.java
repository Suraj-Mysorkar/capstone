package com.bank.digital.lending.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentReviewedRequest(
        String documentId,
        String documentType,
        String status,
        String remarks,
        String verifiedBy,
        String customerEmail
) {}
