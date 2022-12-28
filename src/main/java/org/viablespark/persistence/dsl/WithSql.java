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

import org.viablespark.persistence.Key;
import org.viablespark.persistence.Pair;
import org.viablespark.persistence.Persistable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class WithSql {

    public static String getSQLSelectClause(Class<?> cls, String... customFields) {
        List<Method> methods = Arrays.stream(cls.getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get"))
            .filter(m -> getAnnotation(m, cls, Skip.class).isEmpty())
            .filter(m -> !m.getReturnType().equals(Key.class))
            .sorted(Comparator.comparing(Method::getName)).collect(Collectors.toList());

        StringBuilder sql = new StringBuilder();

        for (String cName : customFields) {
            sql.append(cName).append(",");
        }

        for (Method m : methods) {
            sql.append(deriveNameForSelectClause(m, cls)).append(",");
        }
        sql.deleteCharAt(sql.length() - 1);
        return sql.toString();
    }

    public static SqlClause getSQLUpdateClause(Persistable entity) throws SQLException {
        try {
            List<Method> methods = Arrays.stream(entity.getClass().getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get"))
                .filter(m -> getAnnotation(m, entity.getClass(), Skip.class).isEmpty())
                .sorted(Comparator.comparing(Method::getName))
                .collect(Collectors.toList());

            StringBuilder sql = new StringBuilder("SET ");
            List<Object> answers = new ArrayList<>(methods.size());
            for (Method m : methods) {
                sql.append(deriveName(m, entity)).append("=?,");
                answers.add(deriveValue(m, entity));
            }
            sql.deleteCharAt(sql.length() - 1); // remove last comma.
            Pair<String, Long> primaryKey = entity.getKey().primaryKey();
            sql.append(" WHERE ").append(primaryKey.getKey()).append("=?");
            answers.add(primaryKey.getValue());

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
                .filter(m -> getAnnotation(m, entity.getClass(), Skip.class).isEmpty())
                .sorted(Comparator.comparing(Method::getName))
                .collect(Collectors.toList());

            StringBuilder columns = new StringBuilder("(");
            StringBuilder values = new StringBuilder("VALUES (");
            List<Object> answers = new ArrayList<>(methods.size());
            for (Method m : methods) {
                columns.append(deriveName(m, entity)).append(",");
                values.append("?,");
                answers.add(deriveValue(m, entity));
            }
            columns = new StringBuilder(columns.toString().replaceAll(".$", "") + ")");
            values = new StringBuilder(values.toString().replaceAll(".$", "") + ")");

            String sql = columns + " " + values;

            return new SqlClause(sql, answers.toArray());
        } catch (Exception ex) {
            throw new SQLException("Failed to Create a SQL Clause", ex);
        }
    }

    private static String deriveName(Method m, Persistable entity) throws Exception {
        Optional<Named> namedOption = getAnnotation(m, entity.getClass(), Named.class);
        if (namedOption.isPresent()) {
            return namedOption.get().value();
        }

        Optional<Ref> refOption = getAnnotation(m, entity.getClass(), Ref.class);
        if (refOption.isPresent()) {
            Persistable refObj = (Persistable) m.invoke(entity);
            return refObj.getKey().primaryKey().getKey();
        }

        String name = m.getName().substring(3);
        name = camelToSnake(name);
        return name;
    }

    private static Object deriveValue(Method m, Persistable entity) throws Exception {
        Optional<Ref> refOption = getAnnotation(m, entity.getClass(), Ref.class);
        if (refOption.isPresent()) {
            Persistable refObj = (Persistable) m.invoke(entity);
            return refObj.getKey().primaryKey().getValue();
        }
        return m.invoke(entity);
    }

    private static String deriveNameForSelectClause(Method m, Class<?> cls) {
        Optional<Named> namedOption = getAnnotation(m, cls, Named.class);
        Optional<Ref> refOption = getAnnotation(m, cls, Ref.class);

        if (namedOption.isPresent()) {
            if (refOption.isPresent()) {
                return namedOption.get().value();
            } else {
                return namedOption.get().value()
                    + " as " + camelToSnake(m.getName().substring(3));
            }
        }


        if (refOption.isPresent()) {
            Class<?> foreignType = m.getReturnType();
            if (foreignType.isAnnotationPresent(PrimaryKey.class)) {
                return foreignType.getAnnotation(PrimaryKey.class).value();
            }
        }

        String name = m.getName().substring(3);
        name = camelToSnake(name);
        return name;
    }

    private static Optional<Field> getFieldForMethod(Method m, Class<?> cls) {
        try {
            Field field = cls.getDeclaredField(toLowerCamelCase(m.getName()));
            return Optional.of(field);
        } catch (NoSuchFieldException e) {
            return Optional.empty();
        }
    }

    public static <T extends Annotation> Optional<T> getAnnotation(
        Method m, Class<?> cls, Class<T> annotation) {

        if (m.isAnnotationPresent(annotation)) {
            return Optional.of(m.getAnnotation(annotation));
        }

        Optional<Field> optField = getFieldForMethod(m, cls);
        if (optField.isPresent() && optField.get().isAnnotationPresent(annotation)) {
            return Optional.of(optField.get().getAnnotation(annotation));
        }
        return Optional.empty();
    }

    private static String toLowerCamelCase(String methodName) {
        StringBuilder b = new StringBuilder(methodName.substring(3));
        var result = b.replace(0, 1, (b.charAt(0) + "").toLowerCase());
        return result.toString();
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
