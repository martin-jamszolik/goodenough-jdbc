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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.rowset.ResultSetWrappingSqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.lang.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

@FunctionalInterface
public interface PersistableMapper<E extends Persistable> extends RowMapper<E> {


    @Override
    default E mapRow(ResultSet rs,
                     int rowNum) throws SQLException {
        return mapRow(new ResultSetWrappingSqlRowSet(rs), rowNum);
    }

    @Nullable
    E mapRow(SqlRowSet rs, int rowNum);


    static ResultSet proxy(SqlRowSet on) {
        return (ResultSet) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
            new Class[]{ResultSet.class}, new SqlRowSetWrapper(on));
    }

    static class SqlRowSetWrapper implements InvocationHandler {
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
