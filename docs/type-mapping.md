# Speak the driver's language

The library is deliberately thin about types. It converts a little on the way in,
and almost nothing on the way out. Once you internalize that, the whole table
below becomes obvious instead of something to memorize.

## The two paths

**Reading** (`PersistableRowMapper`) is forgiving: it pulls values with
`getObject` and converts the common shapes — integers between widths, booleans
(including `"true"` strings), and dates/timestamps into `LocalDate`. If your
field is one of those, mapping just works.

**Writing** is brutally honest: your values go to the driver via `setObject`
exactly as they are. Whatever your JDBC driver does with a value is what
happens. There is no conversion layer to save you, because a conversion layer
would be guessing about your database — and this library doesn't guess.

## The table

| Your column is...          | Your field should be...      | Why                                              |
|----------------------------|------------------------------|--------------------------------------------------|
| INTEGER / BIGINT           | `Long`, `Integer`, `int`     | Read path converts between integer widths.       |
| DECIMAL / NUMERIC          | `BigDecimal`                 | Never `float`/`double` for money. Ever.          |
| BOOLEAN / BIT              | `Boolean`, `boolean`         | Read path handles booleans and `"true"` strings. |
| VARCHAR / CHAR / TEXT      | `String`                     | —                                                |
| UUID                       | `java.util.UUID`             | Generate it yourself before `save()` (below).    |
| DATE                       | `LocalDate`                  | `java.sql.Date` converts cleanly.                |
| TIMESTAMP                  | `LocalDateTime`, `LocalDate` | Works; verify truncation behavior on your DB.    |
| An enum hiding in VARCHAR  | `String` + conversion at the edges | See below — this one bites.               |
| JSON / JSONB               | `String` + conversion at the edges | Serialize outside the mapper.             |

## The enum trap

Java enums are **not** converted. If you pass one straight through, you're
relying on unspecified driver behavior — and the classic failure is storing
ordinals, reordering the enum six months later, and silently corrupting every
row. Model enum-like columns as `String` (or `int`, if the schema demands it),
convert to your enum at the edges of your code, and sleep well. If you need
something fancier, that's what a custom `PersistableMapper` is for — your code,
your rules.

## Keys have opinions too

Key components may be integral numbers, `UUID`, or `String` — nothing else,
enforced by `Key` itself. Two habits follow from this:

1. **Pre-generate assigned keys client-side.** Set `UUID.randomUUID()` on the
   insertable mapped key property, leaving refs empty, then `save()`. The insert
   path derives a complete assigned key from mapped properties without requesting
   generated keys. Do not use `setIdentifier`/`setRefs` for a new row: those set
   identity and make `save()` choose UPDATE. `@Write(updatable = false)` can keep
   an assigned key insertable while excluding it from full updates; see
   [partial writes](partial-writes.md).
2. **Use `getIdentifier()`, not `getId()`, for non-numeric keys.**
   `getId()` throws for UUID/String identifiers *on purpose* — it's a
   `Long`-shaped convenience, and pretending otherwise would just move the
   `ClassCastException` somewhere harder to find. `getIdentifier(UUID.class)`
   says what you mean.
