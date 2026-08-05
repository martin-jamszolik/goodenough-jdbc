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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.ResultSetWrappingSqlRowSet;
import org.viablespark.persistence.dsl.SqlClause;
import org.viablespark.persistence.dsl.SqlQuery;
import org.viablespark.persistence.dsl.WithSql;
import org.viablespark.persistence.validation.SqlQueryValidator;

public abstract class BaseRepository<E extends Persistable> {

  protected final JdbcTemplate jdbc;
  private static final Logger log = LoggerFactory.getLogger(BaseRepository.class);

  @SuppressWarnings("exports")
  public BaseRepository(JdbcTemplate db) {
    this.jdbc = db;
  }

  public Optional<Key> save(E entity) {
    try {
      if (entity.isNew()) {
        return insertEntity(entity);
      } else {
        return updateEntity(entity);
      }
    } catch (IllegalArgumentException e) {
      log.error("Invalid mapping for entity {}", describeEntity(entity), e);
      throw e;
    } catch (DataAccessException e) {
      log.error("Failed to save entity {}", describeEntity(entity), e);
      throw e;
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

    Key insertedKey = resolveInsertedKey(entity, keyHolder);
    if (insertedKey.count() > 0) {
      entity.setRefs(insertedKey);
    }

    return optionalIdentity(entity);
  }

  private Optional<Key> updateEntity(E entity) throws Exception {
    WithSql.validateKey(entity.getClass(), entity.getRefs());
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
    int updated = jdbc.update(sql, updateClause.values());
    requireSingleAffectedRow("update", entity, updated);

    return Optional.ofNullable(entity.getRefs());
  }

  public void delete(E entity) {
    WithSql.validateKey(entity.getClass(), entity.getRefs());
    SqlClause predicate = keyPredicate(entity.getRefs());
    String sql =
        String.format(
            "DELETE FROM %s WHERE %s", deriveEntityName(entity.getClass()), predicate.clause());
    if (log.isDebugEnabled()) {
      log.debug("Deleting entity {} using SQL [{}]", describeEntity(entity), sql);
    }
    int deleted = jdbc.update(sql, predicate.values());
    requireSingleAffectedRow("delete", entity, deleted);
  }

  public Optional<E> get(Key key, Class<E> cls) {
    WithSql.validateKey(cls, key);
    SqlClause predicate = keyPredicate(key);
    String sql =
        String.format(
            "SELECT %s FROM %s WHERE %s",
            selectClause(cls, keyNames(key)), deriveEntityName(cls), predicate.clause());
    if (log.isDebugEnabled()) {
      log.debug("Fetching {} using SQL [{}] and key {}", cls.getSimpleName(), sql, key);
    }
    List<E> list;
    try {
      list = queryRows(sql, predicate.values(), PersistableRowMapper.of(cls));
    } catch (RuntimeException ex) {
      log.error(
          "Failed to execute get for {} with SQL [{}] and key {}", cls.getName(), sql, key, ex);
      throw ex;
    }

    return list.stream()
        .findFirst()
        .map(
            entity -> {
              if (WithSql.getPrimaryKeys(cls).isEmpty()) {
                entity.setRefs(key);
              }
              return entity;
            });
  }

  public List<E> queryEntity(SqlQuery query, Class<E> cls) {
    requireFragment(query, "queryEntity");
    List<String> primaryKeyNames = primaryKeyNames(cls, query);
    SqlQueryValidator.assertPlaceholderCount(query);
    String sql =
        String.format(
            "SELECT %s FROM %s %s",
            selectClause(cls, primaryKeyNames), deriveEntityName(cls), query.sql());
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing queryEntity for {} with SQL [{}] and values {}",
          cls.getSimpleName(),
          sql,
          java.util.Arrays.toString(query.values()));
    }
    try {
      return queryRows(sql, query.values(), PersistableRowMapper.of(cls));
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

  public List<E> query(SqlQuery query, PersistableMapper<E> mapper) {
    requireStatement(query, "query");
    SqlQueryValidator.assertPlaceholderCount(query);
    if (log.isDebugEnabled()) {
      log.debug(
          "Executing custom query with SQL [{}] and values {}",
          query.sql(),
          java.util.Arrays.toString(query.values()));
    }
    return queryRows(query.sql(), query.values(), mapper);
  }

  public Optional<E> queryOne(SqlQuery query, Class<E> cls) {
    requireFragment(query, "queryOne");
    List<String> primaryKeyNames = primaryKeyNames(cls, query);
    SqlQueryValidator.assertPlaceholderCount(query);
    String sql =
        String.format(
            "SELECT %s FROM %s %s",
            selectClause(cls, primaryKeyNames), deriveEntityName(cls), query.sql());
    return singleResult(queryAtMostTwo(sql, PersistableRowMapper.of(cls), query.values()), sql);
  }

  public boolean exists(Key key, Class<E> cls) {
    return get(key, cls).isPresent();
  }

  public long count(SqlQuery query, Class<E> cls) {
    requireFragment(query, "count");
    SqlQueryValidator.assertPlaceholderCount(query);
    String sql = String.format("SELECT COUNT(*) FROM %s %s", deriveEntityName(cls), query.sql());
    Long count = jdbc.queryForObject(sql, Long.class, query.values());
    return count != null ? count : 0L;
  }

  public <T> List<T> queryRows(SqlQuery query, PersistableMapper<T> mapper) {
    requireStatement(query, "queryRows");
    SqlQueryValidator.assertPlaceholderCount(query);
    return queryRows(query.sql(), query.values(), mapper);
  }

  public <T> List<T> queryProjection(SqlQuery query, Class<T> projectionType) {
    requireStatement(query, "queryProjection");
    SqlQueryValidator.assertPlaceholderCount(query);
    return queryRows(query.sql(), query.values(), DataClassRowMapper.newInstance(projectionType));
  }

  public <T> Optional<T> queryRow(SqlQuery query, PersistableMapper<T> mapper) {
    requireStatement(query, "queryRow");
    SqlQueryValidator.assertPlaceholderCount(query);
    return singleResult(queryAtMostTwo(query.sql(), mapper, query.values()), query.sql());
  }

  public <T> Optional<T> queryProjectionOne(SqlQuery query, Class<T> projectionType) {
    requireStatement(query, "queryProjectionOne");
    SqlQueryValidator.assertPlaceholderCount(query);
    return singleResult(
        queryAtMostTwo(query.sql(), DataClassRowMapper.newInstance(projectionType), query.values()),
        query.sql());
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
        entity -> {
          WithSql.validateKey(entity.getClass(), entity.getRefs());
          SqlClause predicate = keyPredicate(entity.getRefs());
          return new BatchStatement(
              String.format(
                  "DELETE FROM %s WHERE %s",
                  deriveEntityName(entity.getClass()), predicate.clause()),
              predicate.values());
        });
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
    return WithSql.getEntityName(cls);
  }

  private String selectClause(Class<?> cls, Collection<String> primaryKeyNames) {
    if (primaryKeyNames == null || primaryKeyNames.isEmpty()) {
      return WithSql.getSelectClause(cls);
    }
    return WithSql.getSelectClause(cls, primaryKeyNames.toArray(String[]::new));
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

  private <T> Optional<T> singleResult(List<T> results, String sql) {
    if (results.isEmpty()) {
      return Optional.empty();
    }
    if (results.size() > 1) {
      throw new IllegalStateException(
          String.format("Expected a single result for SQL [%s] but found %d", sql, results.size()));
    }
    return Optional.of(results.get(0));
  }

  private <T> List<T> queryAtMostTwo(String sql, RowMapper<T> mapper, Object... args) {
    try {
      return jdbc.query(sql, (ResultSetExtractor<List<T>>) rs -> mapAtMostTwo(rs, mapper), args);
    } catch (RuntimeException ex) {
      log.error("Failed to execute single-result query for SQL [{}]", sql, ex);
      throw ex;
    }
  }

  private <T> List<T> queryAtMostTwo(String sql, PersistableMapper<T> mapper, Object... args) {
    return queryAtMostTwo(sql, toRowMapper(mapper), args);
  }

  private <T> List<T> mapAtMostTwo(ResultSet rs, RowMapper<T> mapper) throws SQLException {
    List<T> results = new ArrayList<>(2);
    int rowNum = 0;
    while (rs.next() && results.size() < 2) {
      results.add(mapper.mapRow(rs, rowNum++));
    }
    return results;
  }

  private <T> List<T> queryRows(String sql, Object[] values, RowMapper<T> mapper) {
    return jdbc.query(sql, mapper, values);
  }

  private <T> List<T> queryRows(String sql, Object[] values, PersistableMapper<T> mapper) {
    return queryRows(sql, values, toRowMapper(mapper));
  }

  private <T> RowMapper<T> toRowMapper(PersistableMapper<T> mapper) {
    if (mapper instanceof PersistableRowMapper<?> persistableMapper) {
      return (rs, rowNum) -> mapPersistableRow(persistableMapper, rs, rowNum);
    }
    return (rs, rowNum) -> mapper.mapRow(new ResultSetWrappingSqlRowSet(rs), rowNum);
  }

  @SuppressWarnings("unchecked")
  private <T> T mapPersistableRow(PersistableRowMapper<?> mapper, ResultSet resultSet, int rowNum)
      throws SQLException {
    return (T) mapper.mapRow(resultSet, rowNum);
  }

  private void requireFragment(SqlQuery query, String method) {
    if (!query.isFragment()) {
      throw new IllegalArgumentException(
          method + " requires a SQL fragment; use SqlQuery.fragment(...)");
    }
  }

  private void requireStatement(SqlQuery query, String method) {
    if (!query.isStatement()) {
      throw new IllegalArgumentException(
          method + " requires a complete SQL statement; use SqlQuery.statement(...)");
    }
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

  private Key resolveInsertedKey(E entity, KeyHolder keyHolder) throws Exception {
    List<String> primaryKeys = WithSql.getPrimaryKeys(entity.getClass());
    Key resolved = WithSql.getEntityKey(entity);
    Map<String, Object> generated = keyHolder.getKeys();
    if (generated == null || generated.isEmpty() || primaryKeys.isEmpty()) {
      return resolved.count() == primaryKeys.size() ? resolved : Key.None;
    }

    Key result = new Key();
    for (String primaryKey : primaryKeys) {
      Object value =
          generated.entrySet().stream()
              .filter(entry -> entry.getKey().equalsIgnoreCase(primaryKey))
              .map(Map.Entry::getValue)
              .findFirst()
              .orElseGet(() -> resolved.contains(primaryKey).map(Pair::getValue).orElse(null));
      if (value == null && primaryKeys.size() == 1 && generated.size() == 1) {
        value = generated.values().iterator().next();
      }
      if (!(value instanceof Number number)) {
        return resolved;
      }
      result.add(primaryKey, number);
    }
    return result;
  }

  private List<String> primaryKeyNames(Class<?> cls, SqlQuery query) {
    List<String> primaryKeys = WithSql.getPrimaryKeys(cls);
    if (!primaryKeys.isEmpty()) {
      return primaryKeys;
    }
    String queryPrimaryKey = query.getPrimaryKeyName();
    return queryPrimaryKey == null || queryPrimaryKey.isBlank()
        ? List.of()
        : List.of(queryPrimaryKey);
  }

  private List<String> keyNames(Key key) {
    if (key == null) {
      return List.of();
    }
    return key.getKeys().stream().map(Pair::getKey).toList();
  }

  private SqlClause keyPredicate(Key key) {
    if (key == null || key.count() == 0) {
      throw new IllegalArgumentException("A repository key must contain at least one column");
    }
    String clause =
        key.getKeys().stream()
            .map(part -> part.getKey() + " = ?")
            .collect(java.util.stream.Collectors.joining(" AND "));
    Object[] values = key.getKeys().stream().map(Pair::getValue).toArray();
    return new SqlClause(clause, values);
  }

  private void requireSingleAffectedRow(String operation, E entity, int affectedRows) {
    if (affectedRows != 1) {
      throw new IllegalStateException(
          String.format(
              "Expected %s of %s to affect 1 row but affected %d",
              operation, describeEntity(entity), affectedRows));
    }
  }

  private Optional<Key> optionalIdentity(E entity) {
    Key refs = entity.getRefs();
    return refs == null || refs.count() == 0 ? Optional.empty() : Optional.of(refs);
  }

  @FunctionalInterface
  private interface StatementFactory<T> {
    BatchStatement create(T entity) throws Exception;
  }

  private record BatchStatement(String sql, Object[] args) {}

  private record IndexedArgs(int index, Object[] args) {}
}
