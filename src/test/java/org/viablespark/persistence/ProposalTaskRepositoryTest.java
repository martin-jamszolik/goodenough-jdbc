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
import org.viablespark.persistence.dsl.SqlQuery;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProposalTaskRepositoryTest {
    private EmbeddedDatabase db;
    private ProposalTaskRepository repository;
    @Test
    public void testGetProposalWithTasks(){
        var mapper = new ProposalTaskMapper(); // multi relationship mapping
        repository.query(SqlQuery.asRawSql(
            "select * from proposal_task pt " +
                "INNER JOIN est_proposal p on ( pt.pr_key = p.pr_key ) "+
                "INNER JOIN task tsk on (pt.t_key = tsk.t_key) "),
                mapper );
        mapper.getProposals().forEach(
            p -> assertTrue(p.getTasks().size()>0)
        );
    }

    @Test
    public void testInsertWithPKnoAutoGenerate() throws Exception {
        var entity = new ProposalTask();
        repository.getTask(3L).ifPresent(entity::setTask);
        repository.getProposal(1L).ifPresent(entity::setProposal);

        var keyOption = repository.save(entity);
        entity.setRefs( entity.getTask().getRefs() ); //composite pk - Not auto-generated
        keyOption.ifPresent( key -> assertEquals(Key.None, key));
    }


    @BeforeEach
    public void setUp() {
        // creates an HSQL in-memory database populated from default scripts
        // classpath:schema.sql and classpath:data.sql
        db = new EmbeddedDatabaseBuilder()
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
        private final BaseRepository<Proposal> proposalRepository;
        public ProposalTaskRepository(JdbcTemplate db) {
            super(db);
            taskRepository = new BaseRepository<>(db) { };
            proposalRepository = new BaseRepository<>(db) { };
        }

        public Optional<Task> getTask(Long id){
            return taskRepository.get(Key.of("t_key",id),Task.class);
        }
        public Optional<Proposal> getProposal(Long id){
            return proposalRepository.get(Key.of("pr_key",id),Proposal.class);
        }

    }
}