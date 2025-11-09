package org.viablespark.persistence.validation;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.Contractor;
import org.viablespark.persistence.Model;
import org.viablespark.persistence.Note;
import org.viablespark.persistence.Persistable;
import org.viablespark.persistence.Progress;
import org.viablespark.persistence.Proposal;
import org.viablespark.persistence.ProposalTask;
import org.viablespark.persistence.RefValue;
import org.viablespark.persistence.Supplier;
import org.viablespark.persistence.Task;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;

@SuppressWarnings("unused")
class SchemaValidatorTest {

    private EmbeddedDatabase database;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("SchemaValidatorTest")
            .build();
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        database.shutdown();
    }

    @Test
    void validatesMappingsWithoutErrors() {
        assertDoesNotThrow(() -> SchemaValidator.assertMappings(database,
            Contractor.class,
            Proposal.class,
            Note.class,
            Supplier.class,
            ProposalTask.class,
            Task.class,
            Progress.class));
    }

    @Test
    void reportsMissingTable() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
            SchemaValidator.assertMappings(database, MissingEntity.class));
        assertTrue(thrown.getMessage().contains("fake_table"));
    }

    @Test
    void reportsMissingColumn() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
            SchemaValidator.assertMappings(database, MissingColumnEntity.class));
        assertTrue(thrown.getMessage().contains("missing_column"));
    }

    @Named("fake_table")
    @PrimaryKey("fake_id")
    static class MissingEntity extends Model {
        public RefValue getNothing() {
            return null;
        }

        @SuppressWarnings("unused")
        public void setNothing(RefValue value) {
        }
    }

    @Named("contractor")
    @PrimaryKey("sc_key")
    static class MissingColumnEntity implements Persistable {

        private org.viablespark.persistence.Key key = org.viablespark.persistence.Key.None;

        @Override
        public org.viablespark.persistence.Key getRefs() {
            return key;
        }

        @Override
        public void setRefs(org.viablespark.persistence.Key refs) {
            this.key = refs;
        }

        @Named("missing_column")
        public String getBroken() {
            return "";
        }

        @SuppressWarnings("unused")
        public void setBroken(String broken) {
        }
    }
}
