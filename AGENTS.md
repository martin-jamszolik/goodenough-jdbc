# AGENTS.md

## Purpose
Repo-local instructions for Codex in `goodenough-jdbc`.

## Read First
- [`llm.md`](./llm.md) is the durable project memory file.
- This repo is a schema-first JDBC library, not a heavy ORM.
- Use the global `goodenough-jdbc` skill and follow its schema-first repository and mapping rules.

## Codex Behavior
- Prefer the patterns described in `llm.md` over inventing new abstractions.
- Use `BaseRepository`, `SqlQuery`, `NamedSqlQuery`, `PersistableRowMapper`, and `RelationLoader` when they fit the task.
- Treat `SchemaValidator` as the default way to catch mapping drift.
- Keep collection loading explicit; do not assume lazy loading or automatic relationship persistence.
- Keep SQL explicit, parameterized, and readable.

## Safety Rules
- Preserve the schema-first design.
- Do not hide transactions, migrations, or relation loading behind new layers unless explicitly asked.
- When changing persistence code, verify the entity mapping rules in `llm.md` first.

## Fast Recall
If a task mentions:
- `repository` or `query`: follow the repository/query patterns in `llm.md`.
- `mapping` or `entity`: follow the annotation and key rules in `llm.md`.
- `joins` or `relations`: use explicit mappers or `RelationLoader`.
- `schema drift`: run or update `SchemaValidator` checks.
