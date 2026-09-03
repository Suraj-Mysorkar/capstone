-- The shared Users table was sized for short employee handles: login_password is
-- NVARCHAR(20), too small for a real customer password. Widen it — a
-- metadata-only, backward-compatible change (existing rows and the
-- user-validator lookup are unaffected). Guarded so it is a no-op where the
-- column is already wide.
--
-- loginid stays narrow (it may carry an index); the portal stores a short
-- generated handle there and matches sign-in against the wider `email` column.

IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID(N'dbo.Users') AND name = N'login_password' AND max_length BETWEEN 1 AND 200)
    ALTER TABLE dbo.Users ALTER COLUMN login_password NVARCHAR(255) NOT NULL;
