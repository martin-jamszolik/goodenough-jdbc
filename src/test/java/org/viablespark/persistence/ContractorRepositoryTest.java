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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractorRepositoryTest {



    private EmbeddedDatabase db;
    private ContractorRepository repository;

    @BeforeEach
    public void setUp() {
        // creates an HSQL in-memory database populated from default scripts
        // classpath:schema.sql and classpath:data.sql
        db = new EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("ContractorTest")
            .build();
        repository = new ContractorRepository(new JdbcTemplate(db));
    }
    @AfterEach
    public void tearDown() {
        db.shutdown();
    }

    @Test
    public void testGetContractor(){

        var res = repository.get(Key.of("sc_key",1L),Contractor.class);
        assertTrue(res.isPresent());
        res.ifPresent(c -> assertEquals(c.getId(),1L));
    }


    public static class ContractorRepository extends BaseRepository<Contractor> {

        public ContractorRepository(JdbcTemplate db) {
            super(db);
        }

    }
}
