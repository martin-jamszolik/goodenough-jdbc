# Fail at boot, not at 3 a.m.

Every schema-first project eventually lives through this: someone renames a
column on a Friday, the mapping still compiles, tests pass against stale DDL,
and production discovers the drift at the worst possible hour. The failure is
always the same — a SQL error in a code path nobody touched — and it is always
further from the cause than it needed to be.

`SchemaValidator.assertMappings` exists so that story doesn't happen to you. It
compares your entity annotations against the live database: tables, columns,
primary-key columns *and their order*, missing setters, misconfigured `@Ref`s.
But a validator that nobody runs is just documentation with extra steps. This
guide is about giving it a permanent home.

## Run it where it can stop you

**At startup in dev and staging.** Before the app accepts traffic, assert every
entity the deployment uses. In Spring that looks like this — a few lines, in
your codebase, entirely your choice of framework:

```java
@Bean
ApplicationRunner validateMappings(DataSource dataSource) {
    return args -> SchemaValidator.assertMappings(
        dataSource, Order.class, LineItem.class /* ... every deployed entity ... */);
}
```

A failed boot is a gift: loud, immediate, and pointing at the exact mismatch.
Compare that with a `BadSqlGrammarException` three weeks later in a report job.

**In CI, on every run.** The same call in your integration tests catches drift
before it ever ships. Stale test DDL is how Friday-afternoon renames survive to
production — the validator is the tripwire.

**In production, match the check to your nerve.** If your deployment can
tolerate refusing to start on drift, run it at boot there too. If not, run it
as a readiness probe or a scheduled check. Any automatic home beats "when
someone remembers."

## When it fails, trust it

A validator failure means one of two things: the annotations are wrong, or the
schema is wrong. Check the annotations first, then the migration history. What
it almost never means is that the validator is wrong — resist the urge to
"fix" the check instead of the drift.

## What the library won't do

It will not find your entities for you, scan the classpath, or hook into any
framework lifecycle. Which classes to validate and when to validate them are
decisions about *your* deployment, so they live in *your* code. The library
provides the honest check; you provide the discipline of running it.
