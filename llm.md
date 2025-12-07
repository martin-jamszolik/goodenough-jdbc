# goodenough-jdbc - LLM Agent Instructions

## Overview
Lightweight schema-first JDBC library built on `spring-jdbc`. Maps entities via annotations; provides repository CRUD and `SqlQuery` DSL.

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
- `Model` provides `Key getRefs()/setRefs()` and `Long getId()/setId()`

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
