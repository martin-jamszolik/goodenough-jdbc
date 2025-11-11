# Good Enough JDBC

[![Gradle CI](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml/badge.svg)](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml)  [![Coverage](.github/badges/jacoco.svg)](jacoco.svg)  [![Branches Coverage](.github/badges/branches.svg)](branches.svg)

## Overview

`goodenough-jdbc` is a lightweight, flexible library designed for **schema-first** databases, offering a middle ground between raw SQL and heavy ORM frameworks. It elevates and simplifies the `spring-jdbc` library, streamlining common database operations with Repository-style conventions.

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

## Key Features

- Annotation-based entity mapping with default conventions (e.g., snake_case mappings).
- Common repository (column) operations.
- Flexible, customizable mappers for advanced scenarios.
- Designed for **manual SQL control** where necessary.

## Not All Batteries Included

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

### Repository

The `BaseRepository` class simplifies CRUD operations:

- **`create`**, **`update`**, **`delete`**, **`list`**, and more.
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

// Use raw SQL when needed
List<Proposal> rawResults = repository.query(
    SqlQuery.raw("SELECT * FROM est_proposal WHERE dist > ?", 10),
    new ProposalMapper()
);
```

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

// Raw SQL for complex scenarios
SqlQuery.raw(
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
- **`SqlQuery.raw()`**: Use raw SQL for complex queries
- **`primaryKey()`**: Specify primary key for entity mapping


### Mapping Helper

Leverage `PersistableRowMapper` for efficient entity mapping:

```java
// Easy-to-use row mapper
var mapper = PersistableRowMapper.of(PurchaseOrder.class);

// For advanced composites, use custom mappers
var results = repository.query(
    SqlQuery.asRaw("SELECT * FROM est_proposal p " +
                      "INNER JOIN contractor c ON (c.sc_key = p.sc_key) " +
                      "WHERE dist > 0"),
    new ProposalMapper()
);
```

- For most use cases, `PersistableRowMapper` is sufficient.
- Use custom implementations of `PersistableMapper` for complex mappings.

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
| **Query with Primary Key** | [`testQueryNote()`](src/test/java/org/viablespark/persistence/NoteRepositoryTest.java#L61) | Query entities using SqlQuery with primary key specification |
| **Many-to-Many Mapping** | [`testInsertWithPKnoAutoGenerate()`](src/test/java/org/viablespark/persistence/ProposalTaskRepositoryTest.java#L45) | Handle junction table with composite primary keys (no auto-generation) |
| **Validate Constraints** | [`testSaveContractorThrowsException()`](src/test/java/org/viablespark/persistence/ContractorRepositoryTest.java#L69) | Handle database constraint violations gracefully |
| **Full CRUD Workflow** | [`testSaveContractor()`](src/test/java/org/viablespark/persistence/ContractorRepositoryTest.java#L44) | Complete create-retrieve-verify workflow |

For advanced mapping patterns, see:

- [ProposalMapper](src/test/java/org/viablespark/persistence/ProposalMapper.java) - One-to-many relationship mapping
- [ProposalTaskMapper](src/test/java/org/viablespark/persistence/ProposalTaskMapper.java) - Many-to-many relationship mapping

---

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests to improve the library.
