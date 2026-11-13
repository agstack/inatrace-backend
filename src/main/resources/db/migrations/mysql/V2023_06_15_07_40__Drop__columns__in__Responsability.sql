-- Drop the obsolete 'relationship', 'farmer' and 'story' columns from Responsibility.
--
-- Guarded so this migration is idempotent. On a database whose schema was built
-- from the current entities these objects were never created, so each statement
-- is a no-op instead of failing (ERROR 1091 / 1051) and aborting startup. On a
-- database that still carries the legacy schema the drop happens exactly as it
-- always did.

-- ALTER TABLE Responsibility DROP COLUMN relationship;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'Responsibility'
                               AND COLUMN_NAME = 'relationship'),
                     'ALTER TABLE Responsibility DROP COLUMN relationship',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE Responsibility DROP COLUMN farmer;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'Responsibility'
                               AND COLUMN_NAME = 'farmer'),
                     'ALTER TABLE Responsibility DROP COLUMN farmer',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE Responsibility DROP COLUMN story;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'Responsibility'
                               AND COLUMN_NAME = 'story'),
                     'ALTER TABLE Responsibility DROP COLUMN story',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
