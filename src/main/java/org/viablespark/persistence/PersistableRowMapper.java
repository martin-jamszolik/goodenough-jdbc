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

package org.viablespark.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.WithSql;

public class PersistableRowMapper<E extends Persistable> implements PersistableMapper<E> {
  private final BeanPropertyRowMapper<E> propertyMapper;
  private final Class<E> mappedType;
  private final List<Method> namedMethods;
  private final List<Method> referenceMethods;
  private static final Logger log = LoggerFactory.getLogger(PersistableRowMapper.class);
  private static final Map<
          Class<? extends Persistable>, PersistableRowMapper<? extends Persistable>>
      cachedMappers = new ConcurrentHashMap<>(100, 0.75f, 16);

  /**
   * To take advantage of a cached instance of RowMapper use the static method of() instead to
   * create an instance.
   */
  private PersistableRowMapper(Class<E> cls) {
    this.mappedType = cls;
    this.propertyMapper = new BeanPropertyRowMapper<>(cls);
    this.namedMethods =
        WithSql.persistentGetters(cls).stream()
            .filter(method -> WithSql.getAnnotation(method, cls, Named.class).isPresent())
            .filter(method -> WithSql.getAnnotation(method, cls, Ref.class).isEmpty())
            .filter(method -> !WithSql.isCollectionLike(method.getReturnType()))
            .filter(method -> !method.getReturnType().equals(RefValue.class))
            .toList();
    this.referenceMethods =
        WithSql.persistentGetters(cls).stream()
            .filter(method -> WithSql.getAnnotation(method, cls, Ref.class).isPresent())
            .filter(method -> !WithSql.isCollectionLike(method.getReturnType()))
            .filter(
                method ->
                    WithSql.getPrimaryKeys(method.getReturnType()).size() == 1
                        || method.getReturnType().equals(RefValue.class))
            .toList();
  }

  @SuppressWarnings("unchecked")
  public static <E extends Persistable> PersistableRowMapper<E> of(Class<E> cls) {
    return (PersistableRowMapper<E>)
        cachedMappers.computeIfAbsent(
            cls, (target) -> new PersistableRowMapper<>((Class<E>) target));
  }

  E mapRow(ResultSet rs, int rowNum) throws SQLException {
    try {
      var bean = propertyMapper.mapRow(rs, rowNum);
      assignPrimaryKey(Objects.requireNonNull(bean), rs);
      assignForeignRefs(bean, rs);
      assignNamedFields(bean, rs);
      return bean;
    } catch (Exception ex) {
      String message =
          String.format(
              "Failed to map result set row %d to %s: %s",
              rowNum, mappedType.getName(), ex.getMessage());
      log.error(message, ex);
      throw new SQLException(message, ex);
    }
  }

  @Override
  public E mapRow(SqlRowSet rs, int rowNum) {
    try {
      return mapRow(proxy(rs), rowNum);
    } catch (SQLException ex) {
      String message =
          String.format(
              "Failed to map row %d for %s: %s", rowNum, mappedType.getName(), ex.getMessage());
      log.error(message, ex);
      throw new RuntimeException(message, ex);
    }
  }

  private void assignPrimaryKey(Persistable e, ResultSet rs) throws Exception {
    List<String> primaryKeys = WithSql.getPrimaryKeys(e.getClass());
    if (!primaryKeys.isEmpty()) {
      Key key = new Key();
      for (String columnName : primaryKeys) {
        int columnIdx =
            requireColumnIndex(
                rs, columnName, String.format("Primary key mapping for %s", mappedType.getName()));
        long pkValue = rs.getLong(columnIdx);
        key.add(columnName, pkValue);
      }
      e.setRefs(key);
    }
  }

  private void assignNamedFields(Persistable entity, ResultSet rs) throws Exception {
    for (Method m : namedMethods) {
      var optionMethod = WithSql.getAnnotation(m, entity.getClass(), Named.class);

      var customField = optionMethod.orElseThrow().value();
      int index = columnIndex(rs, customField);
      if (index > 0) {
        var setterValue = rs.getObject(index);
        invokeSetter(entity, m, interpolateValue(setterValue, m.getReturnType()));
      } else {
        log.debug(
            "Result set for {} is missing column '{}' required by @Named on {}.{}",
            mappedType.getSimpleName(),
            customField,
            entity.getClass().getSimpleName(),
            m.getName());
      }
    }
  }

  private int columnIndex(ResultSet rs, String columnName) throws SQLException {
    ResultSetMetaData metaData = rs.getMetaData();
    int columnCount = metaData.getColumnCount();
    int index = -1;
    for (int i = 1; i <= columnCount; i++) {
      if (metaData.getColumnLabel(i).equalsIgnoreCase(columnName)) {
        index = i;
        break;
      }
    }
    return index;
  }

  protected static boolean isIntegerType(Class<?> parameterType) {
    return parameterType == int.class || parameterType == Integer.class;
  }

  protected static boolean isBooleanType(Class<?> parameterType) {
    return parameterType == boolean.class || parameterType == Boolean.class;
  }

  protected static boolean isLongType(Class<?> parameterType) {
    return parameterType == long.class || parameterType == Long.class;
  }

  private static Object interpolateValue(Object value, Class<?> asType) {
    if (value == null) {
      return null;
    }

    if (value instanceof Number number && isIntegerType(asType)) {
      return Math.toIntExact(number.longValue());
    }

    if (value instanceof Number number && isLongType(asType)) {
      return number.longValue();
    }

    if (isBooleanType(asType)) {
      if (value instanceof Number number) {
        return number.longValue() != 0L;
      }
      if (value instanceof String stringValue) {
        return Boolean.parseBoolean(stringValue)
            || "1".equals(stringValue)
            || "Y".equalsIgnoreCase(stringValue)
            || "YES".equalsIgnoreCase(stringValue);
      }
    }

    if (asType == java.time.LocalDate.class) {
      if (value instanceof java.sql.Date date) {
        return date.toLocalDate();
      }
      if (value instanceof java.sql.Timestamp timestamp) {
        return timestamp.toLocalDateTime().toLocalDate();
      }
      if (value instanceof java.time.LocalDate) {
        return value;
      }
    }

    return value;
  }

  private void assignForeignRefs(Persistable entity, ResultSet rs) throws Exception {
    for (Method m : referenceMethods) {
      Class<?> foreignType = m.getReturnType();
      var namedOption = WithSql.getAnnotation(m, entity.getClass(), Named.class);
      var ref = WithSql.getAnnotation(m, entity.getClass(), Ref.class).orElseThrow();

      if (foreignType.equals(RefValue.class)) {
        if (ref.value().isBlank() || ref.label().isBlank()) {
          throw new IllegalArgumentException(
              String.format(
                  "@Ref on %s.%s requires both value and label when used with RefValue",
                  entity.getClass().getSimpleName(), m.getName()));
        }

        int valueIdx =
            requireColumnIndex(
                rs,
                ref.value(),
                String.format(
                    "@Ref mapping for %s.%s", entity.getClass().getSimpleName(), m.getName()));
        int labelIdx = columnIndex(rs, ref.label());
        long value = rs.getLong(valueIdx);
        if (rs.wasNull()) {
          invokeSetter(entity, m, null);
          continue;
        }
        String labelValue = labelIdx > 0 ? rs.getString(labelIdx) : null;
        var fkValue = new RefValue(labelValue, Pair.of(ref.value(), value));
        invokeSetter(entity, m, fkValue);

        // In case of RefValue, continue over to the next method.
        continue;
      }

      var pkName = WithSql.getPrimaryKey(foreignType).orElseThrow();
      String columnName = pkName;
      if (namedOption.isPresent()) {
        columnName = namedOption.get().value();
      }
      int columnIdx =
          requireColumnIndex(
              rs,
              columnName,
              String.format(
                  "@Ref mapping for %s.%s", entity.getClass().getSimpleName(), m.getName()));
      var pkValue = rs.getLong(columnIdx);
      if (rs.wasNull()) {
        invokeSetter(entity, m, null);
        continue;
      }
      var fkInstance = foreignType.getDeclaredConstructor().newInstance();
      ((Persistable) fkInstance).setRefs(Key.of(pkName, pkValue));
      invokeSetter(entity, m, fkInstance);
    }
  }

  private int requireColumnIndex(ResultSet rs, String columnName, String context)
      throws SQLException {
    int columnIdx = columnIndex(rs, columnName);
    if (columnIdx < 0) {
      throw new SQLException(
          String.format("%s: column '%s' not present in result set", context, columnName));
    }
    return columnIdx;
  }

  private void invokeSetter(Persistable entity, Method accessor, Object value) throws SQLException {
    String setterName = WithSql.setterName(accessor);
    try {
      Method setter = WithSql.findSetter(entity.getClass(), accessor);
      setter.invoke(entity, value);
    } catch (NoSuchMethodException ex) {
      throw new SQLException(
          String.format(
              "Setter '%s' for %s.%s not found",
              setterName, entity.getClass().getSimpleName(), accessor.getName()),
          ex);
    } catch (IllegalAccessException | InvocationTargetException ex) {
      throw new SQLException(
          String.format(
              "Failed to invoke setter '%s' for %s.%s: %s",
              setterName, entity.getClass().getSimpleName(), accessor.getName(), ex.getMessage()),
          ex);
    }
  }

  private static ResultSet proxy(SqlRowSet on) {
    return (ResultSet)
        Proxy.newProxyInstance(
            on.getClass().getClassLoader(),
            new Class[] {ResultSet.class},
            new SqlRowSetWrapper(on));
  }

  private record SqlRowSetWrapper(SqlRowSet rows) implements InvocationHandler {

    @Override
    @SuppressWarnings("UseSpecificCatch")
    public Object invoke(Object target, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("getMetaData")) {
        return proxyMetaData(rows.getMetaData());
      }

      if ("getObject".equals(method.getName())
          && args.length == 2
          && isIntegerType(method.getParameterTypes()[0])
          && args[1].equals(LocalDate.class)) {
        return rows.getDate((Integer) args[0]);
      }
      var targetMethod = rows.getClass().getMethod(method.getName(), method.getParameterTypes());
      try {
        return targetMethod.invoke(rows, args);
      } catch (Exception ex) {
        throw new SQLException(ex.getMessage(), ex);
      }
    }

    static ResultSetMetaData proxyMetaData(SqlRowSetMetaData meta) {
      return (ResultSetMetaData)
          Proxy.newProxyInstance(
              meta.getClass().getClassLoader(),
              new Class[] {ResultSetMetaData.class},
              new SqlRowSetMetaDataWrapper(meta));
    }
  }

  static class SqlRowSetMetaDataWrapper implements InvocationHandler {
    private final SqlRowSetMetaData meta;

    public SqlRowSetMetaDataWrapper(SqlRowSetMetaData meta) {
      this.meta = meta;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("getColumnLabel")) {
        return meta.getColumnLabel((int) args[0]);
      }
      return meta.getClass()
          .getMethod(method.getName(), method.getParameterTypes())
          .invoke(meta, args);
    }
  }
}
