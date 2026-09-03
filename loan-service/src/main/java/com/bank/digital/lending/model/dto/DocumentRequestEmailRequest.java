package com.bank.digital.lending.model.dto;

import java.util.List;

public record DocumentRequestEmailRequest(
        List<String> requiredDocumentTypes,
        String customNotes
) {
}
