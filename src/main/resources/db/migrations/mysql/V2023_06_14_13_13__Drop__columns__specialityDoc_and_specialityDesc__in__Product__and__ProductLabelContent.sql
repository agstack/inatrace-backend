-- Drop the obsolete 'speciality' document/description columns and their FKs.
--
-- Guarded so this migration is idempotent. On a database whose schema was built
-- from the current entities these objects were never created, so each statement
-- is a no-op instead of failing (ERROR 1091 / 1051) and aborting startup. On a
-- database that still carries the legacy schema the drop happens exactly as it
-- always did.

-- ALTER TABLE Product DROP FOREIGN KEY FK5414co2yomrmr1wbiahodoi6j;
-- Resolved by column rather than by the hard-coded name, so the column can still
-- be dropped on databases where the constraint was generated under another name.
SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'Product'
               AND COLUMN_NAME = 'specialityDocument_id'
               AND REFERENCED_TABLE_NAME IS NOT NULL
             LIMIT 1);
SET @s := (SELECT IF(@fk IS NULL, 'SELECT 1',
                     CONCAT('ALTER TABLE Product DROP FOREIGN KEY ', @fk)));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE Product DROP COLUMN specialityDocument_id;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'Product'
                               AND COLUMN_NAME = 'specialityDocument_id'),
                     'ALTER TABLE Product DROP COLUMN specialityDocument_id',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE Product DROP COLUMN specialityDescription;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'Product'
                               AND COLUMN_NAME = 'specialityDescription'),
                     'ALTER TABLE Product DROP COLUMN specialityDescription',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE ProductLabelContent DROP FOREIGN KEY FKo78331b0piyfvhsjhibs5ckmo;
-- Resolved by column rather than by the hard-coded name, so the column can still
-- be dropped on databases where the constraint was generated under another name.
SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'ProductLabelContent'
               AND COLUMN_NAME = 'specialityDocument_id'
               AND REFERENCED_TABLE_NAME IS NOT NULL
             LIMIT 1);
SET @s := (SELECT IF(@fk IS NULL, 'SELECT 1',
                     CONCAT('ALTER TABLE ProductLabelContent DROP FOREIGN KEY ', @fk)));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE ProductLabelContent DROP COLUMN specialityDocument_id;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'ProductLabelContent'
                               AND COLUMN_NAME = 'specialityDocument_id'),
                     'ALTER TABLE ProductLabelContent DROP COLUMN specialityDocument_id',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ALTER TABLE ProductLabelContent DROP COLUMN specialityDescription;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                             WHERE TABLE_SCHEMA = DATABASE()
                               AND TABLE_NAME = 'ProductLabelContent'
                               AND COLUMN_NAME = 'specialityDescription'),
                     'ALTER TABLE ProductLabelContent DROP COLUMN specialityDescription',
                     'SELECT 1'));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
