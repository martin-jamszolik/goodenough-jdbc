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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.dsl.SqlQuery;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(Lifecycle.PER_CLASS)
public class ProposalRepositoryTest {

    final Logger logger = LoggerFactory.getLogger(ProposalRepositoryTest.class);

    private EmbeddedDatabase db;
    private ProposalRepository repository;

    @BeforeEach
    public void setUp() {
        // creates an HSQL in-memory database populated from default scripts
        // classpath:schema.sql and classpath:data.sql
        db = new EmbeddedDatabaseBuilder()
                .addDefaultScripts()
                .setName("ProposalTest")
                .build();
        repository = new ProposalRepository(new JdbcTemplate(db));
    }

    @AfterEach
    public void tearDown() {
        db.shutdown();
    }

    @Test
    public void testSave() throws Exception {
        Proposal proposal = new Proposal();
        proposal.setDistance(123);
        proposal.setPropDate(new Date());
        proposal.setPropName("Name1");
        proposal.setPropId("ID2");
        proposal.setSubmitDeadline(LocalDate.now());

        proposal.setContractor(new Contractor("sc_key",1L)); //foreign-key

        Optional<Key> optionalKey = repository.save(proposal);

        logger.info("Entity Saved, let's check valid keys");

        assertTrue(optionalKey.isPresent());
        var key = optionalKey.get();
        assertEquals(1, key.count());
        assertNotNull(key.primaryKey());
        assertEquals("pr_key",key.primaryKey().getKey());

        logger.info("Let's update");

        var foundOption = repository.get(key,Proposal.class);
        assertTrue(foundOption.isPresent());
        var found = foundOption.get();
        found.setDistance(567);
        found.setPropName("Saved again");
        var keyOption = repository.save(found);
        keyOption.ifPresent( k -> assertEquals(key,k) );
        repository.get(key,Proposal.class)
            .ifPresent( f -> assertEquals("Saved again",f.getPropName()));
    }

    @Test
    public void testDelete() {
        Proposal e = new Proposal();
        e.setRefs(Key.of("pr_key", 1L));
        repository.delete(e);
        logger.info("Entity Deleted");
    }

    @Test
    public void testGet() {
        Optional<Proposal> result = repository.get(Key.of("pr_key", 1L), Proposal.class);
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getRefs().primaryKey().getValue());
    }

    @Test
    public void testQuery() {

        List<Proposal> results = repository.queryEntity(SqlQuery
                .withClause("WHERE dist >= ?", 10),Proposal.class);

        assertEquals(3, results.size());
        
        results.forEach(e -> assertTrue(e.getId() > 0));

    }

    @Test
    public void testRowQuery(){
        var mapper = new ProposalMapper();
        var results = repository.query(
            SqlQuery.asRaw("select * from est_proposal p " +
                "INNER JOIN contractor c on (c.sc_key = p.sc_key) "+
                "where dist > 0"),
            mapper);

        results.forEach(e -> assertTrue(e.getId() > 0));
        assertEquals(2, mapper.getContractors().size());
    }

    @Test
    public void testRowSetQuery() {
        
        List<Proposal> results = repository.query(SqlQuery
                .asRaw("select * from est_proposal"),
                (rowSet,row) -> {
                    Proposal e = new Proposal();
                    e.setPr_key(rowSet.getLong("pr_key"));
                    e.setDistance(rowSet.getInt("dist"));
                    e.setPropId(rowSet.getString("prop_id"));
                    e.setPropName(rowSet.getString("proposal_name"));
                    e.setSubmitDeadline(rowSet.getDate("submit_deadline").toLocalDate());
                    e.setContractor(
                        new Contractor("sc_key",rowSet.getLong("sc_key")));
                    return e;
                });

        assertEquals(3, results.size());
        
        results.forEach(e -> assertTrue(e.getId() > 0));
    }

    
    public static class ProposalRepository extends BaseRepository<Proposal> {

        public ProposalRepository(JdbcTemplate db) {
            super(db);
        }

    }

}
