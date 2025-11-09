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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.dsl.SqlQuery;

public class NoteRepositoryTest {

    private EmbeddedDatabase db;
    private NoteRepository repository;

    private BaseRepository<Progress> progress;

    @Test
    public void testInsertNote() throws Exception {
        var entity = new Note();
        entity.setNoteContent("test");
        entity.setExtra("extra");
        entity.setDateTaken(LocalDate.now());
        entity.setProgress(new Progress());
        entity.getProgress().setPercent(new BigDecimal("50"));

        progress.save(entity.getProgress());

        var keyOption = repository.save(entity);
        keyOption.ifPresent( key -> assertEquals(3L, key.primaryKey().getValue()));
        assertEquals( 2L,entity.getProgress().getRefs().primaryKey().getValue());
        assertEquals( "id",entity.getProgress().getRefs().primaryKey().getKey());
    }

    @Test
    public void testSelectNote() throws Exception {
        var found = repository.get(Key.of("n_key",1L),Note.class);

        found.ifPresent( n -> assertEquals(1L, n.getRefs().primaryKey().getValue()));
        found.ifPresent( n -> assertNotNull( n.getProgress().getRefs()));
        found.ifPresent( n -> assertNotNull( n.getDateTaken()));
    }


    @Test
    public void testQueryNote() throws Exception {
        var found = repository.queryEntity(new SqlQuery()
            .where("progress_id = ?", 1)
            .primaryKey("n_key"), Note.class);
       assertTrue( found.size() > 1);

    }

    @Test
    public void testQueryOnNotePersistable() throws Exception {
        var repo = new BaseRepository<NotePersistable>(new JdbcTemplate(db)){};

        var plainFound = repo.queryEntity(new SqlQuery()
            .where("progress_id = ?", 1)
            .primaryKey("n_key"), NotePersistable.class);

        assertTrue( plainFound.size() > 1);
    }



    @Test
    public void testUsingPersistableInAMap() throws Exception {
        Map<Persistable,Key> map = new HashMap<>();
        Note note = new Note();
        map.put(note,Key.of("blah",1L) );
        assertTrue(map.containsKey(note));

    }


    @BeforeEach
    public void setUp() {
        // creates an HSQL in-memory database populated from default scripts
        // classpath:schema.sql and classpath:data.sql
        db = new EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("ProposalTaskRepositoryTest")
            .build();
        repository = new NoteRepository(new JdbcTemplate(db));
        progress = new BaseRepository<>(new JdbcTemplate(db)) {};
    }
    @AfterEach
    public void tearDown() {
        db.shutdown();
    }


    public static class NoteRepository extends BaseRepository<Note> {
        public NoteRepository(JdbcTemplate db) {
            super(db);
        }
    }
}
