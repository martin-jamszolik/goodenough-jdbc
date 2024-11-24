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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BaseRepository<E extends Persistable> {

    protected final JdbcTemplate jdbc;

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
        } catch (Exception e) {
            throw new RuntimeException("Failed to save entity: " + entity, e);
        }
    }

    private Optional<Key> insertEntity(E entity) throws Exception {
        SqlClause insertClause = WithSql.getInsertClause(entity);
        String sql = String.format("INSERT INTO %s %s", deriveEntityName(entity.getClass()), insertClause.getClause());
        KeyHolder keyHolder = execWithKey(sql, insertClause.getValues());
        
        if (keyHolder.getKeys() != null) {
            entity.setRefs(Key.of(entity.getClass().getAnnotation(PrimaryKey.class).value(), keyHolder.getKey().longValue()));
        }

        return Optional.of(entity.getRefs());
    }

    private Optional<Key> updateEntity(E entity) throws Exception {
        SqlClause updateClause = WithSql.getUpdateClause(entity);
        String sql = String.format("UPDATE %s %s", deriveEntityName(entity.getClass()), updateClause.getClause());
        jdbc.update(sql, updateClause.getValues());

        return Optional.ofNullable(entity.getRefs());
    }

    public void delete(E entity) {
        String sql = String.format("DELETE FROM %s WHERE %s = ?", 
                                    deriveEntityName(entity.getClass()), 
                                    entity.getRefs().primaryKey().getKey());
        jdbc.update(sql, entity.getRefs().primaryKey().getValue());
    }

    public Optional<E> get(Key key, Class<E> cls) {
        String sql = String.format("SELECT %s FROM %s WHERE %s = ?", 
                                    WithSql.getSelectClause(cls, key.primaryKey().getKey()),
                                    deriveEntityName(cls),
                                    key.primaryKey().getKey());
        List<E> list = jdbc.query(sql, PersistableRowMapper.of(cls), key.primaryKey().getValue());

        return list.stream().findFirst().map(entity -> {
            entity.setRefs(key);
            return entity;
        });
    }

    public List<E> queryEntity(SqlQuery query, Class<E> cls) {
        var primaryKeyName =  cls.isAnnotationPresent(PrimaryKey.class)
            ? cls.getAnnotation(PrimaryKey.class).value()
            : query.getPrimaryKeyName();
        String sql = String.format("SELECT %s FROM %s %s", 
                                    WithSql.getSelectClause(cls, primaryKeyName),
                                    deriveEntityName(cls),
                                    query.sql());
        return jdbc.query(sql, PersistableRowMapper.of(cls), query.values());
    }

    public List<E> query(SqlQuery query, PersistableMapper<E> mapper) {
        SqlRowSet rs = jdbc.queryForRowSet(query.sql(), query.values());
        List<E> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapper.mapRow(rs, rs.getRow()));
        }
        return list;
    }

    protected KeyHolder execWithKey(final String sql, final Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
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

        return camelToSnake(cls.getSimpleName());
    }

    private String camelToSnake(String name) {
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
}
