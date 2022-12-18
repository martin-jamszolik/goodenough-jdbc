# Good Enough JDBC

[![Gradle CI](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml/badge.svg)](https://github.com/martin-jamszolik/goodenough-jdbc/actions/workflows/gradle.yml) 
[![Coverage](.github/badges/jacoco.svg)](jacoco.svg)
[![branches](.github/badges/branches.svg)](branches.svg)

## Why?
Have you tried the latest/greatest ORM frameworks out there yet?

There are more than I can count, each one of them makes some kind of promise to you.  
Most of them want to remove you from the RDBMS layer as much as possible. Some use meta-programming
to generate the model in runtime, some are statically typed and require us to define the schema 
in the application layer so that a rich DSL layer can be used.

Reflecting on this, you may find yourself needing to be close to SQL and all that
it has to offer in flexibility and transparency. Tailor each query for optimal retrieval, using
your knowledge of to put together a performant query. At the same time, have a good enough api to
help you with the boring,silly stuff.  Just enough to help with the needed 
insert,update and simple select statements. Find that middle ground of convention and flexibility.
How low level can we stay and still be productive with RDBMS? This little library was born from
having to work with so many frameworks, between `spring-data` and `rails` and many other 
specialized libraries.

## How?

Clearly, we start of with the fact that we are on the JVM. Taking advantage of one of the most
prolific frameworks out there, which is `spring-jdbc`. An already slim, low level library. 
Between `spring-jdbc` and Springs next flagship library `spring-data`,
this little project sits right in between. We don't do any meta-programming (autogenerate interfaces),
we don't generate any clever queries. Instead, we help with the most boring parts and leave the power
and flexibility and a level of complexity to you. With some good guidelines, best practices and test cases, you may find this library useful.


## Details

### 1) The Entity

First major component is the Entity:
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
}
```

By convention, if your entity matches snake case names, `@Named` is not necessary. But in the real world
that is rarely true. Extending `Model` makes it convenient, but your entity might already be inheriting
from another class, so use `implements Persistable` instead.

### 2) The Repository

`BaseRepository` does most of the work to help you `create`, `remove`, `update`, `delete`, `list`. 
Beyond that, extend the class and define your desired helper methods for additional queries and composites.

### 3) The Mapper

Most mapping needs can be delegated over to `PersistableRowMapper`. Only if you want to take full control of
how every column, field and foreign relationships is mapped, you will want to implement 
`PersistableMapper` and go from there. For an advanced example of this, see 
[ProposalMapper](src/test/java/org/viablespark/persistence/ProposalMapper.java)


### Test Cases

Have a look at the various Test Cases for common uses typically found in a real scenarios.

| Example                         | Reference                                                                                               |
|---------------------------------|:--------------------------------------------------------------------------------------------------------|
| Composite Repository (Line 74+) | [ProposalTaskRepositoryTest](src/test/java/org/viablespark/persistence/ProposalTaskRepositoryTest.java) |
| Many to One Entity Mapping      | [ProposalTaskMapper](src/test/java/org/viablespark/persistence/ProposalTaskMapper.java)                 |