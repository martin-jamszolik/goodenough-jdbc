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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.WithSql;

public class PersistableRowMapper<E extends Persistable> implements PersistableMapper<E> {
  private final BeanPropertyRowMapper<E> propertyMapper;
  private final Class<E> mappedType;
  private static final Logger log = LoggerFactory.getLogger(PersistableRowMapper.class);
  private static final Map<SqlRowSet, ResultSet> proxyCache =
      Collections.synchronizedMap(new WeakHashMap<>());
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
  }

  @SuppressWarnings("unchecked")
  public static <E extends Persistable> PersistableRowMapper<E> of(Class<E> cls) {
    return (PersistableRowMapper<E>)
        cachedMappers.computeIfAbsent(
            cls, (target) -> new PersistableRowMapper<>((Class<E>) target));
  }

  @Override
  public E mapRow(ResultSet rs, int rowNum) throws SQLException {
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
  @SuppressWarnings("exports")
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
    Optional<String> found =
        Stream.of(e.getClass(), e.getClass().getSuperclass())
            .filter(tp -> tp.isAnnotationPresent(PrimaryKey.class))
            .map(tp -> tp.getAnnotation(PrimaryKey.class).value())
            .findFirst();

    if (found.isPresent()) {
      var columnName = found.get();
      int columnIdx =
          requireColumnIndex(
              rs, columnName, String.format("Primary key mapping for %s", mappedType.getName()));
      long pkValue = rs.getLong(columnIdx);
      e.setRefs(Key.of(columnName, pkValue));
    }
  }

  private void assignNamedFields(Persistable entity, ResultSet rs) throws Exception {
    List<Method> methods =
        Arrays.stream(entity.getClass().getDeclaredMethods())
            .filter(
                m ->
                    m.getName().startsWith("get")
                        && WithSql.getAnnotation(m, entity.getClass(), Named.class).isPresent()
                        && WithSql.getAnnotation(m, entity.getClass(), Ref.class).isEmpty()
                        && !m.getReturnType().equals(RefValue.class))
            .collect(Collectors.toList());

    for (Method m : methods) {
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
      if (metaData.getColumnName(i).equalsIgnoreCase(columnName)) {
        index = i;
        break;
      }
    }
    return index;
  }

  protected static boolean isIntegerType(Class<?> parameterType) {
    return parameterType == int.class || parameterType == Integer.class;
  }

  private static Object interpolateValue(Object value, Class<?> asType) {
    if (value == null) {
      return null;
    }

    if (value instanceof Long && isIntegerType(asType)) {
      return Math.toIntExact((Long) value);
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
    List<Method> methods =
        Arrays.stream(entity.getClass().getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get"))
            .filter(m -> WithSql.getAnnotation(m, entity.getClass(), Ref.class).isPresent())
            .filter(
                m ->
                    m.getReturnType().isAnnotationPresent(PrimaryKey.class)
                        || m.getReturnType().equals(RefValue.class))
            .collect(Collectors.toList());

    // Cache reflection results
    Map<Method, Class<?>> foreignTypes = new HashMap<>();
    Map<Method, Optional<Named>> namedOptions = new HashMap<>();
    Map<Method, Ref> refs = new HashMap<>();

    for (Method m : methods) {
      foreignTypes.put(m, m.getReturnType());
      namedOptions.put(m, WithSql.getAnnotation(m, entity.getClass(), Named.class));
      refs.put(m, WithSql.getAnnotation(m, entity.getClass(), Ref.class).orElseThrow());
    }

    for (Method m : methods) {
      Class<?> foreignType = foreignTypes.get(m);
      var namedOption = namedOptions.get(m);
      var ref = refs.get(m);

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
        int labelIdx =
            requireColumnIndex(
                rs,
                ref.label(),
                String.format(
                    "@Ref label mapping for %s.%s",
                    entity.getClass().getSimpleName(), m.getName()));
        String labelValue = rs.getString(labelIdx);
        var fkValue = new RefValue(labelValue, Pair.of(ref.value(), rs.getLong(valueIdx)));
        invokeSetter(entity, m, fkValue);

        // In case of RefValue, continue over to the next method.
        continue;
      }

      var pkName = foreignType.getAnnotation(PrimaryKey.class).value();
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
    String setterName = accessor.getName().replace("get", "set");
    try {
      Method setter = entity.getClass().getDeclaredMethod(setterName, accessor.getReturnType());
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
    return proxyCache.computeIfAbsent(
        on,
        key ->
            (ResultSet)
                Proxy.newProxyInstance(
                    key.getClass().getClassLoader(),
                    new Class[] {ResultSet.class},
                    new SqlRowSetWrapper(key)));
  }

  private static class SqlRowSetWrapper implements InvocationHandler {
    private final SqlRowSet rows;

    public SqlRowSetWrapper(SqlRowSet rows) {
      this.rows = rows;
    }

    @Override
    @SuppressWarnings("UseSpecificCatch")
    public Object invoke(Object target, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("getMetaData")) {
        return proxyMetaData(rows.getMetaData());
      }

      if ("getObject".equals(method.getName())
          && args.length == 2
          && isIntegerType(method.getParameterTypes()[0])
          && args[1].equals(java.time.LocalDate.class)) {
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
