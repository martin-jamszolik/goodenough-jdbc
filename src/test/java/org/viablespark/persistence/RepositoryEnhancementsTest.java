package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
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
  void supportsPositionalEntityQueries() {
    List<Proposal> results =
        proposalRepository.queryEntity(
            new SqlQuery().where("sc_key = ?", 1L).orderBy("pr_key"), Proposal.class);

    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(proposal -> proposal.getContractor().getId() == 1L));
  }

  @Test
  void supportsCustomMapperQueries() {
    List<Long> ids =
        proposalRepository
            .query(
                SqlQuery.statement(
                    "SELECT * FROM est_proposal WHERE pr_key IN (?, ?) ORDER BY pr_key", 1L, 2L),
                (rowSet, rowNum) -> {
                  Proposal proposal = new Proposal();
                  proposal.setPr_key(rowSet.getLong("pr_key"));
                  return proposal;
                })
            .stream()
            .map(Proposal::getId)
            .toList();

    assertEquals(List.of(1L, 2L), ids);
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
  void supportsEmptyBatchOperations() {
    assertArrayEquals(new int[0], contractorRepository.insertAll(List.of()));
    assertArrayEquals(new int[0], contractorRepository.updateAll(List.of()));
    assertArrayEquals(new int[0], contractorRepository.deleteAll(List.of()));
    assertTrue(contractorRepository.saveAll(List.of()).isEmpty());
  }

  @Test
  void supportsSingleResultExistsAndCountConveniences() {
    assertTrue(proposalRepository.exists(Key.of("pr_key", 1L), Proposal.class));
    assertFalse(proposalRepository.exists(Key.of("pr_key", 999L), Proposal.class));
    assertEquals(
        2L, proposalRepository.count(new SqlQuery().where("sc_key = ?", 1L), Proposal.class));

    Optional<Proposal> single =
        proposalRepository.queryOne(new SqlQuery().where("pr_key = ?", 1L), Proposal.class);
    assertTrue(single.isPresent());
    assertEquals(1L, single.orElseThrow().getId());

    Optional<Proposal> missing =
        proposalRepository.queryOne(new SqlQuery().where("pr_key = ?", 999L), Proposal.class);
    assertTrue(missing.isEmpty());

    assertThrows(
        IllegalStateException.class,
        () -> proposalRepository.queryOne(new SqlQuery().where("sc_key = ?", 1L), Proposal.class));
  }

  @Test
  void supportsPositionalProjectionQueries() {
    List<String> names =
        contractorRepository.queryRows(
            SqlQuery.raw(
                "SELECT sc_name FROM contractor WHERE sc_key IN (?, ?) ORDER BY sc_key", 1L, 2L),
            (rs, rowNum) -> rs.getString("sc_name"));

    assertEquals(List.of("Mr Contractor", "ABC Contractor Inc"), names);
  }

  @Test
  void supportsProjectionQueriesViaProjectionType() {
    List<ContractorProjection> results =
        contractorRepository.queryProjection(
            new SqlQuery()
                .selectColumns("sc_key as id", "sc_name as name")
                .from("contractor")
                .where("sc_key IN (?, ?)", 1L, 2L)
                .orderBy("sc_key"),
            ContractorProjection.class);

    assertEquals(List.of(1L, 2L), results.stream().map(ContractorProjection::id).toList());
  }

  @Test
  void supportsSingleProjectionAndSingleRowQueries() {
    Optional<ContractorProjection> projection =
        contractorRepository.queryProjectionOne(
            SqlQuery.raw(
                "SELECT sc_key as id, sc_name as name FROM contractor WHERE sc_key = ?", 1L),
            ContractorProjection.class);

    assertTrue(projection.isPresent());
    assertEquals("Mr Contractor", projection.orElseThrow().name());

    Optional<String> row =
        contractorRepository.queryRow(
            new SqlQuery().selectColumns("sc_name").from("contractor").where("sc_key = ?", 2L),
            (rs, rowNum) -> rs.getString("sc_name"));

    assertEquals(Optional.of("ABC Contractor Inc"), row);
  }

  @Test
  void rejectsQueryKindsThatDoNotMatchRepositoryOperation() {
    IllegalArgumentException entityException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                contractorRepository.queryEntity(
                    SqlQuery.statement("SELECT * FROM contractor"), Contractor.class));
    IllegalArgumentException rowException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                contractorRepository.queryRows(
                    new SqlQuery().where("sc_key = ?", 1L),
                    (rs, rowNum) -> rs.getString("sc_name")));

    assertEquals(
        "queryEntity requires a SQL fragment; use SqlQuery.fragment(...)",
        entityException.getMessage());
    assertEquals(
        "queryRows requires a complete SQL statement; use SqlQuery.statement(...)",
        rowException.getMessage());
  }

  @Test
  void singleRowOperationsDoNotRewriteStatements() {
    Optional<String> value =
        contractorRepository.queryRow(
            SqlQuery.statement("SELECT sc_name FROM contractor WHERE sc_key = ?;", 1L),
            (rs, rowNum) -> rs.getString("sc_name"));

    assertEquals(Optional.of("Mr Contractor"), value);
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
