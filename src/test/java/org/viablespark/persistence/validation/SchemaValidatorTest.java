package org.viablespark.persistence.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
import org.viablespark.persistence.dsl.Ref;

@SuppressWarnings("unused")
class SchemaValidatorTest {

  private EmbeddedDatabase database;

  @BeforeEach
  @SuppressWarnings("unused")
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder().addDefaultScripts().setName("SchemaValidatorTest").build();
  }

  @AfterEach
  @SuppressWarnings("unused")
  void tearDown() {
    database.shutdown();
  }

  @Test
  void validatesMappingsWithoutErrors() {
    assertDoesNotThrow(
        () ->
            SchemaValidator.assertMappings(
                database,
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
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(database, MissingEntity.class));
    assertTrue(thrown.getMessage().contains("fake_table"));
  }

  @Test
  void reportsMissingColumn() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(database, MissingColumnEntity.class));
    assertTrue(thrown.getMessage().contains("missing_column"));
  }

  @Test
  void handlesMultipleEntitiesWithSomeInvalid() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                SchemaValidator.assertMappings(
                    database, Contractor.class, MissingEntity.class, Proposal.class));
    assertTrue(thrown.getMessage().contains("fake_table"));
  }

  @Test
  void handlesNullDataSource() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> SchemaValidator.assertMappings(null, Contractor.class));
    assertTrue(thrown.getMessage().contains("DataSource"));
  }

  @Test
  void handlesNullEntityCollection() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () ->
                SchemaValidator.assertMappings(
                    database, (java.util.Collection<Class<? extends Persistable>>) null));
    assertTrue(thrown.getMessage().contains("Entity collection"));
  }

  @Test
  void handlesEntityWithoutPrimaryKey() {
    // Entity without primary key can still be validated - it just won't have PK column checks
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, NoPrimaryKeyEntity.class));
  }

  @Test
  void handlesEmptyEntityCollection() {
    assertDoesNotThrow(
        () -> SchemaValidator.assertMappings(database, java.util.Collections.emptyList()));
  }

  @Test
  void validatesCamelCaseTableNames() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, ProposalTask.class));
  }

  @Test
  void validatesEntityWithRefAnnotation() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, ProposalTask.class));
  }

  @Test
  void reportsIncompleteCompositePrimaryKey() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(database, IncompleteProposalTask.class));

    assertTrue(thrown.getMessage().contains("Primary key"));
    assertTrue(thrown.getMessage().contains("PR_KEY"));
  }

  @Test
  void handlesEntityWithSkippedFields() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Proposal.class));
  }

  @Test
  void handlesEntityWithRefValueFields() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, RefValueEntity.class));
  }

  @Test
  void handlesEntityWithSuperclass() {
    // Proposal extends Model which has methods to validate
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Proposal.class));
  }

  @Test
  void ignoresCollectionMappingsWithoutSkipAnnotation() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, ContractorWithTasks.class));
  }

  @Test
  void handlesCamelCaseFieldNames() {
    // Test camelToSnake conversion for field names
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Progress.class));
  }

  @Test
  void handlesEntityWithBlankRefValue() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Note.class));
  }

  @Test
  void handlesEmptyTableName() {
    // Edge case with empty value check
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Contractor.class));
  }

  @Test
  void reportsMissingSetterForNamedField() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(database, MissingSetterEntity.class));
    assertTrue(thrown.getMessage().contains("Setter 'setBroken'"));
  }

  @Test
  void reportsMissingSetterForConventionMappedField() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(database, MissingConventionSetterEntity.class));
    assertTrue(thrown.getMessage().contains("Setter 'setScName'"));
  }

  @Test
  void reportsInvalidRefValueConfiguration() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(database, InvalidRefValueEntity.class));
    assertTrue(thrown.getMessage().contains("requires both value and label"));
  }

  @Test
  void reportsMissingSetterForRefField() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(database, MissingRefSetterEntity.class));
    assertTrue(thrown.getMessage().contains("Setter 'setContractor'"));
  }

  @Named("fake_table")
  @PrimaryKey("fake_id")
  static class MissingEntity extends Model {
    public RefValue getNothing() {
      return null;
    }

    @SuppressWarnings("unused")
    public void setNothing(RefValue value) {}
  }

  @Named("proposal_task")
  @PrimaryKey("t_key")
  static class IncompleteProposalTask extends Model {}

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
    public void setBroken(String broken) {}
  }

  @Named("contractor")
  static class NoPrimaryKeyEntity implements Persistable {
    private org.viablespark.persistence.Key key = org.viablespark.persistence.Key.None;

    @Override
    public org.viablespark.persistence.Key getRefs() {
      return key;
    }

    @Override
    public void setRefs(org.viablespark.persistence.Key refs) {
      this.key = refs;
    }
  }

  @Named("contractor")
  @PrimaryKey("sc_key")
  static class ContractorWithTasks extends Model {
    private String name;
    private List<Task> tasks = new ArrayList<>();

    @Named("sc_name")
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<Task> getTasks() {
      return tasks;
    }

    public void setTasks(List<Task> tasks) {
      this.tasks = tasks;
    }
  }

  @Named("contractor")
  @PrimaryKey("sc_key")
  static class MissingSetterEntity extends Model {
    @Named("sc_name")
    public String getBroken() {
      return "broken";
    }
  }

  @Named("contractor")
  @PrimaryKey("sc_key")
  static class MissingConventionSetterEntity extends Model {
    public String getScName() {
      return "broken";
    }
  }

  @Named("note")
  @PrimaryKey("n_key")
  static class RefValueEntity extends Model {
    @Ref(value = "progress_id", label = "joined_progress_label")
    public RefValue getProgress() {
      return null;
    }

    public void setProgress(RefValue progress) {}
  }

  @Named("note")
  @PrimaryKey("n_key")
  static class InvalidRefValueEntity extends Model {
    @Ref(value = "progress_id")
    public RefValue getProgress() {
      return null;
    }

    public void setProgress(RefValue progress) {}
  }

  @Named("est_proposal")
  @PrimaryKey("pr_key")
  static class MissingRefSetterEntity extends Model {
    @Ref
    public Contractor getContractor() {
      return null;
    }
  }
}
