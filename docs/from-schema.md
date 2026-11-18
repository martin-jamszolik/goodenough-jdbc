# From a live database to working repositories

Say someone hands you a JDBC connection and a schema name — "here's the
`demo` database, we need repositories" — and walks away. This guide is what
you do next. No hand-transcribed DDL, no guessing at key order, no mapping from
memory. The database already knows everything about itself; your job is to ask
it, write down what it says, and then prove your code agrees with it.

You will finish with one entity and one repository per table, a validator run
that ties them to the live schema, and a round-trip test per entity. That loop
— **ask, map, prove** — is the whole method.

## Ask: let the schema introduce itself

Before writing a single annotation, interrogate the database. You need four
things per table: its columns, its primary key **in order**, its foreign keys,
and which columns the database fills in by itself (defaults, identities,
generated values). Eyeballing DDL gets three of these right on a good day. The
metadata gets all four right every day.

The portable way is `DatabaseMetaData` — it works on every JDBC driver, which
matters when you're pointed at a database you've never met:

```java
var meta = dataSource.getConnection().getMetaData();

// Tables in the schema
try (var tables = meta.getTables(null, "demo", "%", new String[]{"TABLE"})) {
    while (tables.next()) System.out.println(tables.getString("TABLE_NAME"));
}

// Primary key IN ORDER — KEY_SEQ is the authority on key order.
// This settles every "@PrimaryKey order" question, permanently.
try (var pk = meta.getPrimaryKeys(null, "demo", "member")) {
    while (pk.next())
        System.out.println(pk.getShort("KEY_SEQ") + ": " + pk.getString("COLUMN_NAME"));
}

// Foreign keys: what points where
try (var fk = meta.getImportedKeys(null, "demo", "member")) {
    while (fk.next())
        System.out.println(fk.getString("FKCOLUMN_NAME") + " -> "
            + fk.getString("PKTABLE_NAME") + "." + fk.getString("PKCOLUMN_NAME"));
}
```

If you prefer SQL and you're on Postgres, `information_schema` answers the same
questions — primary-key order comes from `ordinal_position` in the key usage:

```sql
-- PK columns in order, for one table
SELECT kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'demo'
  AND tc.table_name = 'member'
  AND tc.constraint_type = 'PRIMARY KEY'
ORDER BY kcu.ordinal_position;
```

Write the answers down — a table per table, on paper or in a scratch file. This
scratch record is the most valuable artifact of the whole exercise, because
every annotation you write next is a transcription of it, and every validator
failure later is a diff against it.

## Map: transcribe, don't invent

Now classify each table. There are only three shapes, and the metadata tells you
which is which:

1. **Plain entity** — one primary-key column. The common case.
2. **Composite-key entity** — several PK columns. The order you wrote down from
   `KEY_SEQ` is the order of your `@PrimaryKey` annotations. Not alphabetical,
   not declaration order — `KEY_SEQ` order.
3. **Junction table** — its PK is exactly two foreign keys (e.g. `member_id` +
   `project_id`). This becomes an entity with a composite key and one `@Ref` per
   side — the `ProposalTask` shape.

Suppose the metadata told you this about a `member` table:

```
TABLE member
  PK (KEY_SEQ): 1: member_id
  COLUMNS: member_id BIGINT NOT NULL, display_name VARCHAR(200) NOT NULL,
           team_id BIGINT NULL, created_at TIMESTAMP DEFAULT now()
  IMPORTED KEYS: team_id -> team.team_id
```

The transcription is mechanical — and "mechanical" is a compliment here:

```java
@Named("member")
@PrimaryKey("member_id")
public class Member extends Model {

    private Long memberId;                    // member_id, from PK record

    @Named("display_name")
    private String displayName;               // non-conventional name: annotate

    @Ref
    private Team team;                        // team_id -> team.team_id, single-column FK.
                                              // Stores the key only. Null member writes SQL NULL.

    @Write(insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt; // DB-managed, but still readable.

    // Getters and setters for everything mapped. No exceptions —
    // the validator will fail you on a missing setter, and it will be right.
}
```

A few transcription rules that save real debugging sessions:

- **Database-managed columns can stay readable.** Use `@Write` to omit them from
  generated inserts/updates, not `@Skip` (which also excludes reads). Read explicitly
  after saving when you need defaults; see [partial writes](partial-writes.md).

- **Annotate every name that isn't plain camelCase→snake_case.** Especially
  acronyms — `getURLValue` does not map to `url_value`, and the database will
  not find it amusing.
- **Single-PK tables → `extends Model`.** UUID, String, or composite keys →
  implement `Persistable` directly and work with `Key`. (Full rules in
  `type-mapping.md` — including why Java enums never go straight through.)
- **Single-column FK → `@Ref`.** Composite FK → separate mapped columns, no
  `@Ref`. Collections are never mapped — note now which follow-up query or
  `RelationLoader` call each one needs, so it doesn't become a forgotten TODO.
- **Views and PK-less tables are read models.** You can read them with
  `queryEntity` just fine, but without a key there is nothing to update or
  delete by. Don't force them into the CRUD shape.

## Wire: one repository per entity, nothing clever

A repository is a `BaseRepository` subclass holding a `JdbcTemplate`. The
`JdbcTemplate` comes from your `DataSource` — which, in this story, is the
connection you were handed:

```java
var jdbc = new JdbcTemplate(dataSource);

public class MemberRepository extends BaseRepository<Member> {
    public MemberRepository(JdbcTemplate db) { super(db); }

    // Domain finders live here, as fragments — values always as '?'.
    public List<Member> activeInTeam(long teamId) {
        return queryEntity(
            new SqlQuery().where("team_id = ?", teamId).orderBy("display_name", ASC),
            Member.class);
    }
}
```

Resist every urge to be clever here: no generic base classes with reflection
tricks, no transaction handling (see `transactions.md` — that door is locked
from the inside), no tenant magic unless your project has exactly one
documented place for it. A repository that is boring to read is a repository
that is safe to trust.

## Prove: the database grades your work

This is where having the live connection pays off completely. Point the
validator at the *same database you mapped from* — not test DDL, not your
memory, the thing itself:

```java
SchemaValidator.assertMappings(dataSource, Member.class, Team.class /* ... */);
```

Then write the round-trip test per entity: `save` → `get` → `update` →
`delete`, plus the edges that actually break in practice (null `@Ref` writes
`NULL`? composite key survives the trip? `queryOne` throws on two rows?). Run
those against the real engine where you can (see `real-databases.md` for why
HSQLDB alone is a simulator). In this repo that home is
`src/integrationTest` — run it with `./gradlew integrationTest`. It is wired
exactly like this guide prescribes: env-gated (`DEMO_JDBC_*`, aborts loudly
otherwise), excluded from `check` and from the unit coverage gate, and its first
test is the validator itself, pointed at the live schema.

When the validator is green and the round-trips pass, you are done — not
"probably done," done. The mapping is no longer a belief you hold; it is a
fact the database confirmed. And when the schema changes next quarter, you
won't re-derive anything: you'll re-run the validator, read the diff, and fix
exactly what moved. Ask, map, prove — every time.
