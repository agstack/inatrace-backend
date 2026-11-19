PostgreSQL-specific migrations.

The legacy cleanup migrations in `../mysql` use MySQL dynamic SQL
(`SET @s`, `PREPARE`, `information_schema`, `DATABASE()`). A fresh PostgreSQL
installation never has those legacy objects, so no equivalent migration is
needed. Portable migrations shared by both engines live in `../common`.
