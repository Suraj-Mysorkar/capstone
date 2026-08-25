INSERT INTO documents (
    customer_id,
    application_id,
    document_type,
    document_name,
    status,
    created_at,
    updated_at
)
VALUES
    (1001, 5001, 'PAN_CARD', 'pan-card.pdf', 'VERIFIED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (1001, 5001, 'ADDRESS_PROOF', 'address-proof.pdf', 'UNDER_REVIEW',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    (1002, 5002, 'INCOME_PROOF', 'salary-slip.pdf', 'UPLOADED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO document_versions (
    document_id,
    version_number,
    file_name,
    content_type,
    file_size,
    file_data,
    created_at
)
VALUES
    (1, 1, 'pan-card.pdf', 'application/pdf', 102400,
     NULL, CURRENT_TIMESTAMP),

    (2, 1, 'address-proof.pdf', 'application/pdf', 204800,
     NULL, CURRENT_TIMESTAMP),

    (3, 1, 'salary-slip.pdf', 'application/pdf', 153600,
     NULL, CURRENT_TIMESTAMP);