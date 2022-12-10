# Good Enough JDBC

## Why?
Have you tried the latest/greatest ORM frameworks out there yet?

There are more than I can count, each one of them makes some kind of promise to you.  
Most of them want to remove you from the RDBMS layer as much as possible. Some use meta-programming
to generate the model in runtime, some are statically typed and require us to define the schema 
in the application layer so that a rich DSL layer can be used.

Reflecting on this entire space, you may find yourself needing to be close to SQL and all that
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
and flexibility and a level of complexity to you. This project will not protect you from doing silly stuff,
but, with some good guidelines, best practices and test cases, you may find this library useful.


## Details

Best way to start, is to look at one of our test cases. 
[ProposalRepositoryTest](src/test/java/org/viablespark/persistence/ProposalRepositoryTest.java)
A repository pattern demonstrating common use cases with entity storage
and foreign key relationship.