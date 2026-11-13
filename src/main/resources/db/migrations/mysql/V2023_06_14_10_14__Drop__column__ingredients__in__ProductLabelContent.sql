-- Drop the obsolete 'ingredients' column from ProductLabelContent.
--
-- Guarded so this migration is idempotent. On a database whose schema was built
-- from the current entities these objects were never created, so each statement
-- is a no-op instead of failing (ERROR 1091 / 1051) and aborting startup. On a
-- database that still carries the legacy schema the drop happens exactly as it
-- always did.

-- ALTER TABLE ProductLabelContent DROP COLUMN ingredients;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'ProductLabelContent'
                               AND COLUMN_NAME = 'ingredients'),
                     'ALTER TABLE ProductLabelContent DROP COLUMN ingredients',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
