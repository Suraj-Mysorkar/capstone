package com.bank.digital.lending.model.event;

import java.time.Instant;
import java.util.UUID;

public record LoanApplicationCompletedEvent(
    String eventId,
    String eventType,
    String source,
    String timestamp,
    LoanCompletedEventData data
) {
    public static LoanApplicationCompletedEvent of(LoanCompletedEventData data) {
        return new LoanApplicationCompletedEvent(
            "evt-" + UUID.randomUUID().toString(),
            "LOAN_APPLICATION_COMPLETED",
            "/services/digital-lending/loan-service",
            Instant.now().toString(),
            data
        );
    }
}
