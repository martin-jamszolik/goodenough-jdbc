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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PersistableRowMapper<E extends Persistable> implements PersistableMapper<E> {
    private final BeanPropertyRowMapper<E> propertyMapper;
    private final Class<E> persistableType;

    public PersistableRowMapper(Class<E> cls) {
        this.propertyMapper = new BeanPropertyRowMapper<>(cls);
        this.persistableType = cls;
    }

    @Override
    public E mapRow(ResultSet rs, int rowNum) throws SQLException {
        var bean = propertyMapper.mapRow(rs, rowNum);
        assert(bean != null);
        assignPrimaryKey(bean, rs);
        assignForeignRefs(bean, rs);
        return bean;
    }

    @Override
    public E mapRow(SqlRowSet rs, int rowNum) {
        try {
            return mapRow(PersistableMapper.proxy(rs),rowNum);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void assignPrimaryKey(Persistable e, ResultSet rs) throws SQLException {
        if (persistableType.isAnnotationPresent(PrimaryKey.class)) {
            String primaryKeyName = e.getClass().getAnnotation(PrimaryKey.class).value();
            e.setKey(Key.of(primaryKeyName, rs.getLong(primaryKeyName)));
        }
    }

    private void assignForeignRefs(Persistable entity, ResultSet rs) throws SQLException {
        List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get"))
            .filter(m -> m.isAnnotationPresent(Ref.class))
            .filter(m -> m.getReturnType().isAnnotationPresent(PrimaryKey.class))
            .collect(Collectors.toList());
        try {
            for (Method m : methods) {
                Class<?> foreignType = m.getReturnType();
                var pkName = foreignType.getAnnotation(PrimaryKey.class).value();
                var pkValue = rs.getLong(pkName);
                var fkInstance = foreignType.getDeclaredConstructor().newInstance();
                ((Persistable) fkInstance).setKey(Key.of(pkName, pkValue));
                Method setterMethod = entity.getClass().getDeclaredMethod(
                    m.getName().replace("get", "set"), foreignType);
                setterMethod.invoke(entity, fkInstance);
            }
        } catch (Exception ex) {
            throw new SQLException(ex);
        }
    }
}
