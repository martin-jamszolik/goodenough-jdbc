# goodenough-jdbc - LLM Agent Instructions

## Overview
Lightweight schema-first JDBC library built on `spring-jdbc`. Maps entities via annotations; provides repository CRUD, positional and named query DSLs, projection reads, explicit relation loaders, and schema validation helpers.

## Core Concepts

### Entity Creation
Entities implement `Persistable` (or extend `Model`). Use annotations for mapping:

```java
@Named("table_name")     // Optional: custom table name (defaults to snake_case of class)
@PrimaryKey("pk_column") // Required: primary key column name
public class MyEntity extends Model {
    
    @Named("db_column")  // Map field to non-standard column name
    private String fieldName;
    
    @Ref                 // Foreign key: auto-maps by convention (entity's PK)
    private OtherEntity other;
    
    @Ref(value = "fk_column", label = "display_column") // FK with label lookup
    private RefValue lookupRef;
    
    @Skip                // Exclude from persistence
    private String transientField;
    
    // Getters/setters required - annotations work on methods too
}
```

**Key Rules:**
- `@Named` on class = table name; on field/getter = column name
- Default column mapping: `camelCase` → `snake_case`
- `@Ref` on `Persistable` type = foreign key reference (stores only the key)
- `@Ref` on `RefValue` = foreign key with label lookup (value + display text)
- Collection-valued getters are ignored by generated select/insert/update SQL by convention
- `Model` provides `Key getRefs()/setRefs()` and `Long getId()/setId()`
- Getters/setters are required for mapped properties and relation fields

### Alternative: Implement Persistable Directly
```java
@PrimaryKey("my_key")
public class MyEntity implements Persistable {
    private Key key = Key.None;
    
    @Override public Key getRefs() { return key; }
    @Override public void setRefs(Key refs) { this.key = refs; }
    // fields and getters/setters...
}
```

## Repository Usage

### Basic Repository
```java
public class MyRepository extends BaseRepository<MyEntity> {
    public MyRepository(JdbcTemplate db) { super(db); }
}
// Or inline: var repo = new BaseRepository<MyEntity>(jdbcTemplate) {};
```

### CRUD Operations
```java
// CREATE - returns generated key
MyEntity entity = new MyEntity();
entity.setName("Test");
entity.setOther(new OtherEntity("other_key", 1L)); // FK reference
Optional<Key> key = repository.save(entity);

// READ by key
Optional<MyEntity> found = repository.get(Key.of("pk_column", 1L), MyEntity.class);

// UPDATE - automatic when entity has refs set
found.get().setName("Updated");
repository.save(found.get()); // updates because isNew() == false

// DELETE
repository.delete(entity); // uses entity.getRefs()
```

### Query Operations
```java
// Simple query with conditions
List<MyEntity> results = repository.queryEntity(
    new SqlQuery().where("status = ?", "active"),
    MyEntity.class
);

// Complex conditions
List<MyEntity> results = repository.queryEntity(
    new SqlQuery()
        .where("status = ?", "active")
        .andWhere("amount > ?", 1000)
        .orWhere("priority = ?", "high")
        .orderBy("created_date", SqlQuery.Direction.DESC)
        .limit(10),
    MyEntity.class
);

// Pagination
new SqlQuery().where("id > ?", 0).limit(20).offset(40); // page 3

// Specify primary key for query (if not on class annotation)
new SqlQuery().where("fk_id = ?", 1).primaryKey("pk_column");

// Named-parameter DSL
List<MyEntity> namedResults = repository.queryEntity(
    new NamedSqlQuery()
        .where("status = :status")
        .andWhere("created_at >= :fromDate")
        .orderBy("created_at", SqlQuery.Direction.DESC)
        .param("status", "active")
        .param("fromDate", fromDate),
    MyEntity.class
);

// Single-result entity read
Optional<MyEntity> one = repository.queryOne(
    new NamedSqlQuery().where("id = :id").param("id", 1L),
    MyEntity.class
);

// Ad-hoc scalar / DTO reads
Optional<String> status = repository.queryRow(
    new NamedSqlQuery()
        .selectColumns("status")
        .from("my_entity")
        .where("id = :id")
        .param("id", 1L),
    (rs, rowNum) -> rs.getString("status")
);

List<MySummary> summaries = repository.queryProjection(
    new NamedSqlQuery()
        .selectColumns("id as id", "display_name as name")
        .from("my_entity")
        .where("status = :status")
        .param("status", "active"),
    MySummary.class
);
```

## SqlQuery DSL Reference

```java
// Building queries
new SqlQuery()
    .select("SELECT col1, col2")        // or .selectColumns("col1", "col2")
    .selectDistinct("category")          // SELECT DISTINCT
    .from("table_name t")
    .join("INNER JOIN other o ON (t.id = o.t_id)")
    .where("col = ?", value)             // WHERE col = ?
    .andWhere("other > ?", val2)         // AND other > ?
    .orWhere("flag = ?", true)           // OR flag = ?
    .condition("status IN (?, ?)", a, b) // Direct condition
    .orderBy("col", Direction.DESC)      // ORDER BY col desc
    .orderBy("col2", Direction.ASC)      // Multiple: ORDER BY col desc, col2 asc
    .orderBy("CASE WHEN x=1 THEN 0 END") // Raw expression
    .limit(10)
    .offset(20)
    .paginate(pageSize, pageNumber);     // Convenience for limit+offset

// Raw SQL (use for complex JOINs)
SqlQuery.raw("SELECT * FROM t1 INNER JOIN t2 ON ... WHERE x > ?", value);
```

## NamedSqlQuery DSL Reference

```java
new NamedSqlQuery()
    .select("SELECT col1, col2")        // or .selectColumns("col1", "col2")
    .selectDistinct("category")
    .from("table_name t")
    .join("INNER JOIN other o ON (t.id = o.t_id)")
    .where("col = :value")
    .andWhere("other > :minValue")
    .orWhere("flag = :flag")
    .condition("status IN (:statuses)")
    .orderBy("created_at", SqlQuery.Direction.DESC)
    .limit(10)
    .offset(20)
    .paginate(pageSize, offset)
    .param("value", value)
    .param("minValue", 100)
    .params(Map.of("flag", true, "statuses", List.of("A", "B")))
    .primaryKey("id");

NamedSqlQuery.raw(
    "SELECT * FROM my_entity WHERE status IN (:statuses)",
    Map.of("statuses", List.of("A", "B"))
);
```

Use `NamedSqlQuery` when the SQL is still composable but positional placeholders are getting hard to read.

## Projection Reads

Projection helpers use Spring's `DataClassRowMapper`, so aliases must match constructor parameter names or bean property names.

```java
record MySummary(Long id, String name) {}

Optional<MySummary> summary = repository.queryProjectionOne(
    SqlQuery.raw(
        "SELECT id as id, display_name as name FROM my_entity WHERE id = ?",
        1L
    ),
    MySummary.class
);
```

For custom conversions, use `queryRow(...)` / `queryRows(...)` with a `RowMapper`.

## Custom Mappers (for JOINs)

### Simple JOIN Mapper
```java
public class ProposalMapper implements PersistableMapper<Proposal> {
    private final Map<Key, Contractor> contractors = new LinkedHashMap<>();

    @Override
    public Proposal mapRow(SqlRowSet rs, int rowNum) {
        var p = new Proposal();
        p.setRefs(Key.of("pr_key", rs.getLong("pr_key")));
        p.setDistance(rs.getInt("dist"));
        // Map related entity with deduplication
        if (rs.getObject("sc_key") != null) {
            var contractor = contractors.computeIfAbsent(
                Key.of("sc_key", rs.getLong("sc_key")),
                k -> {
                    var c = new Contractor("sc_key", rs.getLong("sc_key"));
                    c.setName(rs.getString("sc_name"));
                    return c;
                });
            p.setContractor(contractor);
        }
        return p;
    }
}

// Usage
var mapper = new ProposalMapper();
List<Proposal> results = repository.query(
    SqlQuery.raw("SELECT * FROM proposal p INNER JOIN contractor c ON (c.sc_key = p.sc_key)"),
    mapper
);
```

### Many-to-Many Mapper
```java
public class ProposalTaskMapper implements PersistableMapper<ProposalTask> {
    private final Map<Key, Proposal> proposals = new LinkedHashMap<>();
    private final Map<Key, Task> tasks = new LinkedHashMap<>();

    @Override
    public ProposalTask mapRow(SqlRowSet rs, int rowNum) {
        var pt = PersistableRowMapper.of(ProposalTask.class).mapRow(rs, rowNum);
        if (rs.getObject("pr_key") != null) {
            var prop = proposals.computeIfAbsent(
                Key.of("pr_key", rs.getLong("pr_key")),
                k -> PersistableRowMapper.of(Proposal.class).mapRow(rs, rowNum));
            pt.setProposal(prop);
            prop.addTask(pt); // Bidirectional
        }
        // Similar for tasks...
        return pt;
    }
    
    public List<Proposal> getProposals() { return new ArrayList<>(proposals.values()); }
}
```

### Lambda Mapper
```java
List<Proposal> results = repository.query(
    SqlQuery.raw("SELECT * FROM est_proposal"),
    (rs, row) -> {
        var e = new Proposal();
        e.setPr_key(rs.getLong("pr_key"));
        e.setDistance(rs.getInt("dist"));
        return e;
    }
);
```

## Explicit Relation Loading

Collections are not auto-loaded. Compose relationships explicitly after the base query.

```java
RelationLoader.attachOneToMany(
    orders,
    ids -> lineItemRepository.queryEntity(
        new NamedSqlQuery().where("order_id IN (:ids)").param("ids", ids),
        LineItem.class
    ),
    Order::getId,
    item -> item.getOrder().getId(),
    Order::setItems
);

RelationLoader.attachManyToOne(
    lineItems,
    ids -> orderRepository.queryEntity(
        new NamedSqlQuery().where("order_id IN (:ids)").param("ids", ids),
        Order.class
    ),
    item -> item.getOrder().getId(),
    Order::getId,
    LineItem::setOrder
);

RelationLoader.attachOneToOne(
    users,
    ids -> profileRepository.queryEntity(
        new NamedSqlQuery().where("user_id IN (:ids)").param("ids", ids),
        UserProfile.class
    ),
    User::getId,
    UserProfile::getId,
    User::setProfile
);

RelationLoader.attachManyToMany(
    groups,
    ids -> membershipRepository.query(
        NamedSqlQuery.raw(
            "SELECT * FROM group_user WHERE group_id IN (:ids)",
            Map.of("ids", ids)
        ),
        PersistableRowMapper.of(GroupUser.class)
    ),
    ids -> userRepository.queryEntity(
        new NamedSqlQuery().where("user_id IN (:ids)").param("ids", ids),
        User.class
    ),
    Group::getId,
    membership -> membership.getGroup().getId(),
    membership -> membership.getUser().getId(),
    User::getId,
    Group::setUsers
);
```

Prefer this explicit pattern over hidden lazy loading.

## Schema Validation

Use `SchemaValidator` in tests or startup validation to catch drift between entity mappings and the real schema:

```java
SchemaValidator.assertMappings(
    dataSource,
    MyEntity.class,
    OtherEntity.class
);
```

It checks:
- Table existence
- Required columns derived from getters and annotations
- Missing setters for actionable mapped fields
- Common `@Ref` / `RefValue` configuration mistakes
- Collection fields are ignored by convention

## Key Class
```java
Key.of("column_name", 123L)           // Single key
Key.of("col1", 1L).and("col2", 2L)    // Composite key
key.primaryKey()                       // Get Pair<String,Long>
key.count()                            // Number of key parts
Key.None                               // Empty key constant
```

## Common Patterns

### Foreign Key Reference (Entity)
```java
// Schema: proposal.sc_key → contractor.sc_key
@PrimaryKey("pr_key")
public class Proposal extends Model {
    @Ref private Contractor contractor;  // Stores only the FK value
}

// Setting FK on create:
proposal.setContractor(new Contractor("sc_key", 1L));
repository.save(proposal); // INSERT includes sc_key=1
```

### Foreign Key with Label (RefValue)
```java
// For dropdown/display scenarios - stores FK + fetches label
@Ref(value = "supplier_id", label = "sup_name")
private RefValue supplierRef;
// On read: RefValue { value="Acme Corp", ref=Pair("supplier_id", 5) }
```

### Composite Primary Key (Junction Table)
```java
@PrimaryKey("t_key") // One of the composite parts
public class ProposalTask extends Model {
    @Ref private Proposal proposal;
    @Ref private Task task;
}
// Insert returns Key.None (no auto-generated key)
```

### New vs Existing Entity
```java
entity.isNew()  // true if getRefs() is null or empty
// save() calls INSERT if isNew(), UPDATE otherwise
```

## Error Handling
```java
try {
    repository.save(entity);
} catch (RuntimeException e) {
    // Message: "Failed to save entity: ClassName [key=value]"
    // Wraps underlying JDBC exception
}
```

Single-result helpers throw `IllegalStateException` when more than one row matches:

```java
Optional<MyEntity> one = repository.queryOne(query, MyEntity.class);
Optional<MySummary> projection = repository.queryProjectionOne(query, MySummary.class);
Optional<String> scalar = repository.queryRow(query, rowMapper);
```

## Testing Setup
```java
@BeforeEach
void setUp() {
    db = new EmbeddedDatabaseBuilder()
        .addDefaultScripts()  // loads schema.sql, data.sql from classpath
        .setName("TestDB")
        .build();
    repository = new MyRepository(new JdbcTemplate(db));
}

@AfterEach
void tearDown() { db.shutdown(); }
```

## Agent Guidance

- Prefer `queryEntity(...)` for entity reads, `queryProjection(...)` for DTO/record reads, and `queryRow(...)` for scalar/custom row mapping.
- Prefer `NamedSqlQuery` when you need `IN (:ids)` or several repeated parameters.
- Alias projection columns to the DTO/record field names.
- Do not assume collections are persisted or loaded automatically.
- When generating startup checks or integration tests, add `SchemaValidator.assertMappings(...)`.
