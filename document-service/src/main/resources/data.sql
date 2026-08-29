INSERT INTO document_types (type_code, category_name, description, is_mandatory, max_size_mb, allowed_extensions)
SELECT 'IDENTITY_PROOF', 'Identity Proof', 'Aadhar Card, PAN Card, Passport', TRUE, 10, 'pdf,jpg,jpeg,png'
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE type_code = 'IDENTITY_PROOF');

INSERT INTO document_types (type_code, category_name, description, is_mandatory, max_size_mb, allowed_extensions)
SELECT 'INCOME_PROOF', 'Income Proof', 'Recent 3 months salary slips or Form 16 / ITR', TRUE, 10, 'pdf,jpg,jpeg,png'
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE type_code = 'INCOME_PROOF');

INSERT INTO document_types (type_code, category_name, description, is_mandatory, max_size_mb, allowed_extensions)
SELECT 'ADDRESS_PROOF', 'Address Proof', 'Electricity bill, Telephone bill, Rent Agreement', TRUE, 10, 'pdf,jpg,jpeg,png'
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE type_code = 'ADDRESS_PROOF');

INSERT INTO document_types (type_code, category_name, description, is_mandatory, max_size_mb, allowed_extensions)
SELECT 'BANK_STATEMENT', 'Bank Statement', 'Latest 6 months bank statement', TRUE, 15, 'pdf'
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE type_code = 'BANK_STATEMENT');

INSERT INTO document_types (type_code, category_name, description, is_mandatory, max_size_mb, allowed_extensions)
SELECT 'PHOTOGRAPH', 'Photograph', 'Recent color passport size photograph', TRUE, 5, 'jpg,jpeg,png'
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE type_code = 'PHOTOGRAPH');

INSERT INTO document_types (type_code, category_name, description, is_mandatory, max_size_mb, allowed_extensions)
SELECT 'EMPLOYMENT_PROOF', 'Employment Proof', 'Official employee ID card or offer letter', FALSE, 10, 'pdf,jpg,jpeg,png'
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE type_code = 'EMPLOYMENT_PROOF');