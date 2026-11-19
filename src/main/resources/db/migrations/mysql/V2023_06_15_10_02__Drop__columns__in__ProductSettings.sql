-- Drop the obsolete B2C toggle columns from ProductSettings.
--
-- Guarded so this migration is idempotent. On a database whose schema was built
-- from the current entities these objects were never created, so each statement
-- is a no-op instead of failing (ERROR 1091 / 1051) and aborting startup. On a
-- database that still carries the legacy schema the drop happens exactly as it
-- always did.

-- ALTER TABLE ProductSettings DROP COLUMN checkAuthenticity;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'ProductSettings'
                               AND COLUMN_NAME = 'checkAuthenticity'),
                     'ALTER TABLE ProductSettings DROP COLUMN checkAuthenticity',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE ProductSettings DROP COLUMN traceOrigin;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'ProductSettings'
                               AND COLUMN_NAME = 'traceOrigin'),
                     'ALTER TABLE ProductSettings DROP COLUMN traceOrigin',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE ProductSettings DROP COLUMN giveFeedback;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'ProductSettings'
                               AND COLUMN_NAME = 'giveFeedback'),
                     'ALTER TABLE ProductSettings DROP COLUMN giveFeedback',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
