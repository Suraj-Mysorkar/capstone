-- The customer portal authenticates against the shared `Users` table (the same
-- one the loan officer console / user-validator use), writing rows with
-- user_role = 'customer'. That table already exists in the shared database, so
-- this migration only creates it when it is missing (fresh database / CI).
IF OBJECT_ID(N'dbo.Users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Users (
        User_ID         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        loginid         NVARCHAR(100)  NOT NULL,
        login_password  NVARCHAR(255)  NOT NULL,
        name            NVARCHAR(150)  NULL,
        email           NVARCHAR(255)  NULL,
        user_role       NVARCHAR(40)   NULL
    );
    CREATE UNIQUE INDEX idx_users_loginid ON dbo.Users(loginid);
END
