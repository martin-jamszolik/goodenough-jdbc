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

import org.junit.jupiter.api.Test;
import org.viablespark.persistence.*;

import java.time.LocalDate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

/**
 *
 * @author mjamsz
 */
public class WithSqlTest {
    
    public WithSqlTest() {
        new WithSql();
    }

    @Test
    public void testGetSQLSelectClause() {
        String select = WithSql.getSelectClause(Proposal.class);
        assertEquals("sc_key,dist as \"distance\",prop_date,prop_id,proposal_name as \"prop_name\",submit_deadline", select);

        String notesSelect  = WithSql.getSelectClause(Note.class,"n_key");
        assertEquals("n_key,note_date as \"date_taken\",additional as \"extra\",note as \"note_content\",progress_id", notesSelect);

        String poSelect  = WithSql.getSelectClause(PurchaseOrder.class);
        assertEquals("some_fake_field as \"fake_field\",long_id as \"long_id\",n_key,po_number_id as \"po_number_id\",primitive_id as \"primitive_example_id\",requester as \"requester\",supplier_id", poSelect);

    }

    @Test
    public void testGetUpdateClause() throws Exception {
        Proposal e = new Proposal();
        e.setRefs( Key.of("pri_key",233L) );
        e.setDistance(344);
        e.setPropDate(new Date());
        e.setPropId("propId");
        e.setSubmitDeadline(LocalDate.now());
        e.setPropName("TestingName");
        e.setContractor(new Contractor("sc_key",1L));
        
        SqlClause update = WithSql.getUpdateClause(e);
        assertNotNull(update);
        
        assertEquals(
                "SET sc_key=?,dist=?,prop_date=?,prop_id=?,proposal_name=?,submit_deadline=? WHERE pri_key=?",
                update.getClause());

        Exception thrown = assertThrows(
            Exception.class,
            () -> WithSql.getUpdateClause(null),
            "Should throw Exception"
        );

        assertTrue("Should throw",
            thrown != null);
        
    }

    @Test
    public void testGetUpdateClauseForPurchaseOrder() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setRefs( Key.of("id",233L) );
        order.setNote(new Note());
        SqlClause update = WithSql.getUpdateClause(order);
        assertNotNull(update);

        assertEquals(
            "SET some_fake_field=?,long_id=?,po_number_id=?,primitive_id=?,requester=? WHERE id=?",
            update.getClause());

    }

    @Test
    public void testGetInsertClauseForPurchaseOrder() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setRefs( Key.of("id",233L) );
        order.setRequester("Requester Name");

        SqlClause insert = WithSql.getInsertClause(order);
        assertNotNull(insert);

        assertEquals(
            "(some_fake_field,long_id,po_number_id,primitive_id,requester) VALUES (?,?,?,?,?)",
            insert.getClause());
    }


    @Test
    public void testGetInsertClause() throws Exception {
        Proposal e = new Proposal();
        e.setRefs(Key.of("pri_key",233L));
        e.setDistance(344);
        e.setPropDate(new Date());
        e.setPropId("propId");
        e.setSubmitDeadline(LocalDate.now());
        e.setPropName("TestingName");
        e.setContractor(new Contractor("sc_key",1L));
        
        SqlClause insert = WithSql.getInsertClause(e);
        assertNotNull(insert);
        
        assertEquals(
                "(sc_key,dist,prop_date,prop_id,proposal_name,submit_deadline) VALUES (?,?,?,?,?,?)",
                insert.getClause());

        Exception thrown = assertThrows(
            Exception.class,
            () -> WithSql.getInsertClause(null),
            "Should throw Exception"
        );

        assertTrue("Should throw",
            thrown != null);
        
    }
    
}
