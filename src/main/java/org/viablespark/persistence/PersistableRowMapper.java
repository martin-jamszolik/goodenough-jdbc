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

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.WithSql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PersistableRowMapper<E extends Persistable> implements PersistableMapper<E> {
    private final BeanPropertyRowMapper<E> propertyMapper;
    private final Map<String, Integer> columnIndexCache = new HashMap<>();
    private static final Map<SqlRowSet, ResultSet> proxyCache = new WeakHashMap<>();
    private static final Map<Class<? extends Persistable>, PersistableRowMapper<? extends Persistable>>
        cachedMappers = new ConcurrentHashMap<>(100, 0.75f, 16);

    /**
     * To take advantage of a cached instance of RowMapper use the
     * static method of() instead to create an instance.
     */
    private PersistableRowMapper(Class<E> cls) {
        this.propertyMapper = new BeanPropertyRowMapper<>(cls);
    }


    @SuppressWarnings("unchecked")
    public static <E extends Persistable> PersistableRowMapper<E> of(Class<E> cls) {
        return (PersistableRowMapper<E>) cachedMappers.computeIfAbsent(cls,
            (target) -> new PersistableRowMapper<>((Class<E>) target));
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
            throw new SQLException(ex.getMessage(), ex);
        }
    }

    @Override
    public E mapRow(SqlRowSet rs, int rowNum) {
        try {
            return mapRow(proxy(rs), rowNum);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }


    private void assignPrimaryKey(Persistable e, ResultSet rs) throws Exception {
        Optional<String> found = Stream.of(e.getClass(), e.getClass().getSuperclass())
            .filter(tp -> tp.isAnnotationPresent(PrimaryKey.class))
            .map(tp -> tp.getAnnotation(PrimaryKey.class).value()).findFirst();

        if (found.isPresent()) {
            e.setRefs(Key.of(found.get(), rs.getLong(found.get())));
        }
    }

    private void assignNamedFields(Persistable entity, ResultSet rs) throws Exception {
        List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get")
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
                Method setterMethod = entity.getClass().getDeclaredMethod(
                    m.getName().replace("get", "set"), m.getReturnType());
                setterMethod.invoke(entity, interpolateValue(setterValue, m.getReturnType()));
            }

        }
    }

    private int columnIndex(ResultSet rs, String columnName) throws SQLException {
        if (columnIndexCache.containsKey(columnName)) {
            return columnIndexCache.get(columnName);
        }

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String currentColumnName = metaData.getColumnName(i);
            columnIndexCache.put(currentColumnName.toLowerCase(), i);
            if (currentColumnName.equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    protected static boolean isIntegerType(Class<?> parameterType) {
        return parameterType == int.class || parameterType == Integer.class;
    }

    private static Object interpolateValue(Object value, Class<?> asType) {
        if (value instanceof Long && isIntegerType(asType)) {
            value = Math.toIntExact((Long) value);
        }

        if (asType == java.time.LocalDate.class) {
            value = ((java.sql.Date) value).toLocalDate();
        }
        return value;
    }

    private void assignForeignRefs(Persistable entity, ResultSet rs) throws Exception {
        List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get"))
            .filter(m -> WithSql.getAnnotation(m, entity.getClass(), Ref.class).isPresent())
            .filter(m -> m.getReturnType().isAnnotationPresent(PrimaryKey.class) || m.getReturnType().equals(RefValue.class))
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

                Method setterMethod = entity.getClass().getDeclaredMethod(
                    m.getName().replace("get", "set"), foreignType);
                var fkValue = new RefValue(
                    rs.getString(ref.label()),
                    Pair.of(ref.value(), rs.getLong(ref.value()))
                );
                setterMethod.invoke(entity, fkValue);

                //In case of RefValue, continue over to the next method.
                continue;
            }

            var pkName = foreignType.getAnnotation(PrimaryKey.class).value();
            String columnName = pkName;
            if (namedOption.isPresent()) {
                columnName = namedOption.get().value();
            }
            var pkValue = rs.getLong(columnName);
            var fkInstance = foreignType.getDeclaredConstructor().newInstance();
            ((Persistable) fkInstance).setRefs(Key.of(pkName, pkValue));
            Method setterMethod = entity.getClass().getDeclaredMethod(
                m.getName().replace("get", "set"), foreignType);
            setterMethod.invoke(entity, fkInstance);
        }
    }


    private static ResultSet proxy(SqlRowSet on) {
        return proxyCache.computeIfAbsent(on, key ->
            (ResultSet) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                new Class[]{ResultSet.class}, new SqlRowSetWrapper(key))
        );
    }

    private static class SqlRowSetWrapper implements InvocationHandler {
        private final SqlRowSet rows;

        public SqlRowSetWrapper(SqlRowSet rows) {
            this.rows = rows;
        }

        @Override
        public Object invoke(Object target, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getMetaData")) {
                return proxyMetaData(rows.getMetaData());
            }

            if ( "getObject".equals(method.getName())
                && args.length == 2
                && isIntegerType(method.getParameterTypes()[0])
                && args[1].equals(java.time.LocalDate.class) ){
                return rows.getDate((Integer)args[0]);
            }
            var targetMethod = rows.getClass().getMethod(method.getName(), method.getParameterTypes());
            try {
                return targetMethod.invoke(rows, args);
            } catch (Exception ex) {
                throw new SQLException(ex.getMessage(), ex);
            }
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
        public Object invoke(Object o, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getColumnLabel")) {
                return meta.getColumnLabel((int) args[0]);
            }
            return meta.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(meta, args);
        }

    }


}
