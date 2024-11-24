# Good Enough JDBC

[![Gradle CI](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml/badge.svg)](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml)  [![Coverage](.github/badges/jacoco.svg)](jacoco.svg)  [![Branches Coverage](.github/badges/branches.svg)](branches.svg)

## Overview

`goodenough-jdbc` is a lightweight, flexible library designed for **schema-first** databases, offering a middle ground between raw SQL and heavy ORM frameworks. It enhances and simplifies the `spring-jdbc` framework, simplifying common database operations without introducing unnecessary complexity.

## Why Use Good Enough JDBC?

Modern ORM frameworks like [KTorm](https://www.ktorm.org/), [Django](https://docs.djangoproject.com/en/5.0/topics/db/), [Spring Data JPA](https://spring.io/projects/spring-data-jpa), and [Rails Active Record](https://guides.rubyonrails.org/active_record_basics.html) often:

- Abstract the RDBMS layer excessively.
- Depend on meta-programming or schema definitions in the application layer.
- Struggle with legacy or obscure designed databases.

`goodenough-jdbc` is built to address these challenges, making it easier to work with databases designed **without** application-layer ORM assumptions. It provides:

- Fine-grained control over queries and mappings.
- Ease of use for CRUD operations.
- Minimal boilerplate while avoiding runtime model generation.
- Ease of foreign relationships composition with repository pattern

## Key Features

- Annotation-based entity mapping.
- Powerful repository abstraction for common operations.
- Flexible, customizable mappers for advanced scenarios.
- Designed for **manual SQL control** where necessary.

---

## How It Works

### Entity Mapping

Entities are simple Java classes decorated with annotations for mapping database tables and columns. For example:

```java
@PrimaryKey("t_key")
public class Task extends Model {
    
    @Named("sc_name")
    public String getName() {
        return name;
    }
    
    @Ref
    public Proposal getProposal() {
        return proposal;
    }

    @Ref(value = "supplier_id", label = "sup_name")
    private RefValue supplierRef;
}
```

Key Annotations:
- **`@PrimaryKey("t_key")`**: Indicates the primary key column.
- **`@Named("sc_name")`**: Maps a column to a specific field or method.
- **`@Ref`**: Maps foreign key references, supporting lightweight lookups with `RefValue`.

If extending `Model` is not feasible, implement `Persistable` for manual control.


### Repository

The `BaseRepository` class simplifies CRUD operations:

- **`create`**, **`update`**, **`delete`**, **`list`**, and more.
- Extend `BaseRepository` to define custom queries and composite operations.

Example:

```java
var repository = new BaseRepository<>(PurchaseOrder.class);

// Fetch a unique entity by key
var result = repository.get(Key.of("sc_key", 1L), Contractor.class);

// Query entities with custom conditions
List<Proposal> results = repository.queryEntity(
    SqlQuery.withClause("WHERE dist >= ?", 10).primaryKey("pr_key"), 
    Proposal.class
);
```

### Mapping Made Simple

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


## Examples and Test Cases

Explore real-world usage scenarios through the provided test cases:

| Example                         | Reference                                                                                               |
|---------------------------------|---------------------------------------------------------------------------------------------------------|
| Composite Repository            | [ProposalTaskRepositoryTest](src/test/java/org/viablespark/persistence/ProposalTaskRepositoryTest.java) |
| Many-to-One Entity Mapping      | [ProposalTaskMapper](src/test/java/org/viablespark/persistence/ProposalTaskMapper.java)                 |
| Simple Repository Example       | [ProposalRepositoryTest](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java)         |


## Contributing

Contributions are welcome! Feel free to submit issues or pull requests to improve the library.
