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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.dsl.SqlQuery;

@TestInstance(Lifecycle.PER_CLASS)
public class BaseRepositoryEdgeCasesTest {

  private EmbeddedDatabase db;
  private TestProposalRepository repository;

  @BeforeEach
  public void setUp() {
    db = new EmbeddedDatabaseBuilder().addDefaultScripts().setName("EdgeCasesTest").build();
    repository = new TestProposalRepository(new JdbcTemplate(db));
  }

  @AfterEach
  public void tearDown() {
    db.shutdown();
  }

  @Test
  public void testSaveWithDatabaseError() {
    Proposal proposal = new Proposal();
    proposal.setPropName(null);
    proposal.setPropId("TEST_ID");

    try {
      repository.save(proposal);
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("Failed to save entity"));
    }
  }

  @Test
  public void testGetNonExistentEntity() {
    Key nonExistentKey = Key.of("pr_key", 99999L);
    Optional<Proposal> result = repository.get(nonExistentKey, Proposal.class);
    assertFalse(result.isPresent());
  }

  @Test
  public void testQueryEntityWithComplexQuery() {
    SqlQuery query =
        new SqlQuery()
            .where("pr_key IS NOT NULL")
            .andWhere("proposal_name IS NOT NULL")
            .orderBy("pr_key", SqlQuery.Direction.DESC)
            .limit(10);

    List<Proposal> results = repository.queryEntity(query, Proposal.class);
    assertNotNull(results);
  }

  @Test
  public void testQueryEntityWithNoResults() {
    SqlQuery query = new SqlQuery().where("pr_key = ?", -1L);
    List<Proposal> results = repository.queryEntity(query, Proposal.class);
    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  public void testQueryWithCustomMapper() {
    SqlQuery query = SqlQuery.raw("SELECT * FROM est_proposal WHERE pr_key > ?", 0);
    PersistableMapper<Proposal> mapper = PersistableRowMapper.of(Proposal.class);
    List<Proposal> results = repository.query(query, mapper);
    assertNotNull(results);
  }

  @Test
  public void testQueryEntityWithPrimaryKeyFromQuery() {
    SqlQuery query = new SqlQuery().primaryKey("pr_key").where("pr_key > ?", 0).limit(5);
    List<Proposal> results = repository.queryEntity(query, Proposal.class);
    assertNotNull(results);
  }

  @Test
  public void testDeleteEntity() {
    // First create a contractor (required foreign key)
    Contractor contractor = new Contractor();
    contractor.setName("Test Contractor");
    contractor.setContact("test@example.com");

    TestContractorRepository contractorRepo = new TestContractorRepository(new JdbcTemplate(db));
    Optional<Key> contractorKey = contractorRepo.save(contractor);
    assertTrue(contractorKey.isPresent());

    // Now create an entity
    Proposal proposal = new Proposal();
    proposal.setPropName("To Delete");
    proposal.setPropId("DELETE_ME");
    proposal.setPropDate(new Date());
    proposal.setContractor(new Contractor("sc_key", contractorKey.get().primaryKey().getValue()));

    Optional<Key> keyOpt = repository.save(proposal);
    assertTrue(keyOpt.isPresent());

    // Now delete it
    proposal.setRefs(keyOpt.get());
    assertDoesNotThrow(() -> repository.delete(proposal));

    // Verify it's gone
    Optional<Proposal> deleted = repository.get(keyOpt.get(), Proposal.class);
    assertFalse(deleted.isPresent());
  }

  @Test
  public void testSaveUpdatePath() {
    // Create contractor first
    Contractor contractor = new Contractor();
    contractor.setName("Update Test Contractor");
    contractor.setContact("update@example.com");

    TestContractorRepository contractorRepo = new TestContractorRepository(new JdbcTemplate(db));
    Optional<Key> contractorKey = contractorRepo.save(contractor);
    assertTrue(contractorKey.isPresent());

    // Create entity
    Proposal proposal = new Proposal();
    proposal.setPropName("Original Name");
    proposal.setPropId("UPDATE_TEST");
    proposal.setPropDate(new Date());
    proposal.setContractor(new Contractor("sc_key", contractorKey.get().primaryKey().getValue()));

    Optional<Key> key = repository.save(proposal);
    assertTrue(key.isPresent());

    // Update it
    Optional<Proposal> loaded = repository.get(key.get(), Proposal.class);
    assertTrue(loaded.isPresent());

    Proposal toUpdate = loaded.get();
    toUpdate.setPropName("Updated Name");
    Optional<Key> updateKey = repository.save(toUpdate);

    assertTrue(updateKey.isPresent());
    assertEquals(key.get(), updateKey.get());

    // Verify update
    Optional<Proposal> updated = repository.get(key.get(), Proposal.class);
    assertTrue(updated.isPresent());
    assertEquals("Updated Name", updated.get().getPropName());
  }

  @Test
  public void testEntityWithNullRefs() {
    // Create contractor first
    Contractor contractor = new Contractor();
    contractor.setName("Null Refs Contractor");
    contractor.setContact("nullrefs@example.com");

    TestContractorRepository contractorRepo = new TestContractorRepository(new JdbcTemplate(db));
    Optional<Key> contractorKey = contractorRepo.save(contractor);
    assertTrue(contractorKey.isPresent());

    Proposal proposal = new Proposal();
    proposal.setPropName("Test");
    proposal.setPropId("NULL_REFS");
    proposal.setPropDate(new Date());
    proposal.setContractor(new Contractor("sc_key", contractorKey.get().primaryKey().getValue()));
    proposal.setRefs(null); // Explicitly null refs

    assertTrue(proposal.isNew());

    Optional<Key> key = repository.save(proposal);
    assertTrue(key.isPresent());
  }

  @Test
  public void testCamelToSnakeConversionForNonAnnotatedClass() {
    Progress progress = new Progress();
    progress.setPercent(new java.math.BigDecimal("50.00"));

    TestProgressRepository progressRepo = new TestProgressRepository(new JdbcTemplate(db));
    Optional<Key> key = progressRepo.save(progress);
    assertTrue(key.isPresent());
  }

  @Test
  public void testQueryWithInvalidSqlThrowsException() {
    SqlQuery badQuery = SqlQuery.raw("SELECT * FROM proposal WHERE pr_key = ? AND name = ?", 1L);
    // Only 1 value but 2 placeholders
    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              repository.queryEntity(badQuery, Proposal.class);
            });
    assertNotNull(exception);
  }

  @Test
  public void testCustomQueryWithException() {
    SqlQuery badQuery = SqlQuery.raw("SELECT * FROM nonexistent_table");
    PersistableMapper<Proposal> mapper = PersistableRowMapper.of(Proposal.class);

    Exception exception =
        assertThrows(
            DataAccessException.class,
            () -> {
              repository.query(badQuery, mapper);
            });
    assertNotNull(exception);
  }

  @Test
  public void testGetExistingEntityFromStagedData() {
    TestContractorRepository contractorRepo = new TestContractorRepository(new JdbcTemplate(db));
    Optional<Contractor> contractor = contractorRepo.get(Key.of("sc_key", 1L), Contractor.class);

    assertTrue(contractor.isPresent());
    assertEquals("Mr Contractor", contractor.get().getName());
    assertEquals(1L, contractor.get().getRefs().primaryKey().getValue());
  }

  @Test
  public void testQueryEntityWithStagedData() {
    SqlQuery query =
        new SqlQuery().where("sc_key = ?", 1L).orderBy("pr_key", SqlQuery.Direction.ASC);

    List<Proposal> proposals = repository.queryEntity(query, Proposal.class);
    assertNotNull(proposals);
    assertEquals(2, proposals.size()); // Staged data has 2 proposals for contractor 1
  }

  @Test
  public void testUpdateExistingEntityFromStagedData() {
    Optional<Proposal> proposal = repository.get(Key.of("pr_key", 1L), Proposal.class);
    assertTrue(proposal.isPresent());

    Proposal toUpdate = proposal.get();
    String originalName = toUpdate.getPropName();
    toUpdate.setPropName("Updated from Test");

    Optional<Key> updateKey = repository.save(toUpdate);
    assertTrue(updateKey.isPresent());

    // Verify update
    Optional<Proposal> updated = repository.get(Key.of("pr_key", 1L), Proposal.class);
    assertTrue(updated.isPresent());
    assertEquals("Updated from Test", updated.get().getPropName());

    // Restore original for other tests
    toUpdate.setPropName(originalName);
    repository.save(toUpdate);
  }

  @Test
  public void testQueryWithLimitAndOffsetOnStagedData() {
    // Test pagination with staged data (3 proposals total)
    SqlQuery query =
        new SqlQuery()
            .where("pr_key > ?", 0L)
            .orderBy("pr_key", SqlQuery.Direction.ASC)
            .limit(2)
            .offset(1);

    List<Proposal> proposals = repository.queryEntity(query, Proposal.class);
    assertNotNull(proposals);
    assertEquals(2, proposals.size());
  }

  // Inner test repository classes
  public static class TestProposalRepository extends BaseRepository<Proposal> {
    public TestProposalRepository(JdbcTemplate db) {
      super(db);
    }
  }

  public static class TestContractorRepository extends BaseRepository<Contractor> {
    public TestContractorRepository(JdbcTemplate db) {
      super(db);
    }
  }

  public static class TestProgressRepository extends BaseRepository<Progress> {
    public TestProgressRepository(JdbcTemplate db) {
      super(db);
    }
  }
}
