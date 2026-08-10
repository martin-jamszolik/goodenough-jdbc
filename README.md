# Good Enough JDBC

[![Gradle CI](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml/badge.svg)](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml)  [![Coverage](.github/badges/jacoco.svg)](.github/badges/jacoco.svg)  [![Branches Coverage](.github/badges/branches.svg)](.github/badges/branches.svg)

## Overview

`goodenough-jdbc` is a lightweight, flexible library designed for **schema-first** databases, offering a middle ground between raw SQL and heavy ORM frameworks. It elevates and simplifies the `spring-jdbc` library, streamlining common database operations with Repository-style conventions.

## Installation

The 3.x line requires Java 17 and Spring Framework 6. Spring JDBC is declared as an API
dependency because its types are part of the public repository and mapper contracts. It is not
shaded or bundled into the library, and Spring Boot/BOM dependency management can select a
compatible Spring 6 version.

GitHub Packages requires authentication, including for public packages. Configure Gradle:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/martin-jamszolik/goodenough-jdbc")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}

dependencies {
    implementation("org.viablespark:goodenough-jdbc:3.0.0")
    runtimeOnly("org.postgresql:postgresql:YOUR_DRIVER_VERSION") // Choose your JDBC driver
}
```

Store credentials outside the project in `~/.gradle/gradle.properties`:

```properties
gpr.user=GITHUB_USERNAME
gpr.key=CLASSIC_PAT_WITH_READ_PACKAGES
```

For Maven, configure a `github` server in `~/.m2/settings.xml`, then add:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/martin-jamszolik/goodenough-jdbc</url>
</repository>

<dependency>
  <groupId>org.viablespark</groupId>
  <artifactId>goodenough-jdbc</artifactId>
  <version>3.0.0</version>
</dependency>
```

## Why Use Good Enough JDBC?

Modern ORM frameworks like [KTorm](https://www.ktorm.org/), [Django](https://docs.djangoproject.com/en/5.0/topics/db/), [Spring Data JPA](https://spring.io/projects/spring-data-jpa), and [Rails Active Record](https://guides.rubyonrails.org/active_record_basics.html) often:

- Abstract the RDBMS layer excessively.
- Depend on meta-programming or schema definitions in the application layer.
- Struggle with legacy or obscure designed databases.

`goodenough-jdbc` is built to address these challenges, making it easier to work with databases designed **without** application-layer ORM assumptions. It provides:

- Fine-grained control over queries and mappings.
- Ease of use for CRUD operations.
- Minimal boilerplate while avoiding runtime model generation.
- Easy foreign relationship composition with repository pattern.
- Positional query DSL, batch operations, projections, and explicit relation attachment helpers.

## Key Features

- Annotation-based entity mapping with default conventions (e.g., snake_case mappings).
- Common repository (column) operations.
- Flexible, customizable mappers for advanced scenarios.
- Designed for **manual SQL control** where necessary.
- Collection-valued relationships are ignored by convention and loaded explicitly.
- Schema validation helpers for catching mapping drift early.

## Not All Batteries Included

- Spring JDBC is declared transitively but remains application-managed and is never shaded.
- Bring your own transaction management (e.g., Spring Transactions).
- Bring your own schema and data migration/evolution (e.g., Flyway).
- Query DSL is just a helper (e.g., SQL strings).

---

## How It Works

### Entity Mapping

If extending `Model` is not feasible, implement `Persistable` for manual control.
Entities are simple Java classes decorated with annotations for mapping database tables and columns. For example:

```java
@PrimaryKey("t_key")
public class Task extends Model {
    
    @Named("sc_name") String name;
    
    @Ref Proposal proposal;    

    @Ref(value = "supplier_id", label = "sup_name")
    private RefValue supplierRef;

    @Skip String skipMeField;

    /* Followed by getters/setters */
}
```

Key Annotations:

- **`@PrimaryKey("t_key")`**: Indicates the primary key column.
- **`@Named("sc_name")`**: Maps a column to a specific field or method.
- **`@Ref`**: Maps foreign key references, supporting lightweight lookups with `RefValue`.
- **`@Skip`**: Ignore a column(field) or a list, so you can seperate to another Repository.

Entity mappings include inherited getters and field annotations. The nearest `@Named` and
`@PrimaryKey` declarations in the class hierarchy define the table and key, while properties from
both parent and child classes map to that table. This supports either an annotated parent with an
unannotated concrete child, or an unannotated property parent with an annotated child.

Repeat `@PrimaryKey` in database key order for composite identities:

```java
@PrimaryKey("order_id")
@PrimaryKey("line_number")
public class OrderLine extends Model {
    // mapped properties
}
```

Repository get, query, update, delete, and batch operations use every key component. A single
`@Ref` still represents one foreign-key column; map references to composite identities as separate
properties so the column pairing remains explicit.

`RefValue` participates in generated CRUD through its `ref` value. Generated entity reads select
the foreign-key column and leave the display value null. A custom joined query that selects the
configured `label` column populates the display value. Setting either an entity `@Ref` or a
`RefValue` to null and saving writes SQL `NULL` to the foreign-key column.

Primary and foreign-key values may be integral numbers, `UUID`, or `String`. Existing numeric
helpers such as `getId()` and `primaryKey()` remain available; use `getIdentifier()`,
`Key.primary()`, `Key.value(...)`, and `RefValue.referenceValue(...)` for UUID or String keys.

### Repository

The `BaseRepository` class simplifies CRUD operations:

- **`create`**, **`update`**, **`delete`**, **`list`**, and more.
- **`insertAll`**, **`updateAll`**, **`deleteAll`**, **`saveAll`** for batch-oriented workflows.
- **`queryOne`**, **`exists`**, **`count`**, **`queryRows`**, **`queryRow`**, **`queryProjection`**, and **`queryProjectionOne`** for common repository reads.
- Extend `BaseRepository` to define custom, high performance queries and composite operations.

Example:

```java
var repository = new BaseRepository<>(new JdbcTemplate(dataSource)) {};

// Fetch a unique entity by key
var result = repository.get(Key.of("sc_key", 1L), Contractor.class);

// Query entities with custom conditions using SqlQuery DSL
List<Proposal> results = repository.queryEntity(
    new SqlQuery().where("dist >= ?", 10), 
    Proposal.class
);

// Query a single entity safely
Optional<Proposal> proposal = repository.queryOne(
    new SqlQuery().where("pr_key = ?", 1L),
    Proposal.class
);

// Use raw SQL when needed
List<Proposal> rawResults = repository.query(
    SqlQuery.raw("SELECT * FROM est_proposal WHERE dist > ?", 10),
    new ProposalMapper()
);

// Read DTO/record projections directly
List<ContractorSummary> summaries = repository.queryProjection(
    new SqlQuery()
        .selectColumns("sc_key as id", "sc_name as name")
        .from("contractor")
        .where("sc_key IN (?, ?)", 1L, 2L)
        .orderBy("sc_key"),
    ContractorSummary.class
);
```

### Collection Relationships

Collection-valued getters such as `List<Task>` are **ignored by convention**. They are not mapped from the base row, they are not included in generated insert/update SQL, and they are not validated against table columns.

This keeps relation loading explicit and predictable:

```java
var proposals = proposalRepository.queryEntity(
    new SqlQuery().where("sc_key = ?", 1L),
    Proposal.class
);

RelationLoader.attachOneToMany(
    proposals,
    ids -> proposalTaskRepository.query(
        SqlQuery.statement("SELECT * FROM proposal_task WHERE pr_key IN (?, ?)", ids.get(0), ids.get(1)),
        PersistableRowMapper.of(ProposalTask.class)
    ),
    proposal -> proposal.getRefs().primaryKey().getValue(),
    proposalTask -> proposalTask.getProposal().getRefs().primaryKey().getValue(),
    Proposal::setTasks
);
```

Build `IN` placeholder lists and their values explicitly for variable-size batches. Chunk large ID
lists according to your database's parameter limit.

Use `@Skip` when you need to omit a scalar property or a relationship for a custom reason. You no longer need it for `List`, `Set`, or `Collection` properties.

### SqlQuery DSL

The `SqlQuery` class provides a fluent API for building programmatic SQL queries:

```java
// Composed query with WHERE clause
new SqlQuery()
    .where("dist >= ?", 10)
    .orderBy("dist", Direction.DESC)
    .limit(5);

// Combining conditions with AND/OR
new SqlQuery()
    .where("status = ?", "active")
    .andWhere("amount > ?", 1000)
    .orWhere("priority = ?", "high");

// Select specific columns
new SqlQuery()
    .selectColumns("id", "name", "status")
    .from("proposals")
    .where("created_date > ?", LocalDate.now().minusDays(30));

// Complete SQL statement for complex scenarios
SqlQuery.statement(
    "SELECT * FROM proposal p " +
    "INNER JOIN contractor c ON (p.sc_key = c.sc_key) " +
    "WHERE p.dist > ?", 
    10
);
```

Key Methods:

- **`where()`**, **`andWhere()`**, **`orWhere()`**: Build WHERE conditions with parameter binding
- **`selectColumns()`**, **`selectDistinct()`**: Specify columns to retrieve
- **`from()`**, **`join()`**: Define table expressions and joins
- **`orderBy()`**: Sort results by column or expression
- **`limit()`**, **`offset()`**, **`paginate()`**: Control result pagination
- **`SqlQuery.statement()`**: Use a complete SQL statement for projections and custom rows
- **`SqlQuery.fragment()`**: Use a raw query fragment with `queryEntity`, `queryOne`, or `count`

Legacy `SqlQuery.raw()` works in either context for compatibility. Prefer `statement()` and
`fragment()` in new code so context mistakes are rejected before execution.

### Batch Operations

`BaseRepository` includes explicit batch helpers for repetitive write operations:

```java
int[] inserted = contractorRepository.insertAll(List.of(first, second));
int[] updated = contractorRepository.updateAll(List.of(first, second));
int[] deleted = contractorRepository.deleteAll(List.of(first, second));

// saveAll keeps per-entity save semantics when you need generated keys back
List<Optional<Key>> keys = contractorRepository.saveAll(List.of(first, second));
```

`insertAll`, `updateAll`, and `deleteAll` use JDBC batching. `saveAll` executes one save per entity
so generated keys remain available. None of these methods starts a transaction; wrap multi-step
work in your transaction manager when atomicity is required.

### Projections And Single-Row Reads

For read models, DTOs, and API-facing shapes, prefer projections over entity overloading:

```java
record ContractorSummary(Long id, String name) {}

Optional<ContractorSummary> contractor = contractorRepository.queryProjectionOne(
    SqlQuery.statement(
        "SELECT sc_key as id, sc_name as name FROM contractor WHERE sc_key = ?",
        1L
    ),
    ContractorSummary.class
);

Optional<String> contractorName = contractorRepository.queryRow(
    new SqlQuery()
        .selectColumns("sc_name")
        .from("contractor")
        .where("sc_key = ?", 2L),
    (rs, rowNum) -> rs.getString("sc_name")
);
```

`queryOne(...)`, `queryRow(...)`, and `queryProjectionOne(...)` enforce single-result semantics and return `Optional`.

### Schema Validation

Use `SchemaValidator` in tests or startup checks to catch mapping drift early:

```java
SchemaValidator.assertMappings(
    dataSource,
    Contractor.class,
    Proposal.class,
    Note.class
);
```

It validates table presence, required columns, primary-key composition, and common annotation
mistakes such as missing setters or inconsistent `@Ref`/`RefValue` configuration.


### Mapping Helper

Leverage `PersistableRowMapper` for efficient entity mapping:

```java
// Easy-to-use row mapper
var mapper = PersistableRowMapper.of(PurchaseOrder.class);

// For advanced composites, use custom mappers
var results = repository.query(
    SqlQuery.statement("SELECT * FROM est_proposal p " +
                    "INNER JOIN contractor c ON (c.sc_key = p.sc_key) " +
                    "WHERE dist > 0"),
    new ProposalMapper()
);
```

- For most use cases, `PersistableRowMapper` is sufficient.
- Use `PersistableMapper` for custom scalar, DTO, or entity mappings.

**Advanced Example:**  
See the [ProposalMapper](src/test/java/org/viablespark/persistence/ProposalMapper.java) for a detailed example of custom mapping.

## Notes on Java Compatibility

We will maintain Java 11/Spring 5 compatibility for as long as it allows us to retain core functionality. For Java 11/Spring 5 support, see the [1.x-java-11](https://github.com/martin-jamszolik/goodenough-jdbc/tree/1.x-java-11) branch.

We test the library with Kotlin for compatibility with data classes and common JSON marshallers for easy exposure over REST controllers/endpoints.

---

## Examples and Test Cases

The test suite demonstrates real-world usage patterns covering common development scenarios:

| Use Case | Test Method | Description |
|----------|-------------|-------------|
| **Save & Update Entity** | [`testSave()`](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java#L58) | Insert new entity with foreign key reference and update existing record |
| **Delete Entity** | [`testDelete()`](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java#L93) | Remove entity from database by key |
| **Retrieve by Key** | [`testGet()`](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java#L100) | Fetch single entity using primary key |
| **Query with Conditions** | [`testQuery()`](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java#L106) | Filter entities using SqlQuery WHERE clause with parameters |
| **Join Multiple Tables** | [`testGetProposalWithTasks()`](src/test/java/org/viablespark/persistence/ProposalTaskRepositoryTest.java#L32) | Execute multi-table JOIN query to map many-to-many relationships |
| **Custom Row Mapper** | [`testRowQuery()`](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java#L115) | Use custom mapper to handle JOIN queries with related entities |
| **Manual Row Mapping** | [`testRowSetQuery()`](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java#L120) | Map result sets manually using lambda expressions |
| **Insert with Foreign Key** | [`testInsertNote()`](src/test/java/org/viablespark/persistence/NoteRepositoryTest.java#L48) | Create entity with nested foreign key relationships |
| **Select with Relations** | [`testSelectNote()`](src/test/java/org/viablespark/persistence/NoteRepositoryTest.java#L53) | Retrieve entity and verify foreign key references are populated |
| **Query DSL & Projections** | [`RepositoryEnhancementsTest`](src/test/java/org/viablespark/persistence/RepositoryEnhancementsTest.java) | Positional query builder, single-row helpers, and DTO projection reads |
| **Explicit Relation Loading** | [`RelationLoaderTest`](src/test/java/org/viablespark/persistence/RelationLoaderTest.java) | One-to-many, many-to-one, one-to-one, and many-to-many attachment patterns |
| **Schema Validation** | [`SchemaValidatorTest`](src/test/java/org/viablespark/persistence/validation/SchemaValidatorTest.java) | Validate mappings, relation metadata, and setter requirements |
| **Query with Primary Key** | [`testQueryNote()`](src/test/java/org/viablespark/persistence/NoteRepositoryTest.java#L61) | Query entities using SqlQuery with primary key specification |
| **Many-to-Many Mapping** | [`testInsertWithPKnoAutoGenerate()`](src/test/java/org/viablespark/persistence/ProposalTaskRepositoryTest.java#L45) | Handle junction table with composite primary keys (no auto-generation) |
| **Validate Constraints** | [`testSaveContractorThrowsException()`](src/test/java/org/viablespark/persistence/ContractorRepositoryTest.java#L69) | Handle database constraint violations gracefully |
| **Full CRUD Workflow** | [`testSaveContractor()`](src/test/java/org/viablespark/persistence/ContractorRepositoryTest.java#L44) | Complete create-retrieve-verify workflow |

For advanced mapping patterns, see:

- [ProposalMapper](src/test/java/org/viablespark/persistence/ProposalMapper.java) - One-to-many relationship mapping
- [ProposalTaskMapper](src/test/java/org/viablespark/persistence/ProposalTaskMapper.java) - Many-to-many relationship mapping

---

## Using LLM Coding Agents Effectively

This library works exceptionally well with LLM coding agents (GitHub Copilot, Cursor, etc.). For optimal results, point your agent to **[llm.md](llm.md)** - a compact reference guide specifically designed for code generation agents with all essential patterns and examples.

### Quick Tips

1. **Share Your Database Schema** - Provide DDL so the agent understands your table structure and relationships
2. **Reference llm.md** - Include it in your agent's context for accurate code generation
3. **Start Simple** - Entity classes first, then repository, then custom queries

### Example Schema Context

```sql
-- Example: share your schema.sql or migration files
CREATE TABLE contractor (
    sc_key BIGINT PRIMARY KEY AUTO_INCREMENT,
    sc_name VARCHAR(100) NOT NULL,
    contact VARCHAR(100),
    phone_1 VARCHAR(20),
    email VARCHAR(100)
);

CREATE TABLE est_proposal (
    pr_key BIGINT PRIMARY KEY AUTO_INCREMENT,
    sc_key BIGINT,
    proposal_name VARCHAR(200),
    dist INT,
    submit_deadline DATE,
    FOREIGN KEY (sc_key) REFERENCES contractor(sc_key)
);
```

### Sample Prompt for Agents

```text
I'm using goodenough-jdbc for database operations. 
Please read llm.md for the library reference.

Here's my schema:
[paste schema.sql]

I need to:
1. Create an entity class for the 'order_items' table
2. Create a repository with a custom query to find items by order_id and status
3. Handle the foreign key relationship to 'orders' table
```

---

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests to improve the library.
