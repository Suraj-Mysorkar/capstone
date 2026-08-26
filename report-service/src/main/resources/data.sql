-- Clean up existing data to prevent duplicate key errors on restart
DELETE FROM Documents;
DELETE FROM Loan_Applications;
DELETE FROM Customers;

-- 1. Insert Sample Customers
INSERT INTO Customers (
    Customer_ID, Full_Name, DOB, National_ID, Mobile_Number, 
    Email, Address, Employment_Details, Income_Details, Onboarding_Status, Created_At
) VALUES (
    '1', 
    'Alice Smith', 
    '1990-04-12', 
    'ENC_AKV_NID_8837192', 
    '+15550192834', 
    'alice.smith@example.com', 
    '742 Evergreen Terrace, Springfield', 
    'Senior Product Manager at TechCorp (Full-Time)', 
    115000.00, 
    'APPROVED', 
    CURRENT_TIMESTAMP
);

INSERT INTO Customers (
    Customer_ID, Full_Name, DOB, National_ID, Mobile_Number, 
    Email, Address, Employment_Details, Income_Details, Onboarding_Status, Created_At
) VALUES (
    '2', 
    'Bob Jones', 
    '1983-11-23', 
    'ENC_AKV_NID_2948103', 
    '+15550147291', 
    'bob.jones@example.com', 
    '123 Maple Street, Riverdale', 
    'Independent Business Consultant (Self-Employed)', 
    85000.00, 
    'PENDING', 
    CURRENT_TIMESTAMP
);


-- 2. Insert Sample Loan Applications
-- Alice has an APPROVED Home Loan
INSERT INTO Loan_Applications (
    Application_ID, Customer_ID, Loan_Type, Loan_Amount, 
    Tenure_Months, Interest_Rate, Status, Assigned_To_Manager, Created_At
) VALUES (
    '1', 
    '2', 
    'Home', 
    320000.00, 
    240, 
    5.75, 
    'APPROVED', 
    'Manager_Sarah', 
    CURRENT_TIMESTAMP
);

-- Bob has an IN_REVIEW Personal Loan
INSERT INTO Loan_Applications (
    Application_ID, Customer_ID, Loan_Type, Loan_Amount, 
    Tenure_Months, Interest_Rate, Status, Assigned_To_Manager, Created_At
) VALUES (
    '2', 
    '1', 
    'Personal', 
    15000.00, 
    36, 
    10.50, 
    'IN_REVIEW', 
    'Manager_David', 
    CURRENT_TIMESTAMP
);


-- 3. Insert Sample Document Metadata
-- Alice's documents for her Home Loan (Identity and Income proof)
INSERT INTO Documents (
    Document_ID, Application_ID, Doc_Type, Blob_Storage_Path, Version, Uploaded_At
) VALUES (
    '1', 
    '1', 
    'Identity Proof', 
    'https://windows.net', 
    1, 
    CURRENT_TIMESTAMP
);

INSERT INTO Documents (
    Document_ID, Application_ID, Doc_Type, Blob_Storage_Path, Version, Uploaded_At
) VALUES (
    '2', 
    '2', 
    'Income Proof', 
    'https://windows.net', 
    2, 
    CURRENT_TIMESTAMP
);

-- Bob's document for his Personal Loan
INSERT INTO Documents (
    Document_ID, Application_ID, Doc_Type, Blob_Storage_Path, Version, Uploaded_At
) VALUES (
    '3', 
    '1', 
    'Address Proof', 
    'https://windows.net', 
    1, 
    CURRENT_TIMESTAMP
);
