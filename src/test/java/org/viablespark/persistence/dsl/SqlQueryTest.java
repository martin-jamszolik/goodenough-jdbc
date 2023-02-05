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
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.viablespark.persistence.dsl.SqlQuery;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 *
 */
public class SqlQueryTest {

    public SqlQueryTest() {
    }

    @Test
    public void testWhere() {
        SqlQuery q = SqlQuery.where(Pair.of("pri_key=?", 233L), Pair.of("sec_date >=?", new Date()));

        String result = q.sql();
        assertEquals("WHERE pri_key=? AND sec_date >=?", result);
    }

    @Test
    public void testCondition() {
        SqlQuery q = SqlQuery.where(Pair.of("pri_key=?", 233L), Pair.of("sec_date >=?", new Date()));
        q.condition(" OR (other_key =?", 23L)
                .condition("AND something !=?)", "elseHere");

        String result = q.sql();
        assertEquals("WHERE pri_key=? AND sec_date >=? OR (other_key =? AND something !=?)", result);
    }

    @Test
    public void testOrderBy() {
        SqlQuery q = SqlQuery.where(Pair.of("pri_key=?", 233L), Pair.of("sec_date >=?", new Date()));
        String result = q.orderBy("testColumn", "desc").sql();
        assertEquals("WHERE pri_key=? AND sec_date >=? ORDER BY testColumn desc", result);
    }

    @Test
    public void testLimit() {
        SqlQuery q = SqlQuery.where(Pair.of("pri_key=?", 233L), Pair.of("sec_date >=?", new Date()));
        String result = q.limit("5", "").sql();
        assertEquals("WHERE pri_key=? AND sec_date >=? LIMIT 5", result);
    }

    @Test
    public void testDynamicQuery() {
        String name = "123 main";

        var query = SqlQuery.withSelect("SELECT COUNT(*)");
        query.clause("FROM 1_proposalproject p "
            +"INNER JOIN company c using(c_key) "
            +"INNER JOIN 1_estlocation el using(el_key) "
            +"INNER JOIN estimator e using(e_key) WHERE",null);

        query.condition("pp_name like ? ","%"+name+"%");

        assertTrue(query.sql().contains("SELECT COUNT(*) FROM 1_proposalproject p") );
        assertTrue(query.values().length == 1);
        assertTrue(query.sql().contains("WHERE pp_name like ?") );

        query.select( "SELECT p.pp_key, pp_name, tax, pp_status, pp_date, pp_owner");
        query.paginate(20,0);

        assertTrue(query.sql().contains("SELECT p.pp_key, pp_name, tax, pp_status, pp_date, pp_owner FROM 1_proposalproject p") );
        assertTrue(query.values().length == 1);
    }

}
