/*
 * Copyright (c) 2023 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.viablespark.persistence.dsl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.viablespark.persistence.Key;
import org.viablespark.persistence.Pair;
import org.viablespark.persistence.Persistable;
import org.viablespark.persistence.RefValue;

public final class WithSql {

  private static final ClassValue<List<Method>> PERSISTENT_GETTERS =
      new ClassValue<>() {
        @Override
        protected List<Method> computeValue(Class<?> type) {
          return discoverPersistentGetters(type);
        }
      };
  private static final ClassValue<List<String>> PRIMARY_KEYS =
      new ClassValue<>() {
        @Override
        protected List<String> computeValue(Class<?> type) {
          return discoverPrimaryKeys(type);
        }
      };
  private static final ClassValue<String> ENTITY_NAMES =
      new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
          return discoverEntityName(type);
        }
      };

  public WithSql() {}

  public static String getSelectClause(Class<?> cls, String... customFields) {
    Set<String> columns = new LinkedHashSet<>();
    if (customFields != null) {
      Arrays.stream(customFields)
          .filter(field -> field != null && !field.isBlank())
          .forEach(columns::add);
    }

    persistentGetters(cls).stream()
        .filter(method -> getAnnotation(method, cls, Skip.class).isEmpty())
        .filter(method -> !isCollectionLike(method.getReturnType()))
        .filter(method -> !method.getReturnType().equals(Key.class))
        .map(method -> deriveNameForSelectClause(method, cls))
        .flatMap(Optional::stream)
        .forEach(columns::add);

    if (columns.isEmpty()) {
      throw new IllegalArgumentException("No selectable properties found for " + cls.getName());
    }
    return String.join(",", columns);
  }

  public static SqlClause getUpdateClause(Persistable entity) throws SQLException {
    try {
      List<Method> methods = writableGetters(entity.getClass());
      StringBuilder sql = new StringBuilder("SET ");
      List<Object> answers = new ArrayList<>(methods.size() + entity.getRefs().count());
      for (Method method : methods) {
        Optional<String> derivedName = deriveName(method, entity.getClass());
        if (derivedName.isPresent()) {
          sql.append(derivedName.orElseThrow()).append("=?,");
          answers.add(deriveValue(method, entity));
        }
      }
      if (answers.isEmpty()) {
        throw new IllegalArgumentException(
            "No updatable properties found for " + entity.getClass().getName());
      }
      sql.deleteCharAt(sql.length() - 1);
      validateKey(entity.getClass(), entity.getRefs());
      appendKeyPredicate(sql, answers, entity.getRefs());
      return new SqlClause(sql.toString(), answers.toArray());
    } catch (Exception ex) {
      throw new SQLException("Failed to create an update SQL clause", ex);
    }
  }

  public static SqlClause getInsertClause(Persistable entity) throws SQLException {
    try {
      List<Method> methods = writableGetters(entity.getClass());
      List<String> columnNames = new ArrayList<>(methods.size());
      List<Object> answers = new ArrayList<>(methods.size());
      for (Method method : methods) {
        Optional<String> derivedName = deriveName(method, entity.getClass());
        if (derivedName.isPresent()) {
          columnNames.add(derivedName.orElseThrow());
          answers.add(deriveValue(method, entity));
        }
      }
      if (columnNames.isEmpty()) {
        throw new IllegalArgumentException(
            "No insertable properties found for " + entity.getClass().getName());
      }
      String placeholders =
          String.join(",", java.util.Collections.nCopies(columnNames.size(), "?"));
      String sql = "(" + String.join(",", columnNames) + ") VALUES (" + placeholders + ")";
      return new SqlClause(sql, answers.toArray());
    } catch (Exception ex) {
      throw new SQLException("Failed to create an insert SQL clause", ex);
    }
  }

  /** Derives an assigned/composite key from properties that map to declared key columns. */
  public static Key getEntityKey(Persistable entity) throws Exception {
    List<String> primaryKeys = getPrimaryKeys(entity.getClass());
    if (primaryKeys.isEmpty()) {
      return Key.None;
    }

    Map<String, Object> valuesByColumn = new LinkedHashMap<>();
    for (Method method : writableGetters(entity.getClass())) {
      Optional<String> column = deriveName(method, entity.getClass());
      if (column.isPresent()) {
        valuesByColumn.put(column.orElseThrow(), deriveValue(method, entity));
      }
    }

    Key key = new Key();
    for (String primaryKey : primaryKeys) {
      Object value = valuesByColumn.get(primaryKey);
      if (value != null) {
        key.add(primaryKey, value);
      }
    }
    return key;
  }

  private static List<Method> writableGetters(Class<?> cls) {
    return persistentGetters(cls).stream()
        .filter(method -> getAnnotation(method, cls, Skip.class).isEmpty())
        .filter(method -> !isCollectionLike(method.getReturnType()))
        .filter(method -> !method.getReturnType().equals(Key.class))
        .filter(
            method ->
                !method.getReturnType().equals(RefValue.class)
                    || validRefValue(method, cls).isPresent())
        .toList();
  }

  private static Optional<String> deriveName(Method method, Class<?> entityClass) {
    Optional<Named> named = getAnnotation(method, entityClass, Named.class);
    Optional<Ref> ref = getAnnotation(method, entityClass, Ref.class);
    Optional<PrimaryKey> primaryKey = getAnnotation(method, entityClass, PrimaryKey.class);

    if (method.getReturnType().equals(RefValue.class)) {
      return validRefValue(method, entityClass).map(Ref::value);
    }
    if (primaryKey.isPresent()) {
      return blankToEmpty(primaryKey.orElseThrow().value());
    }
    if (named.isPresent()) {
      return blankToEmpty(named.orElseThrow().value());
    }
    if (ref.isPresent()) {
      if (!Persistable.class.isAssignableFrom(method.getReturnType())) {
        throw new IllegalArgumentException(
            "@Ref property "
                + entityClass.getName()
                + "."
                + method.getName()
                + " must return Persistable or RefValue");
      }
      List<String> referencedKeys = getPrimaryKeys(method.getReturnType());
      if (referencedKeys.size() > 1) {
        throw new IllegalArgumentException(
            "@Ref property "
                + entityClass.getName()
                + "."
                + method.getName()
                + " targets a composite key; map its columns as separate properties");
      }
      return referencedKeys.stream().findFirst();
    }
    return Optional.of(camelToSnake(propertyName(method)));
  }

  private static Object deriveValue(Method method, Persistable entity) throws Exception {
    Optional<Ref> ref = getAnnotation(method, entity.getClass(), Ref.class);
    if (!method.canAccess(entity)) {
      method.trySetAccessible();
    }
    Object value = method.invoke(entity);
    if (ref.isEmpty()) {
      return value;
    }
    if (method.getReturnType().equals(RefValue.class)) {
      RefValue reference = (RefValue) value;
      return reference == null ? null : reference.referenceValue();
    }
    Persistable referencedEntity = (Persistable) value;
    if (referencedEntity == null
        || referencedEntity.getRefs() == null
        || referencedEntity.getRefs().count() == 0) {
      return null;
    }
    return referencedEntity.getRefs().primary().getValue();
  }

  private static Optional<String> deriveNameForSelectClause(Method method, Class<?> cls) {
    Optional<Named> named = getAnnotation(method, cls, Named.class);
    Optional<Ref> ref = getAnnotation(method, cls, Ref.class);
    Optional<PrimaryKey> primaryKey = getAnnotation(method, cls, PrimaryKey.class);

    if (method.getReturnType().equals(RefValue.class)) {
      return validRefValue(method, cls).map(Ref::value);
    }
    if (primaryKey.isPresent()) {
      Optional<String> column = blankToEmpty(primaryKey.orElseThrow().value());
      return column.map(value -> value + " as \"" + camelToSnake(propertyName(method)) + "\"");
    }
    if (named.isPresent()) {
      Optional<String> column = blankToEmpty(named.orElseThrow().value());
      if (column.isEmpty()) {
        return Optional.empty();
      }
      return ref.isPresent()
          ? column
          : Optional.of(
              column.orElseThrow() + " as \"" + camelToSnake(propertyName(method)) + "\"");
    }
    if (ref.isPresent()) {
      List<String> referencedKeys = getPrimaryKeys(method.getReturnType());
      if (referencedKeys.size() > 1) {
        throw new IllegalArgumentException(
            "@Ref property "
                + cls.getName()
                + "."
                + method.getName()
                + " targets a composite key; map its columns as separate properties");
      }
      return referencedKeys.stream().findFirst();
    }
    return Optional.of(camelToSnake(propertyName(method)));
  }

  private static Optional<Ref> validRefValue(Method method, Class<?> cls) {
    return getAnnotation(method, cls, Ref.class)
        .filter(ref -> ref.value() != null && !ref.value().isBlank());
  }

  private static void appendKeyPredicate(StringBuilder sql, List<Object> values, Key key) {
    if (key == null || key.count() == 0) {
      throw new IllegalArgumentException("An update requires at least one key column");
    }
    sql.append(" WHERE ");
    boolean first = true;
    for (Pair<String, Object> part : key.parts()) {
      if (!first) {
        sql.append(" AND ");
      }
      sql.append(part.getKey()).append("=?");
      values.add(part.getValue());
      first = false;
    }
  }

  /** Rejects incomplete or unrelated keys before a repository mutation can execute. */
  public static void validateKey(Class<?> entityClass, Key key) {
    if (key == null || key.count() == 0) {
      throw new IllegalArgumentException("A repository key must contain at least one column");
    }
    List<String> declared = getPrimaryKeys(entityClass);
    if (declared.isEmpty()) {
      return;
    }
    List<String> supplied = key.parts().stream().map(Pair::getKey).toList();
    boolean matches =
        declared.size() == supplied.size()
            && declared.stream()
                .allMatch(expected -> supplied.stream().anyMatch(expected::equalsIgnoreCase));
    if (!matches) {
      throw new IllegalArgumentException(
          "Key for " + entityClass.getName() + " must contain exactly " + declared);
    }
    if (key.parts().stream().anyMatch(part -> part.getValue() == null)) {
      throw new IllegalArgumentException("Key for " + entityClass.getName() + " contains null");
    }
  }

  /** Returns mapped accessors from the complete entity hierarchy, with child overrides winning. */
  public static List<Method> persistentGetters(Class<?> cls) {
    return PERSISTENT_GETTERS.get(cls);
  }

  private static List<Method> discoverPersistentGetters(Class<?> cls) {
    Map<String, Method> methodsByProperty = new LinkedHashMap<>();
    for (Class<?> current = cls;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      Arrays.stream(current.getDeclaredMethods())
          .filter(WithSql::isGetter)
          .sorted(Comparator.comparing(Method::getName))
          .forEach(method -> methodsByProperty.putIfAbsent(propertyName(method), method));
    }
    return methodsByProperty.values().stream()
        .sorted(Comparator.comparing(Method::getName))
        .toList();
  }

  private static boolean isGetter(Method method) {
    if (!Modifier.isPublic(method.getModifiers())
        || Modifier.isStatic(method.getModifiers())
        || method.isBridge()
        || method.isSynthetic()
        || method.getParameterCount() != 0
        || method.getReturnType().equals(void.class)) {
      return false;
    }
    if (method.getName().startsWith("get") && method.getName().length() > 3) {
      return true;
    }
    return method.getName().startsWith("is")
        && method.getName().length() > 2
        && (method.getReturnType().equals(boolean.class)
            || method.getReturnType().equals(Boolean.class));
  }

  public static String propertyName(Method method) {
    int prefixLength = method.getName().startsWith("is") ? 2 : 3;
    String name = method.getName().substring(prefixLength);
    if (name.length() > 1
        && Character.isUpperCase(name.charAt(0))
        && Character.isUpperCase(name.charAt(1))) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  public static String setterName(Method getter) {
    String property = propertyName(getter);
    return "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
  }

  public static Method findSetter(Class<?> cls, Method getter) throws NoSuchMethodException {
    return cls.getMethod(setterName(getter), getter.getReturnType());
  }

  public static <T extends Annotation> Optional<T> getAnnotation(
      Method method, Class<?> cls, Class<T> annotation) {
    String fieldName = propertyName(method);
    for (Class<?> current = cls;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      try {
        Method candidate = current.getDeclaredMethod(method.getName(), method.getParameterTypes());
        if (candidate.isAnnotationPresent(annotation)) {
          return Optional.of(candidate.getAnnotation(annotation));
        }
      } catch (NoSuchMethodException ignored) {
        // The accessor may be inherited without an override.
      }
      try {
        Field field = current.getDeclaredField(fieldName);
        if (field.isAnnotationPresent(annotation)) {
          return Optional.of(field.getAnnotation(annotation));
        }
      } catch (NoSuchFieldException ignored) {
        // The property may be declared by another class in the hierarchy.
      }
    }
    return Optional.empty();
  }

  /** Resolves the nearest primary-key declaration; repeated annotations define key order. */
  public static List<String> getPrimaryKeys(Class<?> cls) {
    return PRIMARY_KEYS.get(cls);
  }

  private static List<String> discoverPrimaryKeys(Class<?> cls) {
    for (Class<?> current = cls;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      List<String> declared =
          Arrays.stream(current.getDeclaredAnnotationsByType(PrimaryKey.class))
              .map(PrimaryKey::value)
              .filter(value -> value != null && !value.isBlank())
              .toList();
      if (!declared.isEmpty()) {
        return declared;
      }
    }

    List<String> propertyKeys = new ArrayList<>();
    for (Method method : persistentGetters(cls)) {
      getAnnotation(method, cls, PrimaryKey.class)
          .map(PrimaryKey::value)
          .filter(value -> !value.isBlank())
          .ifPresent(propertyKeys::add);
    }
    return List.copyOf(propertyKeys);
  }

  public static Optional<String> getPrimaryKey(Class<?> cls) {
    return getPrimaryKeys(cls).stream().findFirst();
  }

  /** Resolves the nearest table annotation while still mapping all inherited properties. */
  public static String getEntityName(Class<?> cls) {
    return ENTITY_NAMES.get(cls);
  }

  private static String discoverEntityName(Class<?> cls) {
    for (Class<?> current = cls;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      Named named = current.getDeclaredAnnotation(Named.class);
      if (named != null && named.value() != null && !named.value().isBlank()) {
        return named.value();
      }
    }
    return camelToSnake(cls.getSimpleName());
  }

  public static boolean isCollectionLike(Class<?> type) {
    return type != null && Collection.class.isAssignableFrom(type);
  }

  private static Optional<String> blankToEmpty(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  private static String camelToSnake(String value) {
    return value.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
  }
}
