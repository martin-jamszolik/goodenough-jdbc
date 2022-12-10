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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SqlQuery {

    private final List<Pair<String,Object>> where = new ArrayList<>();

    private final List<Pair<String,Object>> conditions = new ArrayList<>();

    private Pair<String,Object> orderBy;

    private Pair<String,Object> limit;
    
    private String primaryKeyName;
    
    private String rawSql;
    private Object[] rawValues;

    @SafeVarargs
    public SqlQuery(Pair<String,Object>... _where) {
        where.addAll(List.of(_where));
    }
    public SqlQuery(String _raw,Object... vals){
        rawSql = _raw;
        rawValues = vals;
    }
    
    @SafeVarargs
    public static SqlQuery where(Pair<String,Object>... _where) {
        return new SqlQuery(_where);
    }
    
    public static SqlQuery asRawSql(String sql, Object... vals){
        return new SqlQuery(sql, vals);
    }

    public SqlQuery condition(String key, Object value) {
        conditions.add(Pair.of(key, value));
        return this;
    }

    public SqlQuery orderBy(String key, Object value) {
        orderBy = Pair.of(key, value);
        return this;
    }

    public SqlQuery limit(String key, Object value) {
        limit = Pair.of(key,value);
        return this;
    }
    
    public SqlQuery primaryKey(String pkName){
        primaryKeyName = pkName;
        return this;
    }

    public String sql() {
        
        if ( rawSql != null ){
            return rawSql;
        }
        
        Optional<String> whereResult = where.stream()
                .map(p -> p.key)
                .reduce((acc, a) -> "" + acc + " AND " + a);

        String whereStr = whereResult.map(s -> "WHERE " + s).orElse(" ");

        Optional<String> condResult = conditions.stream()
                .map(p -> p.key)
                .reduce((acc, a) -> "" + acc + " " + a);

        String condStr = condResult.orElse("");

        String orderStr = orderBy != null ? " ORDER BY " +orderBy.key + " " + orderBy.value.toString() : "";

        String limitStr = limit != null ? " LIMIT "+limit.key : "";

        return whereStr + condStr + orderStr + limitStr;
    }

    public Object[] values() {
        if( rawValues != null ){
            return rawValues;
        }
        List<Object> list = new LinkedList<>();
        list.addAll(where.stream().map(p -> p.value).collect(Collectors.toList()));
        list.addAll(conditions.stream().map(p -> p.value).collect(Collectors.toList()));
        return list.toArray();
    }
    
    public String getPrimaryKeyName(){
        return primaryKeyName;
    }

}
