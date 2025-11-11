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

    Set<String> expectedColumns;
    try {
      expectedColumns = collectExpectedColumns(entityClass);
    } catch (NullPointerException ex) {
      throw new IllegalStateException(
          "Failed to derive expected columns for " + entityClass.getName(), ex);
    }
    WithSql.getPrimaryKey(entityClass).ifPresent(pk -> expectedColumns.add(pk));
    if (entityClass.getSuperclass() != null) {
      WithSql.getPrimaryKey(entityClass.getSuperclass()).ifPresent(expectedColumns::add);
    }

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

  private static Set<String> collectExpectedColumns(Class<? extends Persistable> entityClass) {
    Set<String> columns = new LinkedHashSet<>();
    for (Method method : entityClass.getDeclaredMethods()) {
      if (!method.getName().startsWith("get")) {
        continue;
      }
      if (WithSql.getAnnotation(method, entityClass, Skip.class).isPresent()) {
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
          if (!ref.value().isBlank()) {
            columns.add(ref.value());
          }
        }
        continue;
      }

      if (refAnnotation.isPresent()) {
        String columnName =
            WithSql.getAnnotation(method, entityClass, Named.class)
                .map(Named::value)
                .orElseGet(() -> WithSql.getPrimaryKey(returnType).orElse(null));
        if (columnName != null && !columnName.isBlank()) {
          columns.add(columnName);
        }
        continue;
      }

      Optional<Named> named = WithSql.getAnnotation(method, entityClass, Named.class);
      if (named.isPresent()) {
        if (!named.get().value().isBlank()) {
          columns.add(named.get().value());
        }
      } else {
        columns.add(camelToSnake(method.getName().substring(3)));
      }
    }
    return columns;
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

  private static List<String> candidates(String name) {
    return List.of(name, name.toUpperCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
  }

  private static String resolveTableName(Class<? extends Persistable> entityClass) {
    if (entityClass.isAnnotationPresent(Named.class)) {
      return entityClass.getAnnotation(Named.class).value();
    }
    return camelToSnake(entityClass.getSimpleName());
  }

  private static String camelToSnake(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return value.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT);
  }
}
