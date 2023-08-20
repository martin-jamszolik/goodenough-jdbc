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

import java.util.*;
import java.util.stream.Collectors;

public class SqlQuery {

    private final List<SqlClause> clauses = new ArrayList<>();

    private final List<Pair<String,Object>> conditions = new ArrayList<>();

    private Pair<String,Object> orderBy;

    private Pair<String,Object> limit;
    
    private String primaryKeyName;

    private String select;

    private SqlClause pagination;
    
    private String rawSql;
    private Object[] rawValues;

    public SqlQuery() {
    }
    public SqlQuery(String _raw,Object... vals){
        rawSql = _raw;
        rawValues = vals;
    }

    public SqlQuery clause(String clause, Object[] values ){
        clauses.add(new SqlClause(clause,values));
        return this;
    }

    public SqlQuery paginate(int limit, int offset){
        pagination = new SqlClause(" LIMIT "+limit+" OFFSET "+offset,null );
        return this;
    }

    public SqlQuery select(String select){
        this.select = select;
        return this;
    }
    

    public static SqlQuery asRawSql(String sql, Object... values){
        return new SqlQuery(sql, values);
    }

    public static SqlQuery withClause(String clause, Object... values) {
        var q = new SqlQuery();
        q.clause(clause,values);
        return q;
    }

    public static SqlQuery withSelect(String select) {
        var q = new SqlQuery();
        q.select(select);
        return q;
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

        String selectStr = select != null ? select+" " : "";


        Optional<String> condResult = conditions.stream()
                .map(Pair::getKey)
                .reduce((acc, a) -> acc + " " + a);

        Optional<String> clausesResult = clauses.stream()
            .map(SqlClause::getClause).reduce((acc, a) -> acc + " " + a);

        String clauseStr = clausesResult.orElse("");

        String condStr = condResult.orElse("");

        String orderStr = orderBy != null ? " ORDER BY " +orderBy.getKey() + " " + orderBy.getValue().toString() : "";

        String limitStr = limit != null ? " LIMIT "+limit.getKey() : "";

        String paginateStr = pagination != null ? pagination.getClause() : "";

        return selectStr + clauseStr + condStr + orderStr + limitStr + paginateStr;
    }

    public Object[] values() {
        if( rawValues != null ){
            return rawValues;
        }
        List<Object> list = new LinkedList<>();
        list.addAll(clauses.stream().flatMap( clause -> Arrays.stream(clause.getValues())).collect(Collectors.toList()));
        list.addAll(conditions.stream().map(Pair::getValue).collect(Collectors.toList()));
        return list.toArray();
    }
    
    public String getPrimaryKeyName(){
        return primaryKeyName;
    }

}
