-- ==============================================================================
-- DATABASE MIGRATION SCRIPT: ADD must_change_password COLUMN
-- Compatible with PostgreSQL & MySQL / Safe ALTER TABLE
-- ==============================================================================

-- Step 1: Add column if it does not already exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'must_change_password'
    ) THEN
        ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

-- Fallback standard SQL (for MySQL or direct execution if needed):
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN DEFAULT FALSE;
-- UPDATE users SET must_change_password = FALSE WHERE must_change_password IS NULL;
