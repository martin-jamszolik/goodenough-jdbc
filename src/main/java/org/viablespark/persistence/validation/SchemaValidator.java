package org.viablespark.persistence.validation;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.viablespark.persistence.Key;
import org.viablespark.persistence.Persistable;
import org.viablespark.persistence.RefValue;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.Skip;
import org.viablespark.persistence.dsl.WithSql;

/**
 * Validates annotated {@link Persistable} types against the schema exposed by a {@link
 * javax.sql.DataSource}. This is intended to provide fast feedback when mappings drift from the
 * actual database definition.
 */
public final class SchemaValidator {

  private SchemaValidator() {}

  @SafeVarargs
  public static void assertMappings(
      DataSource dataSource, Class<? extends Persistable>... entityClasses) {
    assertMappings(dataSource, Arrays.asList(entityClasses));
  }

  public static void assertMappings(
      DataSource dataSource, Collection<Class<? extends Persistable>> entityClasses) {
    Objects.requireNonNull(dataSource, "DataSource must not be null");
    Objects.requireNonNull(entityClasses, "Entity collection must not be null");

    List<String> failures = new ArrayList<>();
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      for (Class<? extends Persistable> entityClass : entityClasses) {
        validateEntity(entityClass, metaData, failures);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to validate schema mappings", ex);
    }

    if (!failures.isEmpty()) {
      throw new IllegalStateException("Schema validation failed:\n" + String.join("\n", failures));
    }
  }

  private static void validateEntity(
      Class<? extends Persistable> entityClass, DatabaseMetaData metaData, List<String> failures)
      throws SQLException {
    String configuredTable = resolveTableName(entityClass);
    Optional<String> actualTable = findTable(metaData, configuredTable);
    if (actualTable.isEmpty()) {
      failures.add(
          String.format(
              "- Table '%s' for entity %s not found", configuredTable, entityClass.getName()));
      return;
    }

    Set<String> tableColumns = loadColumns(metaData, actualTable.get());
    if (tableColumns.isEmpty()) {
      failures.add(
          String.format(
              "- No column metadata available for entity %s (table '%s')",
              entityClass.getName(), actualTable.get()));
      return;
    }

    List<String> declaredPrimaryKeys = WithSql.getPrimaryKeys(entityClass);
    PrimaryKeyMetadata primaryKeyMetadata = loadPrimaryKeys(metaData, actualTable.get());
    List<String> actualPrimaryKeys = primaryKeyMetadata.columns();
    if (!declaredPrimaryKeys.isEmpty()
        && primaryKeyMetadata.available()
        && !equalsIgnoreCase(declaredPrimaryKeys, actualPrimaryKeys)) {
      failures.add(
          String.format(
              "- Primary key for %s declares %s but table '%s' uses %s",
              entityClass.getName(), declaredPrimaryKeys, actualTable.get(), actualPrimaryKeys));
    }

    Set<String> expectedColumns;
    try {
      expectedColumns = collectExpectedColumns(entityClass, failures);
    } catch (NullPointerException ex) {
      throw new IllegalStateException(
          "Failed to derive expected columns for " + entityClass.getName(), ex);
    }
    expectedColumns.addAll(WithSql.getPrimaryKeys(entityClass));

    try {
      for (String column : expectedColumns) {
        if (column == null || column.isBlank()) {
          continue;
        }
        if (!tableColumns.contains(column.toUpperCase(Locale.ROOT))) {
          failures.add(
              String.format(
                  "- Column '%s' required by %s is missing in table '%s'",
                  column, entityClass.getName(), actualTable.get()));
        }
      }
    } catch (NullPointerException ex) {
      throw new IllegalStateException(
          "Null column encountered while validating "
              + entityClass.getName()
              + "; expected columns="
              + expectedColumns,
          ex);
    }
  }

  private static Set<String> collectExpectedColumns(
      Class<? extends Persistable> entityClass, List<String> failures) {
    Set<String> columns = new LinkedHashSet<>();
    for (Method method : WithSql.persistentGetters(entityClass)) {
      if (WithSql.getAnnotation(method, entityClass, Skip.class).isPresent()) {
        continue;
      }
      if (WithSql.isCollectionLike(method.getReturnType())) {
        continue;
      }
      if (method.getReturnType().equals(Key.class)) {
        continue;
      }

      Optional<Ref> refAnnotation = WithSql.getAnnotation(method, entityClass, Ref.class);
      Class<?> returnType = method.getReturnType();

      if (RefValue.class.equals(returnType)) {
        if (refAnnotation.isPresent()) {
          Ref ref = refAnnotation.get();
          boolean hasValue = !ref.value().isBlank();
          boolean hasLabel = !ref.label().isBlank();
          if (hasValue || hasLabel) {
            validateSetter(entityClass, method, failures);
          }
          if (hasValue != hasLabel) {
            failures.add(
                String.format(
                    "- @Ref on %s.%s using RefValue requires both value and label attributes",
                    entityClass.getName(), method.getName()));
          }
          if (hasValue) {
            columns.add(ref.value());
          }
        }
        continue;
      }

      if (refAnnotation.isPresent()) {
        Optional<Named> namedAnnotation = WithSql.getAnnotation(method, entityClass, Named.class);
        String columnName =
            namedAnnotation.isPresent()
                ? (namedAnnotation.get().value().isBlank() ? null : namedAnnotation.get().value())
                : WithSql.getPrimaryKey(returnType).orElse(null);
        if (columnName != null && !columnName.isBlank()) {
          validateSetter(entityClass, method, failures);
          columns.add(columnName);
        }
        continue;
      }

      Optional<Named> named = WithSql.getAnnotation(method, entityClass, Named.class);
      if (named.isPresent()) {
        if (!named.get().value().isBlank()) {
          validateSetter(entityClass, method, failures);
          columns.add(named.get().value());
        }
      } else {
        validateSetter(entityClass, method, failures);
        columns.add(camelToSnake(WithSql.propertyName(method)));
      }
    }
    return columns;
  }

  private static void validateSetter(
      Class<? extends Persistable> entityClass, Method getter, List<String> failures) {
    String setterName = WithSql.setterName(getter);
    try {
      WithSql.findSetter(entityClass, getter);
    } catch (NoSuchMethodException ex) {
      failures.add(
          String.format(
              "- Setter '%s' required for %s.%s is missing",
              setterName, entityClass.getName(), getter.getName()));
    }
  }

  private static Optional<String> findTable(DatabaseMetaData metaData, String tableName)
      throws SQLException {
    for (String candidate : candidates(tableName)) {
      try (ResultSet tables = metaData.getTables(null, null, candidate, new String[] {"TABLE"})) {
        if (tables.next()) {
          return Optional.ofNullable(tables.getString("TABLE_NAME"));
        }
      }
    }
    return Optional.empty();
  }

  private static Set<String> loadColumns(DatabaseMetaData metaData, String tableName)
      throws SQLException {
    Set<String> columns = new HashSet<>();
    for (String candidate : candidates(tableName)) {
      try (ResultSet rs = metaData.getColumns(null, null, candidate, null)) {
        while (rs.next()) {
          String columnName = rs.getString("COLUMN_NAME");
          if (columnName != null) {
            columns.add(columnName.toUpperCase(Locale.ROOT));
          }
        }
      }
      if (!columns.isEmpty()) {
        break;
      }
    }
    return columns;
  }

  private static PrimaryKeyMetadata loadPrimaryKeys(DatabaseMetaData metaData, String tableName)
      throws SQLException {
    Map<Integer, String> columnsBySequence = new java.util.TreeMap<>();
    boolean available = false;
    for (String candidate : candidates(tableName)) {
      ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, candidate);
      if (primaryKeys == null) {
        continue;
      }
      available = true;
      try (primaryKeys) {
        while (primaryKeys.next()) {
          columnsBySequence.put(
              primaryKeys.getInt("KEY_SEQ"), primaryKeys.getString("COLUMN_NAME"));
        }
      }
      if (!columnsBySequence.isEmpty()) {
        break;
      }
    }
    return new PrimaryKeyMetadata(
        available, columnsBySequence.values().stream().filter(Objects::nonNull).toList());
  }

  private static boolean equalsIgnoreCase(List<String> first, List<String> second) {
    if (first.size() != second.size()) {
      return false;
    }
    for (int index = 0; index < first.size(); index++) {
      if (!first.get(index).equalsIgnoreCase(second.get(index))) {
        return false;
      }
    }
    return true;
  }

  private record PrimaryKeyMetadata(boolean available, List<String> columns) {}

  private static List<String> candidates(String name) {
    return List.of(name, name.toUpperCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
  }

  private static String resolveTableName(Class<? extends Persistable> entityClass) {
    return WithSql.getEntityName(entityClass);
  }

  private static String camelToSnake(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return value.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT);
  }
}
