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

package org.viablespark.persistence.dsl;

import org.viablespark.persistence.Pair;
import org.viablespark.persistence.Persistable;
import org.viablespark.persistence.InstanceData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public abstract class WithSql {

    public static String getSQLSelectClause(Class<?> cls, String... customFields) {
        List<Method> methods = Arrays.stream(cls.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get"))
                .filter(m -> !m.isAnnotationPresent(Skip.class) )
                .filter(m -> !m.getReturnType().equals(InstanceData.class))
                .sorted(Comparator.comparing(Method::getName)).collect(Collectors.toList());

        StringBuilder sql = new StringBuilder();

        if (customFields != null) {
            for (String cName : customFields) {
                sql.append(cName).append(",");
            }
        }

        for (Method m : methods) {
            sql.append(deriveNameForSelectClause(m)).append(",");
        }
        sql.deleteCharAt(sql.length() - 1);
        return sql.toString();
    }

    public static SqlClause getSQLUpdateClause(Persistable entity) throws SQLException {
        try {
            List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("get"))
                    .filter(m -> !m.isAnnotationPresent(Skip.class))
                    .filter(m -> !m.getReturnType().equals(InstanceData.class))
                    .sorted(Comparator.comparing(Method::getName))
                    .collect(Collectors.toList());

            StringBuilder sql = new StringBuilder("SET ");
            List<Object> answers = new ArrayList<>(methods.size());
            for (Method m : methods) {
                sql.append(deriveName(m,entity)).append("=?,");
                answers.add(deriveValue(m,entity));
            }
            sql.deleteCharAt(sql.length() - 1); // remove last comma.
            Pair<String, Long> primaryKey = entity.getKey().getPrimaryKey();
            sql.append(" WHERE ").append(primaryKey.key).append("=?");
            answers.add(primaryKey.value);

            //sql = new StringBuilder(sql.toString().replaceAll(".$", ""));
            return new SqlClause(sql.toString(), answers.toArray());
        } catch (Exception ex) {
            throw new SQLException("Failed to Create a SQL Clause", ex);
        }
    }

    public static SqlClause getSQLInsertClause(Persistable entity) throws SQLException {
        try {
            List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("get"))
                    .filter(m -> !m.isAnnotationPresent(Skip.class))
                    .filter(m -> !m.getReturnType().equals(InstanceData.class))
                    .sorted(Comparator.comparing(Method::getName))
                    .collect(Collectors.toList());

            StringBuilder columns = new StringBuilder("(");
            StringBuilder values = new StringBuilder("VALUES (");
            List<Object> answers = new ArrayList<>(methods.size());
            for (Method m : methods) {
                columns.append(deriveName(m,entity)).append(",");
                values.append("?,");
                answers.add(deriveValue(m,entity));
            }
            columns = new StringBuilder(columns.toString().replaceAll(".$", "") + ")");
            values = new StringBuilder(values.toString().replaceAll(".$", "") + ")");

            String sql = columns + " " + values;

            return new SqlClause(sql, answers.toArray());
        } catch (Exception ex) {
            throw new SQLException("Failed to Create a SQL Clause", ex);
        }
    }

    private static String deriveName(Method m, Persistable entity) {
        try {
            if (m.isAnnotationPresent(Ref.class)) {
                Persistable refObj = (Persistable)m.invoke(entity);
                return refObj.getKey().getPrimaryKey().key;
            }

            if (m.isAnnotationPresent(Named.class)) {
                return m.getAnnotation(Named.class).value();
            }
            String name = m.getName().substring(3);
            name = camelToSnake(name);
            return name;
        } catch (Exception ex) {
            return null;
        }
    }
    
    private static Object deriveValue(Method m, Persistable entity ){
        try {
            if (m.isAnnotationPresent(Ref.class)) {
                Persistable refObj = (Persistable)m.invoke(entity);
                return refObj.getKey().getPrimaryKey().value;
            }
            return m.invoke(entity);
            
        } catch (Exception ex) {
            return null;
        }
    }

    private static String deriveNameForSelectClause(Method m) {

        if (m.isAnnotationPresent(Ref.class)) {
            Class<?> foreignType = m.getReturnType();
            if (foreignType.isAnnotationPresent(PrimaryKey.class)) {
                return foreignType.getAnnotation(PrimaryKey.class).value();
            }
        }

        if (m.isAnnotationPresent(Named.class)) {
            return m.getAnnotation(Named.class).value()
                    + " as " + camelToSnake(m.getName().substring(3));
        }
        String name = m.getName().substring(3);
        name = camelToSnake(name);
        return name;
    }

    private static String camelToSnake(String str) {
        // Regular Expression
        String regex = "([a-z])([A-Z]+)";
        // Replacement string
        String replacement = "$1_$2";
        str = str.replaceAll(regex, replacement).toLowerCase();

        return str;
    }

}
