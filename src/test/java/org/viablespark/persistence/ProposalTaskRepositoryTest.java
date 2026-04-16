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

public class ProposalTaskRepositoryTest {
  private EmbeddedDatabase db;
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
    entity.setRefs(entity.getTask().getRefs()); // composite pk - Not auto-generated
    keyOption.ifPresent(key -> assertEquals(Key.None, key));
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
    repository = new ProposalTaskRepository(new JdbcTemplate(db));
  }

  @AfterEach
  public void tearDown() {
    db.shutdown();
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
          NamedSqlQuery.raw(
              "SELECT * FROM proposal_task WHERE pr_key IN (:proposalIds) ORDER BY pr_key, t_key",
              Map.of("proposalIds", proposalIds)),
          PersistableRowMapper.of(ProposalTask.class));
    }
  }
}
