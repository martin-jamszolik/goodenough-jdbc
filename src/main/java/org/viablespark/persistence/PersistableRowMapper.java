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
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.WithSql;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;

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
        try {
            assignPrimaryKey(bean, rs);
            assignForeignRefs(bean, rs);
            assignNamedFields(bean, rs);
            return bean;
        } catch (Exception ex) {
            throw new SQLException(ex);
        }
    }

    @Override
    public E mapRow(SqlRowSet rs, int rowNum) {
        try {
            return mapRow(proxy(rs), rowNum);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void assignPrimaryKey(Persistable e, ResultSet rs) throws Exception {
        if (persistableType.isAnnotationPresent(PrimaryKey.class)) {
            String primaryKeyName = e.getClass().getAnnotation(PrimaryKey.class).value();
            e.setKey(Key.of(primaryKeyName, rs.getLong(primaryKeyName)));
        }
    }

    private void assignNamedFields(Persistable entity, ResultSet rs) throws Exception {
        List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get"))
            .filter(m -> WithSql.getAnnotation(m, entity.getClass(), Named.class).isPresent())
            .filter(m -> WithSql.getAnnotation(m, entity.getClass(), Ref.class).isEmpty())
            .collect(Collectors.toList());

        for (Method m : methods) {
            var optionMethod = WithSql.getAnnotation(m, entity.getClass(), Named.class);

            var customField = optionMethod.orElseThrow().value();

            if (rs.getObject(customField) != null) {
                var setterValue = rs.getObject(customField);
                Method setterMethod = entity.getClass().getDeclaredMethod(
                    m.getName().replace("get", "set"), m.getReturnType());
                setterMethod.invoke(entity, interpolateValue(setterValue,m.getReturnType()));
            }

        }
    }

    private static Object interpolateValue(Object value, Class<?> asType) {
        if( value instanceof Long && asType == Integer.class ){
            value = Math.toIntExact((Long) value);
        }
        return value;
    }

    private void assignForeignRefs(Persistable entity, ResultSet rs) throws Exception {
        List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get"))
            .filter(m -> WithSql.getAnnotation(m, entity.getClass(), Ref.class).isPresent())
            .filter(m -> m.getReturnType().isAnnotationPresent(PrimaryKey.class))
            .collect(Collectors.toList());
        for (Method m : methods) {
            Class<?> foreignType = m.getReturnType();
            var pkName = foreignType.getAnnotation(PrimaryKey.class).value();
            var namedOption = WithSql.getAnnotation(m, entity.getClass(), Named.class);
            String columnName = pkName;
            if (namedOption.isPresent()) {
                columnName = namedOption.get().value();
            }
            var pkValue = rs.getLong(columnName);
            var fkInstance = foreignType.getDeclaredConstructor().newInstance();
            ((Persistable) fkInstance).setKey(Key.of(pkName, pkValue));
            Method setterMethod = entity.getClass().getDeclaredMethod(
                m.getName().replace("get", "set"), foreignType);
            setterMethod.invoke(entity, fkInstance);
        }
    }
       

    private static ResultSet proxy(SqlRowSet on) {
        return (ResultSet) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
            new Class[]{ResultSet.class}, new SqlRowSetWrapper(on));
    }

    private static class SqlRowSetWrapper implements InvocationHandler {
        private final SqlRowSet rows;

        public SqlRowSetWrapper(SqlRowSet rows) {
            this.rows = rows;
        }

        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {

            if (method.getName().equals("getMetaData")) {
                return proxyMetaData(rows.getMetaData());
            }

            var targetMethod = rows.getClass().getMethod(method.getName(), method.getParameterTypes());
            return targetMethod.invoke(rows, objects);
        }

        static ResultSetMetaData proxyMetaData(SqlRowSetMetaData meta) {
            return (ResultSetMetaData) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                new Class[]{ResultSetMetaData.class}, new SqlRowSetMetaDataWrapper(meta));
        }

    }

    static class SqlRowSetMetaDataWrapper implements InvocationHandler {
        private final SqlRowSetMetaData meta;

        public SqlRowSetMetaDataWrapper(SqlRowSetMetaData meta) {
            this.meta = meta;
        }

        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {

            if (method.getName().contains("getColumnLabel")) {
                return meta.getColumnLabel((int) objects[0]);
            }

            return meta.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(meta, objects);
        }

    }


}
