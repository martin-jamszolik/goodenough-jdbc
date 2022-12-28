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
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistableRowMapperTest {


    private EmbeddedDatabase db;
    @Test
    public void testRowSetMapping(){
        var f = new PersistableRowMapper<>(Contractor.class);
        var jdbc = new JdbcTemplate(db);
        var rowSet = jdbc.queryForRowSet("select * from contractor order by sc_key asc");
        List<Contractor> resultSet = jdbc.query("select * from contractor order by sc_key asc", f);
        while( rowSet.next() ){
            Contractor contractor = f.mapRow(rowSet, rowSet.getRow());
            assertEquals(contractor.getKey(),resultSet.get(rowSet.getRow()-1).getKey() );
        }
    }

    @Test
    public void testMapRowMappingUsingResultSet() throws Exception {

        var jdbc = new JdbcTemplate(db);
        var f = new PersistableMapper<Contractor>() {
            @Override
            public Contractor mapRow(SqlRowSet rs, int rowNum) {
                Contractor c = new Contractor();
                c.setKey(Key.of("sc_key",rs.getLong("sc_key")));
                c.setName(rs.getString("sc_name"));
                c.setContact(rs.getString("contact"));
                return c;
            }
        };

        var sql = "select sc_key, sc_name as name, contact, phone1, fax, email from contractor c order by sc_key asc";

        List<Contractor> list = jdbc.query(sql, f);

        try (var statement = db.getConnection()
            .prepareStatement(sql)) {
            var rs = statement.executeQuery();
            rs.next();
            Contractor entity = f.mapRow(rs,rs.getRow());
            assertEquals(entity.getKey(), list.get(rs.getRow()-1).getKey() );
        }
    }

    @Test
    public void testException(){
        var f = new PersistableRowMapper<>(Contractor.class);

        Exception thrown = assertThrows(
            Exception.class,
            () -> f.mapRow((SqlRowSet) null,1),
            "Expected to throw, but it didn't"
        );

        assertTrue("Should throw",
            thrown != null);
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