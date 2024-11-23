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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

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


    @Test
    public void testSaveContractor(){
        // Step 1: Create a new Contractor instance
        Contractor contractor = new Contractor();
        contractor.setName("John Doe Construction");
        contractor.setContact("John Doe");
        contractor.setPhone1("123-456-7890");
        contractor.setFax("098-765-4321");
        contractor.setEmail("contact@johndoeconstruction.com");

        // Step 2: Save the contractor using the repository
        var savedKey = repository.save(contractor);

        // Step 3: Verify the contractor was saved and key was generated
        assertTrue(savedKey.isPresent(), "Contractor save should return a generated key");
        Key generatedKey = savedKey.get();

        // Step 4: Retrieve the contractor from the repository to verify its data
        var retrievedContractorOpt = repository.get(generatedKey, Contractor.class);
        assertTrue(retrievedContractorOpt.isPresent(), "Saved contractor should be retrievable");

        Contractor retrievedContractor = retrievedContractorOpt.get();

        // Step 5: Verify all fields match the original data
        assertEquals("John Doe Construction", retrievedContractor.getName(), "Name should match");
        assertEquals("John Doe", retrievedContractor.getContact(), "Contact should match");
        assertEquals("123-456-7890", retrievedContractor.getPhone1(), "Phone1 should match");
        assertEquals("098-765-4321", retrievedContractor.getFax(), "Fax should match");
        assertEquals("contact@johndoeconstruction.com", retrievedContractor.getEmail(), "Email should match");

    }

    @Test
    public void testSaveContractorThrowsException() {
        Contractor contractor = new Contractor();
        contractor.setName(null); // Assuming name should not be null
        Executable executable = () -> repository.save(contractor);
        Exception thrownException = assertThrows(RuntimeException.class, executable);
        assertTrue(thrownException.getMessage().contains("Failed to save entity"),
            "Exception message should indicate a not-null constraint violation");
    }




    public static class ContractorRepository extends BaseRepository<Contractor> {

        public ContractorRepository(JdbcTemplate db) {
            super(db);
        }

    }
}
