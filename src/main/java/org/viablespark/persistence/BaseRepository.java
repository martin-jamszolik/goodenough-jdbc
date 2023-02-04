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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.viablespark.persistence.dsl.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public abstract class BaseRepository<E extends Persistable> {

    protected final JdbcTemplate jdbc;

    public BaseRepository(JdbcTemplate db) {
        jdbc = db;
    }

    public Optional<Key> save(E entity) throws SQLException {

        if (entity.isNew()) {
            SqlClause withInsert = WithSql.getSQLInsertClause(entity);
            String sql = "INSERT INTO " + deriveEntityName(entity.getClass()) + " " + withInsert.getClause();
            KeyHolder key = execWithKey(sql, withInsert.getValues());
            if ( key.getKeys() != null ) {
                entity.setRefs(Key.of(entity.getClass()
                    .getAnnotation(PrimaryKey.class)
                    .value(), key.getKey().longValue()));
            }
            return Optional.ofNullable(entity.getRefs());
        }

        SqlClause withUpdate = WithSql.getSQLUpdateClause(entity);
        String sql = "UPDATE " + deriveEntityName(entity.getClass()) + " "
            + withUpdate.getClause();
        jdbc.update(sql, withUpdate.getValues());

        return Optional.ofNullable(entity.getRefs());
    }

    public void delete(E entity) {
        String sql = "DELETE FROM " + deriveEntityName(entity.getClass())
            + " WHERE " + entity.getRefs().primaryKey().getKey() + "=? ";

        jdbc.update(sql, entity.getRefs().primaryKey().getValue());
    }

    public Optional<E> get(Key key, Class<E> cls) throws NoSuchElementException {
        List<E> list = jdbc.query(
            "SELECT " + WithSql.getSQLSelectClause(cls, key.primaryKey().getKey())
                + " FROM " + deriveEntityName(cls)
                + " WHERE " + key.primaryKey().getKey() + "=?",
            PersistableRowMapper.of(cls),
            key.primaryKey().getValue());

        Optional<E> res = list.stream().findFirst();
        res.ifPresent(e -> e.setRefs(key));
        return res;
    }

    public List<E> queryEntity(SqlQuery query, Class<E> cls) {
        return jdbc.query(
            "SELECT " + WithSql.getSQLSelectClause(cls, query.getPrimaryKeyName())
                + " FROM " + deriveEntityName(cls) + " "
                + query.sql(), PersistableRowMapper.of(cls), query.values());
    }
    public List<E> query(SqlQuery query, PersistableMapper<E> mapper) {
        SqlRowSet rs = jdbc.queryForRowSet(query.sql(), query.values());
        List<E> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapper.mapRow(rs, rs.getRow()));
        }
        return list;
    }

    private KeyHolder execWithKey(final String sql, final Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
            connection -> {
                PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < args.length; i++) {
                    stmt.setObject(i + 1, args[i]);
                }
                return stmt;
            }, keyHolder);

        return keyHolder;
    }

    private String deriveEntityName(Class<?> cls) {
        if (cls.isAnnotationPresent(Named.class)) {
            return cls.getAnnotation(Named.class).value();
        }

        String name = cls.getSimpleName();
        //camelToSnake
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

}
