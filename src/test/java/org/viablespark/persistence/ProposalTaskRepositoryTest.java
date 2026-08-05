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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

public class ProposalTaskRepositoryTest {
  private EmbeddedDatabase db;
  private JdbcTemplate jdbc;
  private ProposalTaskRepository repository;

  @Test
  public void testGetProposalWithTasks() {
    var mapper = new ProposalTaskMapper(); // multi relationship mapping
    repository.query(
        SqlQuery.raw(
            "select * from proposal_task pt "
                + "INNER JOIN est_proposal p on ( pt.pr_key = p.pr_key ) "
                + "INNER JOIN task tsk on (pt.t_key = tsk.t_key) "),
        mapper);
    mapper.getProposals().forEach(p -> assertFalse(p.getTasks().isEmpty()));
  }

  @Test
  public void testInsertWithPKnoAutoGenerate() throws Exception {
    var entity = new ProposalTask();
    repository.getTask(3L).ifPresent(entity::setTask);
    repository.getProposal(1L).ifPresent(entity::setProposal);

    var keyOption = repository.save(entity);
    Key expected = Key.of("t_key", 3L).add("pr_key", 1L);

    assertEquals(Optional.of(expected), keyOption);
    assertEquals(expected, entity.getRefs());
  }

  @Test
  public void testCompositeKeyCrudUsesEveryKeyPart() {
    jdbc.update("INSERT INTO proposal_task (t_key, pr_key, price) VALUES (?, ?, ?)", 1L, 2L, 400L);
    Key firstKey = Key.of("t_key", 1L).add("pr_key", 1L);
    Key siblingKey = Key.of("t_key", 1L).add("pr_key", 2L);

    ProposalTask first = repository.get(firstKey, ProposalTask.class).orElseThrow();
    ProposalTask sibling = repository.get(siblingKey, ProposalTask.class).orElseThrow();
    assertEquals(200L, first.getPrice());
    assertEquals(400L, sibling.getPrice());
    assertEquals(firstKey, first.getRefs());
    assertTrue(
        repository.get(Key.of("t_key", 1L).add("pr_key", 999L), ProposalTask.class).isEmpty());

    first.setPrice(250L);
    repository.save(first);
    assertEquals(250L, price(firstKey));
    assertEquals(400L, price(siblingKey));

    repository.delete(first);
    assertTrue(repository.get(firstKey, ProposalTask.class).isEmpty());
    assertTrue(repository.get(siblingKey, ProposalTask.class).isPresent());
  }

  @Test
  public void testCompositeOperationsRejectPartialKeysBeforeExecution() {
    Key partial = Key.of("t_key", 1L);

    assertThrows(IllegalArgumentException.class, () -> repository.get(partial, ProposalTask.class));
    ProposalTask task = new ProposalTask();
    task.setRefs(partial);
    task.setPrice(999L);
    assertThrows(IllegalArgumentException.class, () -> repository.save(task));
    assertThrows(IllegalArgumentException.class, () -> repository.delete(task));

    assertEquals(200L, price(Key.of("t_key", 1L).add("pr_key", 1L)));
    assertEquals(300L, price(Key.of("t_key", 2L).add("pr_key", 1L)));
  }

  @Test
  public void testCompositeKeyBatchOperationsUseEveryKeyPart() {
    jdbc.update("INSERT INTO proposal_task (t_key, pr_key, price) VALUES (?, ?, ?)", 1L, 2L, 400L);
    Key firstKey = Key.of("t_key", 1L).add("pr_key", 1L);
    Key siblingKey = Key.of("t_key", 1L).add("pr_key", 2L);
    ProposalTask first = repository.get(firstKey, ProposalTask.class).orElseThrow();

    first.setPrice(275L);
    assertEquals(1, repository.updateAll(List.of(first))[0]);
    assertEquals(275L, price(firstKey));
    assertEquals(400L, price(siblingKey));

    assertEquals(1, repository.deleteAll(List.of(first))[0]);
    assertTrue(repository.get(firstKey, ProposalTask.class).isEmpty());
    assertTrue(repository.get(siblingKey, ProposalTask.class).isPresent());
  }

  @Test
  public void testAttachTasksExplicitly() {
    List<Proposal> proposals =
        repository.proposalRepository.queryEntity(
            new SqlQuery().where("sc_key = ?", 1L).orderBy("pr_key", SqlQuery.Direction.ASC),
            Proposal.class);

    proposals.forEach(proposal -> assertTrue(proposal.getTasks().isEmpty()));

    repository.attachTasks(proposals);

    assertEquals(2, proposals.get(0).getTasks().size());
    assertTrue(proposals.get(1).getTasks().isEmpty());
  }

  @BeforeEach
  public void setUp() {
    // creates an HSQL in-memory database populated from default scripts
    // classpath:schema.sql and classpath:data.sql
    db =
        new EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("ProposalTaskRepositoryTest")
            .build();
    jdbc = new JdbcTemplate(db);
    repository = new ProposalTaskRepository(jdbc);
  }

  @AfterEach
  public void tearDown() {
    db.shutdown();
  }

  private Long price(Key key) {
    Long price =
        jdbc.queryForObject(
            "SELECT price FROM proposal_task WHERE t_key = ? AND pr_key = ?",
            Long.class,
            key.getKey("t_key"),
            key.getKey("pr_key"));
    assertNotNull(price);
    return price;
  }

  public static class ProposalTaskRepository extends BaseRepository<ProposalTask> {

    private final BaseRepository<Task> taskRepository;
    final BaseRepository<Proposal> proposalRepository;

    public ProposalTaskRepository(JdbcTemplate db) {
      super(db);
      taskRepository = new BaseRepository<>(db) {};
      proposalRepository = new BaseRepository<>(db) {};
    }

    public Optional<Task> getTask(Long id) {
      return taskRepository.get(Key.of("t_key", id), Task.class);
    }

    public Optional<Proposal> getProposal(Long id) {
      return proposalRepository.get(Key.of("pr_key", id), Proposal.class);
    }

    public void attachTasks(List<Proposal> proposals) {
      RelationLoader.attachOneToMany(
          proposals,
          this::listByProposalIds,
          proposal -> proposal.getRefs().primaryKey().getValue(),
          proposalTask -> proposalTask.getProposal().getRefs().primaryKey().getValue(),
          Proposal::setTasks);
    }

    private List<ProposalTask> listByProposalIds(List<Long> proposalIds) {
      if (proposalIds.isEmpty()) {
        return List.of();
      }
      return query(
          SqlQuery.statement(
              "SELECT * FROM proposal_task WHERE pr_key IN ("
                  + "?"
                  + ", ?".repeat(proposalIds.size() - 1)
                  + ") ORDER BY pr_key, t_key",
              proposalIds.toArray()),
          PersistableRowMapper.of(ProposalTask.class));
    }
  }
}
