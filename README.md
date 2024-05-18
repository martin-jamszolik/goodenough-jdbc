# Good Enough JDBC

[![Gradle CI](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml/badge.svg)](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml) 
[![Coverage](.github/badges/jacoco.svg)](jacoco.svg)
[![branches](.github/badges/branches.svg)](branches.svg)

## Why?
Have you tried the latest ORM frameworks? They abstract the RDBMS layer extensively, using meta-programming to generate models at runtime or requiring schemas defined in the application layer for a rich DSL. Examples include:
* [KTorm](https://www.ktorm.org/) (Schema-driven with Kotlin DSL)
* [Django](https://docs.djangoproject.com/en/5.0/topics/db/) (Migrations, models, Python DSL)
* [Spring Data JPA](https://spring.io/projects/spring-data-jpa) (JPA, boilerplate galore)
* [Rails Active Record](https://guides.rubyonrails.org/active_record_basics.html) (Meta-programming, Active Record as an ORM)

`goodenough-jdbc` stems from my experience with **schema-first** designed databases. Rich ORM frameworks are hard to adopt for old databases designed **without** application frameworks in mind. This library balances low-level flexibility and abstraction.

## How?

Inspired by other language communities, this JVM-based project leverages the `spring-jdbc` framework.
It sits above `spring-jdbc` but below `spring-data`, avoiding meta-programming and clever query generation.
Instead, it helps with the most tedious parts while providing flexibility and complexity control. 
With good guidelines, best practices, and test cases, this library might be useful to you.


## Implementation Details

### The Entity

Like with most ORMs, there is a notion of an Entity to help you decorate your object:
```java
@PrimaryKey("t_key")
public class Task extends Model {
    
    @Named("sc_name")
    public String getName() {
        return name;
    }
    
    @Ref
    public Proposal getProposal(){
        return proposal;
    }
    @Ref(value="supplier_id",label="sup_name")
    private RefValue supplierRef;
}
```
* `@PrimaryKey("t_key")` - assist in CRUD and Queries
* `@Named("sc_name")` - mapping between columns and fields
* `@Ref(value="supplier_id",label="sup_name")` - assist with foreign references.
*  `RefValue` object is especially convenient for lookup queries. You want the foreign value not the entire Data Reference.

Extending `Model` makes it convenient, but if unable, use `implements Persistable` instead.

### The Repository

`BaseRepository` assist with `create`, `remove`, `update`, `delete`, `list`. 
Otherwise, extend the class and define your desired helper methods for additional queries and composites for full flexibility.

### The Mapper

The best stuff is here:

```java
//So may goodies added here on top of Spring's RowMapper class.
var mapper = PersistableRowMapper.of(PurchaseOrder.class);

//Fetching unique Entity, easy!
var res = repository.get(Key.of("sc_key",1L),Contractor.class);

//Fetching a set with a query 
List<Proposal> results = repository.queryEntity(SqlQuery
        .withClause("WHERE dist >= ?", 10)
        .primaryKey("pr_key"), Proposal.class);

//Need a bit more control over rich composites?
var mapper = new ProposalMapper();
var results = repository.query(
    SqlQuery.asRawSql("select * from est_proposal p " +
        "INNER JOIN contractor c on (c.sc_key = p.sc_key) "+
        "where dist > 0"), mapper);
```
Most mapping needs can be delegated over to `PersistableRowMapper`. Only if you want to take full control of
how every column, field and foreign relationships is mapped, you will want to implement 
`PersistableMapper` and go from there. For an advanced example of this, see 
[ProposalMapper](src/test/java/org/viablespark/persistence/ProposalMapper.java) This is the most laborious 
part of the library, queries will need to match entities fields, so often you will have to spell out
the select statement with `column as renamed`.  A tradeoff I found `good-enough` to retain fine control.


### Test Cases

Have a look at the various Test Cases for common uses typically found in a real scenarios.

| Example                         | Reference                                                                                               |
|---------------------------------|:--------------------------------------------------------------------------------------------------------|
| Composite Repository (Line 74+) | [ProposalTaskRepositoryTest](src/test/java/org/viablespark/persistence/ProposalTaskRepositoryTest.java) |
| Many to One Entity Mapping      | [ProposalTaskMapper](src/test/java/org/viablespark/persistence/ProposalTaskMapper.java)                 |
| Simple Repository Example       | [ProposalRepositoryTest](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java)         |
 