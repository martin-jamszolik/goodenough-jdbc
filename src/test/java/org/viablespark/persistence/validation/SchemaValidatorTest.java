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
            NullPointerException.class, () -> SchemaValidator.assertMappings(database, (java.util.Collection<Class<? extends Persistable>>) null));
    assertTrue(thrown.getMessage().contains("Entity collection"));
  }

  @Test
  void handlesEntityWithoutPrimaryKey() {
    // Entity without primary key can still be validated - it just won't have PK column checks
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, NoPrimaryKeyEntity.class));
  }

  @Test
  void handlesEmptyEntityCollection() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, java.util.Collections.emptyList()));
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
  void handlesEntityWithSkippedFields() {
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Proposal.class));
  }

  @Test
  void handlesEntityWithRefValueFields() {
    // Note has RefValue fields with progress reference
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Note.class));
  }

  @Test
  void handlesEntityWithSuperclass() {
    // Proposal extends Model which has methods to validate
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(database, Proposal.class));
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

  @Named("fake_table")
  @PrimaryKey("fake_id")
  static class MissingEntity extends Model {
    public RefValue getNothing() {
      return null;
    }

    @SuppressWarnings("unused")
    public void setNothing(RefValue value) {}
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
}
