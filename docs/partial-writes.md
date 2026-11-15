# Partial writes

Choose the smallest write shape that expresses the operation:

- `save(entity)` retains full mapped INSERT/UPDATE semantics, selected by `isNew()`.
- `updateFields(entity, "displayName", "team")` always UPDATEs only those Java
  properties. It needs a complete identity in `getRefs()` and a nonempty selection.
- `@Write(insertable = false, updatable = false)` keeps a database-managed value
  readable and schema-validated while omitting it from generated writes.
- `@Write(updatable = false)` permits an insert-only value.
- Independent narrow entities can map fixed slices of the same table.

## Selected updates

```java
Member patch = new Member();
patch.setRefs(Key.of("member_id", memberId));
patch.setDisplayName("New name");
patch.setTeam(null);
repository.updateFields(patch, "displayName", "team");
```

One parameterized UPDATE sets the mapped name and team FK (to SQL NULL), using
every key component in the predicate. Unselected values need not be loaded or
initialized; their getters are not invoked. Names resolve through the existing
`@Named` and `@Ref` mappings, not through raw SQL column names.

Null, blank, unknown, duplicate, skipped, collection, key and non-updatable
selections are rejected before JDBC. Incomplete identity is also rejected.
The single-row convention applies: zero or multiple affected rows fail.
The return value is the existing identity, not a refreshed entity. There is no
preceding read, insert fallback, dirty tracking, or automatic generated-value
refresh. Read explicitly if you need database-derived values.

## Write policies

`org.viablespark.persistence.dsl.Write` is a runtime field/getter annotation.
Both flags default to `true`; inherited annotation resolution follows the other
mapping annotations. Policies apply equally to `save`, `insertAll`, `updateAll`,
and selected updates. `@Skip` still excludes reads and writes; `@Write` does not.

```java
@Write(insertable = false, updatable = false)
private LocalDateTime createdAt;

@Write(updatable = false)
private String createdBy;
```

Omitting a column lets the database apply its default; sending a selected null
is different and writes SQL NULL. The database owns defaults and constraints.
An operation with no eligible columns fails before execution: portable
default-only INSERTs are not supported. Assigned keys should be set on their
insertable mapped properties while refs remain empty; `setIdentifier`/`setRefs`
instead makes `save` choose UPDATE. Assigned insertion identity is derived only
from insert-eligible mapped properties. A stale value on an insert-excluded key
property is ignored; generated keys must come from the database, not that value.
Legacy full updates retain their existing mapped-primary-key behavior; selected
updates always reject primary-key columns.

## Fixed table slices and tradeoffs

For stable use cases, define two independent entities with the same `@Named`
table and complete `@PrimaryKey` declarations: e.g. `MemberProfile` maps only
profile columns and `MemberPreferences` maps only preference columns. Saving an
existing slice updates only that slice, so one does not clobber the other.
Subclassing a full entity does **not** create a slice: inherited properties are
still mapped. Slice inserts must satisfy all table constraints and defaults,
including columns the slice does not map.

Selected updates avoid maintaining extra slice classes for occasional narrow
writes but use string property names, which should be covered by tests when
renaming properties. Neither mechanism is authorization or concurrency control.
Trusted application code owns the choice of permitted fields, authorization,
tenant scoping, lost-update policy, and transaction boundaries. A mapped-name
allowlist is not an authorization boundary; do not forward arbitrary request
fields to it.

## Verification

Run `SchemaValidator.assertMappings` for all entity shapes against an authorized
schema. It checks mapped columns, not insert readiness or full table coverage.
Test preservation of unselected values, explicit nulls, references, composite
keys, policies, batch parity and invalid selections. This repository's
`PartialWritesTest` uses an isolated HSQLDB schema; `./gradlew check` also runs
the existing CRUD regressions, coverage gate and style checks. Defaults,
generated keys and affected-row counts should additionally be verified with
the consumer's actual driver/database in its authorized test environment.
