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

import org.viablespark.persistence.Contractor;
import org.viablespark.persistence.Key;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.viablespark.persistence.Proposal;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author mjamsz
 */
public class WithSqlTest {
    
    public WithSqlTest() {
    }

    @Test
    public void testGetSQLSelectClause() {
        String select = WithSql.getSQLSelectClause(Proposal.class);
        assertEquals("sc_key,dist as distance,prop_date,prop_id,proposal_name as prop_name,submit_deadline", select);
    }

    @Test
    public void testGetSQLUpdateClause() throws Exception {
        Proposal e = new Proposal();
        e.setKey( Key.of("pri_key",233L) );
        e.setDistance(344);
        e.setPropDate(new Date());
        e.setPropId("propId");
        e.setSubmitDeadline(new Date());
        e.setPropName("TestingName");
        e.setContractor(new Contractor("sc_key",1L));
        
        SqlClause update = WithSql.getSQLUpdateClause(e);
        assertNotNull(update);
        
        assertEquals(
                "SET sc_key=?,dist=?,prop_date=?,prop_id=?,proposal_name=?,submit_deadline=? WHERE pri_key=?",
                update.getClause());
        
    }

    @Test
    public void testGetSQLInsertClause() throws Exception {
        Proposal e = new Proposal();
        e.setKey(Key.of("pri_key",233L));
        e.setDistance(344);
        e.setPropDate(new Date());
        e.setPropId("propId");
        e.setSubmitDeadline(new Date());
        e.setPropName("TestingName");
        e.setContractor(new Contractor("sc_key",1L));
        
        SqlClause insert = WithSql.getSQLInsertClause(e);
        assertNotNull(insert);
        
        assertEquals(
                "(sc_key,dist,prop_date,prop_id,proposal_name,submit_deadline) VALUES (?,?,?,?,?,?)",
                insert.getClause());
        
    }
    
}
