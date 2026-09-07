# Transactions belong to you

This library will never start, commit, or roll back a transaction. That is not a
missing feature. It is the design.

## Why

A transaction is a statement about your business: "these writes stand or fall
together." Only your application knows where that boundary lies. A persistence
library that commits on your behalf is making that decision for you — silently,
in code you didn't write, around invariants it can't see. The moment it guesses
wrong, you get the worst kind of bug: data that is *almost* right.

So here is the deal this library offers: every method does exactly the SQL it
says, against the connection you gave it, and nothing else. No ambient state, no
thread-bound sessions, no surprise commits. It behaves identically inside your
transaction or outside it. What happens around the call is entirely yours.

## What that means for you

**Wrap service operations in your own transaction, with your own tools.**
Spring's `@Transactional`, a `TransactionTemplate`, manual commit — whatever your
project uses. That code lives in your codebase, not in repositories:

```java
// YOUR code, YOUR transaction, YOUR invariant.
@Transactional
public void placeOrder(Order order, List<LineItem> items) {
    orders.save(order);
    for (var item : items) {
        item.setOrder(order);
        lineItems.save(item);
    }
}
```

**Know that `saveAll` is N units of work, not one.** It loops individual saves to
preserve generated-key behavior. Without your transaction around it, a failure
halfway through leaves half your rows committed. That is not a bug to report —
it is the documented shape of the method. If those rows must stand together, you
already know what to do (see above).

**Never reach into a repository to manage transactions.** No AOP, no connection
wrestling, no "helpful" commit-on-success wrapper around `BaseRepository`. The
day transaction logic hides inside persistence code is the day nobody can answer
"what exactly is atomic here?" — and that question must always have a boring,
obvious answer.

## This will not change

There will be no transaction API in this library, no `@Transactional` support, no
unit-of-work, no ambient transaction detection. Any contribution that manages a
transaction inside the library will be declined, kindly but firmly. Your
transactions, your rules.
