# Your tests are only as honest as your database

The test suite runs on embedded HSQLDB. It's fast, it's convenient, and it is —
charitably — a simulator. It forgives things your production database will not,
and every forgiveness is a bug your tests can't see.

## Three ways the simulator lies to you

**Quoted aliases.** Generated selects alias primary-key columns with double
quotes (`col as "alias"`). HSQLDB, H2, and Postgres accept this happily. MySQL
without `ANSI_QUOTES` reads double quotes as *string literals* — your select
list quietly becomes a constant. Green on HSQLDB, broken in production.

**Generated keys.** After an insert, the library fishes the new key out of
whatever map the driver returns — matching by case-insensitive column name,
falling back to the entity's own value, falling back again to "the single value
in the map." Three fallback layers exist because drivers genuinely disagree
about the shape of that map. HSQLDB exercises exactly one shape.

**DDL quirks.** This repo's own `schema.sql` once carried a trailing comma in a
`CREATE TABLE` that HSQLDB tolerated without complaint. If the test database
accepts invalid DDL, it can also accept mappings that a real database rejects.

## The practice

Keep HSQLDB for what it's good at: **speed**. The fast inner loop — mapping
logic, query shapes, edge cases — belongs on the embedded DB.

Then add honesty where it matters: run the repository round-trip tests against
the real engine with Testcontainers, at least for PostgreSQL/MySQL targets, and
run `SchemaValidator.assertMappings` against a schema migrated with your *real*
migration history — not hand-written test DDL. You don't need the whole suite
on containers; you need the paths where the database has opinions: inserts with
generated keys, quoted aliases, and anything dialect-sensitive.

When an HSQLDB-green test fails on the real engine, fix the SQL generation, not
the test. That failure just paid for itself.

## What the library won't do

No bundled Testcontainers, no per-dialect SQL generation, no dialect abstraction
layer. One explicit SQL shape, verified against real engines, is the entire
strategy — and it only works if someone actually runs the verification.
