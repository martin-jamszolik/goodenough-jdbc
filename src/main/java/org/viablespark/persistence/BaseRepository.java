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

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.NamedSqlQuery;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.SqlClause;
import org.viablespark.persistence.dsl.SqlQuery;
import org.viablespark.persistence.dsl.WithSql;
import org.viablespark.persistence.validation.SqlQueryValidator;

public abstract class BaseRepository<E extends Persistable> {

  protected final JdbcTemplate jdbc;
  protected final NamedParameterJdbcTemplate namedJdbc;
  private static final Logger log = LoggerFactory.getLogger(BaseRepository.class);

  @SuppressWarnings("exports")
  public BaseRepository(JdbcTemplate db) {
    this.jdbc = db;
    this.namedJdbc = new NamedParameterJdbcTemplate(db);
  }

  public Optional<Key> save(E entity) {
    try {
      if (entity.isNew()) {
        return insertEntity(entity);
      } else {
        return updateEntity(entity);
      }
    } catch (Exception e) {
      String description = describeEntity(entity);
      log.error("Failed to save entity {}", description, e);
      throw new RuntimeException("Failed to save entity: " + description, e);
    }
  }

  private Optional<Key> insertEntity(E entity) throws Exception {
    SqlClause insertClause = WithSql.getInsertClause(entity);
    String sql =
        String.format(
            "INSERT INTO %s %s", deriveEntityName(entity.getClass()), insertClause.clause());
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing insert for {} with SQL [{}] and values {}",
          entity.getClass().getSimpleName(),
          sql,
          java.util.Arrays.toString(insertClause.values()));
    }
    KeyHolder keyHolder = execWithKey(sql, insertClause.values());

    if (keyHolder.getKeys() != null) {
      entity.setRefs(
          Key.of(
              entity.getClass().getAnnotation(PrimaryKey.class).value(),
              keyHolder.getKey().longValue()));
    }

    return Optional.of(entity.getRefs());
  }

  private Optional<Key> updateEntity(E entity) throws Exception {
    SqlClause updateClause = WithSql.getUpdateClause(entity);
    String sql =
        String.format("UPDATE %s %s", deriveEntityName(entity.getClass()), updateClause.clause());
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing update for {} with SQL [{}] and values {}",
          entity.getClass().getSimpleName(),
          sql,
          java.util.Arrays.toString(updateClause.values()));
    }
    jdbc.update(sql, updateClause.values());

    return Optional.ofNullable(entity.getRefs());
  }

  public void delete(E entity) {
    String sql =
        String.format(
            "DELETE FROM %s WHERE %s = ?",
            deriveEntityName(entity.getClass()), entity.getRefs().primaryKey().getKey());
    if (log.isDebugEnabled()) {
      log.debug("Deleting entity {} using SQL [{}]", describeEntity(entity), sql);
    }
    jdbc.update(sql, entity.getRefs().primaryKey().getValue());
  }

  public Optional<E> get(Key key, Class<E> cls) {
    String sql =
        String.format(
            "SELECT %s FROM %s WHERE %s = ?",
            selectClause(cls, key.primaryKey().getKey()),
            deriveEntityName(cls),
            key.primaryKey().getKey());
    if (log.isDebugEnabled()) {
      log.debug("Fetching {} using SQL [{}] and key {}", cls.getSimpleName(), sql, key);
    }
    List<E> list;
    try {
      list = jdbc.query(sql, PersistableRowMapper.of(cls), key.primaryKey().getValue());
    } catch (RuntimeException ex) {
      log.error(
          "Failed to execute get for {} with SQL [{}] and key {}", cls.getName(), sql, key, ex);
      throw ex;
    }

    return list.stream()
        .findFirst()
        .map(
            entity -> {
              entity.setRefs(key);
              return entity;
            });
  }

  public List<E> queryEntity(SqlQuery query, Class<E> cls) {
    var primaryKeyName =
        cls.isAnnotationPresent(PrimaryKey.class)
            ? cls.getAnnotation(PrimaryKey.class).value()
            : query.getPrimaryKeyName();
    SqlQueryValidator.assertPlaceholderCount(query);
    String sql =
        String.format(
            "SELECT %s FROM %s %s",
            selectClause(cls, primaryKeyName), deriveEntityName(cls), query.sql());
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing queryEntity for {} with SQL [{}] and values {}",
          cls.getSimpleName(),
          sql,
          java.util.Arrays.toString(query.values()));
    }
    try {
      return jdbc.query(sql, PersistableRowMapper.of(cls), query.values());
    } catch (RuntimeException ex) {
      log.error(
          "Failed to execute queryEntity for {} with SQL [{}] and values {}",
          cls.getName(),
          sql,
          java.util.Arrays.toString(query.values()),
          ex);
      throw ex;
    }
  }

  public List<E> queryEntity(NamedSqlQuery query, Class<E> cls) {
    var primaryKeyName =
        cls.isAnnotationPresent(PrimaryKey.class)
            ? cls.getAnnotation(PrimaryKey.class).value()
            : null;
    String sql =
        String.format(
            "SELECT %s FROM %s %s",
            selectClause(cls, primaryKeyName), deriveEntityName(cls), query.sql());
    if (log.isDebugEnabled()) {
      log.debug("Executing named queryEntity for {} with SQL [{}]", cls.getSimpleName(), sql);
    }
    try {
      return namedJdbc.query(sql, query.params(), PersistableRowMapper.of(cls));
    } catch (RuntimeException ex) {
      log.error("Failed to execute named queryEntity for {} with SQL [{}]", cls.getName(), sql, ex);
      throw ex;
    }
  }

  public List<E> query(SqlQuery query, PersistableMapper<E> mapper) {
    SqlQueryValidator.assertPlaceholderCount(query);
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing custom query with SQL [{}] and values {}",
          query.sql(),
          java.util.Arrays.toString(query.values()));
    }
    SqlRowSet rs;
    try {
      rs = jdbc.queryForRowSet(query.sql(), query.values());
    } catch (RuntimeException ex) {
      log.error(
          "Failed to execute query for SQL [{}] and values {}",
          query.sql(),
          java.util.Arrays.toString(query.values()),
          ex);
      throw ex;
    }
    List<E> list = new ArrayList<>();
    while (rs.next()) {
      list.add(mapper.mapRow(rs, rs.getRow()));
    }
    return list;
  }

  public List<E> query(NamedSqlQuery query, PersistableMapper<E> mapper) {
    if (log.isDebugEnabled()) {
      log.debug("Executing custom named query with SQL [{}]", query.sql());
    }
    try {
      return namedJdbc.query(
          query.sql(),
          query.params(),
          rs -> {
            List<E> list = new ArrayList<>();
            int rowNum = 0;
            while (rs.next()) {
              list.add(mapper.mapRow(rs, rowNum++));
            }
            return list;
          });
    } catch (RuntimeException ex) {
      log.error("Failed to execute named query for SQL [{}]", query.sql(), ex);
      throw ex;
    }
  }

  public Optional<E> queryOne(SqlQuery query, Class<E> cls) {
    return singleResult(queryEntity(query, cls), query.sql());
  }

  public Optional<E> queryOne(NamedSqlQuery query, Class<E> cls) {
    return singleResult(queryEntity(query, cls), query.sql());
  }

  public boolean exists(Key key, Class<E> cls) {
    return get(key, cls).isPresent();
  }

  public long count(SqlQuery query, Class<E> cls) {
    SqlQueryValidator.assertPlaceholderCount(query);
    String sql = String.format("SELECT COUNT(*) FROM %s %s", deriveEntityName(cls), query.sql());
    Long count = jdbc.queryForObject(sql, Long.class, query.values());
    return count != null ? count : 0L;
  }

  public long count(NamedSqlQuery query, Class<E> cls) {
    String sql = String.format("SELECT COUNT(*) FROM %s %s", deriveEntityName(cls), query.sql());
    Long count = namedJdbc.queryForObject(sql, query.params(), Long.class);
    return count != null ? count : 0L;
  }

  public <T> List<T> queryRows(SqlQuery query, RowMapper<T> mapper) {
    SqlQueryValidator.assertPlaceholderCount(query);
    return jdbc.query(query.sql(), mapper, query.values());
  }

  public <T> List<T> queryRows(NamedSqlQuery query, RowMapper<T> mapper) {
    return namedJdbc.query(query.sql(), query.params(), mapper);
  }

  public int[] insertAll(List<E> entities) {
    return batchStatements(
        entities,
        entity -> {
          SqlClause insertClause = WithSql.getInsertClause(entity);
          return new BatchStatement(
              String.format(
                  "INSERT INTO %s %s", deriveEntityName(entity.getClass()), insertClause.clause()),
              insertClause.values());
        });
  }

  public int[] updateAll(List<E> entities) {
    return batchStatements(
        entities,
        entity -> {
          SqlClause updateClause = WithSql.getUpdateClause(entity);
          return new BatchStatement(
              String.format(
                  "UPDATE %s %s", deriveEntityName(entity.getClass()), updateClause.clause()),
              updateClause.values());
        });
  }

  public int[] deleteAll(List<E> entities) {
    return batchStatements(
        entities,
        entity ->
            new BatchStatement(
                String.format(
                    "DELETE FROM %s WHERE %s = ?",
                    deriveEntityName(entity.getClass()), entity.getRefs().primaryKey().getKey()),
                new Object[] {entity.getRefs().primaryKey().getValue()}));
  }

  public List<Optional<Key>> saveAll(List<E> entities) {
    List<Optional<Key>> keys = new ArrayList<>(entities.size());
    for (E entity : entities) {
      keys.add(save(entity));
    }
    return keys;
  }

  protected KeyHolder execWithKey(final String sql, final Object... args) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement stmt =
              connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) {
            stmt.setObject(i + 1, args[i]);
          }
          return stmt;
        },
        keyHolder);

    return keyHolder;
  }

  private String deriveEntityName(Class<?> cls) {
    if (cls.isAnnotationPresent(Named.class)) {
      return cls.getAnnotation(Named.class).value();
    }

    return camelToSnake(cls.getSimpleName());
  }

  private String selectClause(Class<?> cls, String primaryKeyName) {
    if (primaryKeyName == null || primaryKeyName.isBlank()) {
      return WithSql.getSelectClause(cls);
    }
    return WithSql.getSelectClause(cls, primaryKeyName);
  }

  private String camelToSnake(String name) {
    return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
  }

  private String describeEntity(Persistable entity) {
    if (entity == null) {
      return "<null entity>";
    }
    StringBuilder description = new StringBuilder(entity.getClass().getName());
    try {
      if (entity.getRefs() != null) {
        description.append(" [").append(entity.getRefs()).append(']');
      }
    } catch (Exception ex) {
      log.debug("Unable to describe entity refs for {}", entity.getClass().getName(), ex);
    }
    return description.toString();
  }

  private Optional<E> singleResult(List<E> results, String sql) {
    if (results.isEmpty()) {
      return Optional.empty();
    }
    if (results.size() > 1) {
      throw new IllegalStateException(
          String.format("Expected a single result for SQL [%s] but found %d", sql, results.size()));
    }
    return Optional.of(results.get(0));
  }

  private int[] batchStatements(List<E> entities, StatementFactory<E> statementFactory) {
    int[] results = new int[entities.size()];
    Map<String, List<IndexedArgs>> grouped = new LinkedHashMap<>();
    for (int i = 0; i < entities.size(); i++) {
      try {
        BatchStatement statement = statementFactory.create(entities.get(i));
        grouped
            .computeIfAbsent(statement.sql(), ignored -> new ArrayList<>())
            .add(new IndexedArgs(i, statement.args()));
      } catch (Exception ex) {
        throw new RuntimeException("Failed to build batch statement", ex);
      }
    }

    for (Map.Entry<String, List<IndexedArgs>> entry : grouped.entrySet()) {
      int[] batchResult =
          jdbc.batchUpdate(
              entry.getKey(), entry.getValue().stream().map(IndexedArgs::args).toList());
      for (int i = 0; i < batchResult.length; i++) {
        results[entry.getValue().get(i).index()] = batchResult[i];
      }
    }
    return results;
  }

  @FunctionalInterface
  private interface StatementFactory<T> {
    BatchStatement create(T entity) throws Exception;
  }

  private record BatchStatement(String sql, Object[] args) {}

  private record IndexedArgs(int index, Object[] args) {}
}
