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

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistableRowMapperTest {


    private EmbeddedDatabase db;
    @Test
    public void testRowSetMapping(){
        var f = PersistableRowMapper.of(Contractor.class);
        var jdbc = new JdbcTemplate(db);
        var rowSet = jdbc.queryForRowSet("select * from contractor order by sc_key asc");
        List<Contractor> resultSet = jdbc.query("select * from contractor order by sc_key asc", f);
        while( rowSet.next() ){
            Contractor contractor = f.mapRow(rowSet, rowSet.getRow());
            assertEquals(contractor.getRefs(),resultSet.get(rowSet.getRow()-1).getRefs() );
        }
    }

    @Test
    public void testMapRowMappingUsingResultSet() throws Exception {

        var jdbc = new JdbcTemplate(db);
        PersistableMapper<Contractor> f = (SqlRowSet rs, int rowNum) -> {
            Contractor c = new Contractor();
            c.setRefs(Key.of("sc_key",rs.getLong("sc_key")));
            c.setName(rs.getString("sc_name"));
            c.setContact(rs.getString("contact"));
            return c;
        };

        var sql = "select sc_key, sc_name as name, contact, phone1, fax, email from contractor c order by sc_key asc";

        List<Contractor> list = jdbc.query(sql, f);

        try (var statement = db.getConnection()
            .prepareStatement(sql)) {
            var rs = statement.executeQuery();
            rs.next();
            Contractor entity = f.mapRow(rs,rs.getRow());
            assertEquals(entity.getRefs(), list.get(rs.getRow()-1).getRefs() );
        }
    }

    @Test
    public void testException(){
        var f = PersistableRowMapper.of(Contractor.class);

        Exception thrown = assertThrows(
            Exception.class,
            () -> f.mapRow((SqlRowSet) null,1),
            "Expected to throw, but it didn't"
        );

        assertTrue("Should throw",
            thrown != null);
    }

    @Test
    public void testPurchaseOrderMappings() throws Exception {
        var mapper = PersistableRowMapper.of(PurchaseOrder.class);
        var jdbc = new JdbcTemplate(db);
        var rowSet = jdbc.queryForRowSet("select purchase_order.*, supplier.*, 'fake' as fake_col " +
            "from purchase_order left join supplier on ( supplier.id = purchase_order.supplier_id ) ");
        while( rowSet.next() ){
           PurchaseOrder po = mapper.mapRow(rowSet,rowSet.getRow());
           assertNotNull(po);
           assertEquals(po.getSupplierRef().getRef().getValue(),1L);
           assertEquals(po.getSupplierRef().getValue(), "Test Supplier");
        }

    }

    @Test
    public void testNoteMappings() throws Exception {
        var mapper = PersistableRowMapper.of(PurchaseOrder.class);
        var jdbc = new JdbcTemplate(db);
        var rowSet = jdbc.queryForRowSet("select purchase_order.*, supplier.*, 'fake' as fake_col " +
            "from purchase_order left join supplier on ( supplier.id = purchase_order.supplier_id ) ");
        while( rowSet.next() ){
            PurchaseOrder po = mapper.mapRow(rowSet,rowSet.getRow());
            assertNotNull(po);
        }

    }



    @BeforeEach
    public void setUp() {
        db = new EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("PersistableRowMapperTest")
            .build();
    }

    @AfterEach
    public void tearDown() {
        db.shutdown();
    }

}