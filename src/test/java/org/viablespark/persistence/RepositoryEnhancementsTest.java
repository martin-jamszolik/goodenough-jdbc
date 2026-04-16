package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.dsl.NamedSqlQuery;
import org.viablespark.persistence.dsl.SqlQuery;

class RepositoryEnhancementsTest {

  private EmbeddedDatabase db;
  private ContractorRepository contractorRepository;
  private ProposalRepository proposalRepository;

  @BeforeEach
  void setUp() {
    db =
        new EmbeddedDatabaseBuilder().addDefaultScripts().setName("RepositoryEnhancements").build();
    contractorRepository = new ContractorRepository(new JdbcTemplate(db));
    proposalRepository = new ProposalRepository(new JdbcTemplate(db));
  }

  @AfterEach
  void tearDown() {
    db.shutdown();
  }

  @Test
  void supportsNamedParameterEntityQueries() {
    List<Proposal> results =
        proposalRepository.queryEntity(
            NamedSqlQuery.raw(
                "WHERE sc_key = :contractorId ORDER BY pr_key", Map.of("contractorId", 1L)),
            Proposal.class);

    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(proposal -> proposal.getContractor().getId() == 1L));
  }

  @Test
  void supportsNamedParameterProjectionQueries() {
    List<ContractorProjection> results =
        contractorRepository.queryRows(
            NamedSqlQuery.raw(
                "SELECT sc_key, sc_name FROM contractor WHERE sc_key IN (:ids) ORDER BY sc_key",
                Map.of("ids", List.of(1L, 2L))),
            (rs, rowNum) ->
                new ContractorProjection(rs.getLong("sc_key"), rs.getString("sc_name")));

    assertEquals(List.of(1L, 2L), results.stream().map(ContractorProjection::id).toList());
  }

  @Test
  void supportsBatchInsertOperations() {
    Contractor first = contractor("Batch Insert One", "one@example.com");
    Contractor second = contractor("Batch Insert Two", "two@example.com");

    assertArrayEquals(new int[] {1, 1}, contractorRepository.insertAll(List.of(first, second)));
    assertEquals(
        2L,
        contractorRepository.count(
            new SqlQuery().where("sc_name IN (?, ?)", "Batch Insert One", "Batch Insert Two"),
            Contractor.class));
  }

  @Test
  void supportsBatchUpdateAndDeleteOperations() {
    Contractor first = contractor("Batch Update One", "one@example.com");
    Contractor second = contractor("Batch Update Two", "two@example.com");

    List<Optional<Key>> saved = contractorRepository.saveAll(List.of(first, second));
    assertTrue(saved.stream().allMatch(Optional::isPresent));

    first.setName("Batch Updated One");
    second.setName("Batch Updated Two");

    assertArrayEquals(new int[] {1, 1}, contractorRepository.updateAll(List.of(first, second)));
    assertEquals(
        2L,
        contractorRepository.count(
            new SqlQuery().where("sc_name IN (?, ?)", "Batch Updated One", "Batch Updated Two"),
            Contractor.class));

    assertArrayEquals(new int[] {1, 1}, contractorRepository.deleteAll(List.of(first, second)));
    assertEquals(
        0L,
        contractorRepository.count(
            new SqlQuery().where("sc_name IN (?, ?)", "Batch Updated One", "Batch Updated Two"),
            Contractor.class));
  }

  @Test
  void supportsSingleResultExistsAndCountConveniences() {
    assertTrue(proposalRepository.exists(Key.of("pr_key", 1L), Proposal.class));
    assertEquals(
        2L, proposalRepository.count(new SqlQuery().where("sc_key = ?", 1L), Proposal.class));

    Optional<Proposal> single =
        proposalRepository.queryOne(new SqlQuery().where("pr_key = ?", 1L), Proposal.class);
    assertTrue(single.isPresent());
    assertEquals(1L, single.orElseThrow().getId());

    assertThrows(
        IllegalStateException.class,
        () -> proposalRepository.queryOne(new SqlQuery().where("sc_key = ?", 1L), Proposal.class));
  }

  private Contractor contractor(String name, String contact) {
    Contractor contractor = new Contractor();
    contractor.setName(name);
    contractor.setContact(contact);
    return contractor;
  }

  private record ContractorProjection(Long id, String name) {}

  private static class ContractorRepository extends BaseRepository<Contractor> {
    private ContractorRepository(JdbcTemplate db) {
      super(db);
    }
  }

  private static class ProposalRepository extends BaseRepository<Proposal> {
    private ProposalRepository(JdbcTemplate db) {
      super(db);
    }
  }
}
