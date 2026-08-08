-- ==============================================================================
-- RAILWAY MYSQL MIGRATION SCRIPT
-- Add 'subject' column to 'class_sessions' table with 'UNSPECIFIED' placeholder
-- ==============================================================================

-- Step 1: Add 'subject' column as nullable initially
ALTER TABLE class_sessions ADD COLUMN subject VARCHAR(255) NULL;

-- Step 2: Backfill existing rows where subject is null/empty with placeholder 'UNSPECIFIED'
UPDATE class_sessions SET subject = 'UNSPECIFIED' WHERE subject IS NULL OR TRIM(subject) = '';

-- Step 3: Enforce NOT NULL constraint and default value for future rows
ALTER TABLE class_sessions MODIFY COLUMN subject VARCHAR(255) NOT NULL DEFAULT 'UNSPECIFIED';

-- NOTE REGARDING HISTORICAL DATA:
-- Existing rows updated to 'UNSPECIFIED' placeholder will maintain database integrity,
-- but historical attendance percentage calculations for old sessions will be inaccurate
-- until those old session rows are manually updated or re-labeled in MySQL.
