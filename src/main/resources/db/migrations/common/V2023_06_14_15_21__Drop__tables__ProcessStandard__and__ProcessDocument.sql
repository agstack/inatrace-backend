-- Drop the obsolete ProcessStandard and ProcessDocument tables.
--
-- Guarded so this migration is idempotent. On a database whose schema was built
-- from the current entities these objects were never created, so each statement
-- is a no-op instead of failing (ERROR 1091 / 1051) and aborting startup. On a
-- database that still carries the legacy schema the drop happens exactly as it
-- always did.

DROP TABLE IF EXISTS ProcessStandard;

DROP TABLE IF EXISTS ProcessDocument;
