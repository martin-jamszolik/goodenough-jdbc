package org.viablespark.persistence.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.viablespark.persistence.Key;
import org.viablespark.persistence.Model;
import org.viablespark.persistence.Persistable;
import org.viablespark.persistence.RefValue;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;

/** Advanced tests for SchemaValidator focusing on edge cases, error paths, and SQL exceptions. */
class SchemaValidatorAdvancedTest {

  @Test
  void handlesSQLExceptionDuringConnection() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(mockDataSource, TestEntity.class));
    assertTrue(thrown.getMessage().contains("Failed to validate schema mappings"));
    assertNotNull(thrown.getCause());
    assertTrue(thrown.getCause() instanceof SQLException);
  }

  @Test
  void handlesEmptyColumnMetadata() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet emptyColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    // Table exists
    when(mockMetaData.getTables(isNull(), isNull(), eq("test_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("test_entity");

    // But no columns found
    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(emptyColumnResultSet);
    when(emptyColumnResultSet.next()).thenReturn(false);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(mockDataSource, TestEntity.class));
    assertTrue(thrown.getMessage().contains("No column metadata available"));
  }

  @Test
  void handlesNullColumnNameInMetadata() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(isNull(), isNull(), eq("test_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("test_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME")).thenReturn(null).thenReturn("ID");

    // Null columns are filtered, but we still need at least the ID column
    // This should fail because expected column 'NAME' won't be found
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> SchemaValidator.assertMappings(mockDataSource, TestEntity.class));
    assertTrue(thrown.getMessage().contains("Column") || thrown.getMessage().contains("missing"));

    verify(mockConnection).close();
  }

  @Test
  void handlesBlankColumnName() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(isNull(), isNull(), eq("blank_column_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("blank_column_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME")).thenReturn("ID");

    // Should not throw - blank column expectations are skipped
    assertDoesNotThrow(
        () -> SchemaValidator.assertMappings(mockDataSource, BlankColumnEntity.class));

    verify(mockConnection).close();
  }

  @Test
  void handlesRefValueWithoutRefAnnotation() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(isNull(), isNull(), eq("ref_value_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("ref_value_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME")).thenReturn("ID");

    // Should handle RefValue without @Ref annotation
    assertDoesNotThrow(
        () -> SchemaValidator.assertMappings(mockDataSource, RefValueEntityNoRef.class));

    verify(mockConnection).close();
  }

  @Test
  void handlesRefValueWithBlankRefValue() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(
            isNull(), isNull(), eq("ref_value_blank_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("ref_value_blank_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME")).thenReturn("ID");

    // Should skip blank Ref values
    assertDoesNotThrow(
        () -> SchemaValidator.assertMappings(mockDataSource, RefValueBlankRefEntity.class));

    verify(mockConnection).close();
  }

  @Test
  void handlesRefAnnotationWithNullPrimaryKey() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(isNull(), isNull(), eq("ref_null_pk_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("ref_null_pk_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME")).thenReturn("ID");

    // Should handle null primary key on referenced entity
    assertDoesNotThrow(() -> SchemaValidator.assertMappings(mockDataSource, RefNullPKEntity.class));

    verify(mockConnection).close();
  }

  @Test
  void handlesNamedAnnotationWithBlankValue() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(isNull(), isNull(), eq("blank_named_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("blank_named_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME")).thenReturn("ID");

    // Should skip blank @Named values
    assertDoesNotThrow(
        () -> SchemaValidator.assertMappings(mockDataSource, BlankNamedEntity.class));

    verify(mockConnection).close();
  }

  @Test
  void handlesRefAnnotationWithBlankNamedValue() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(
            isNull(), isNull(), eq("ref_blank_named_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("ref_blank_named_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME")).thenReturn("ID");

    // Should skip when both @Ref and @Named are blank
    assertDoesNotThrow(
        () -> SchemaValidator.assertMappings(mockDataSource, RefBlankNamedEntity.class));

    verify(mockConnection).close();
  }

  @Test
  void handlesCamelCaseConversion() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(
            isNull(), isNull(), eq("camel_case_test_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("camel_case_test_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next())
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME"))
        .thenReturn("ID")
        .thenReturn("MY_COMPLEX_FIELD")
        .thenReturn("ANOTHER_VALUE");

    assertDoesNotThrow(
        () -> SchemaValidator.assertMappings(mockDataSource, CamelCaseTestEntity.class));

    verify(mockConnection).close();
  }

  @Test
  void handlesEntityWithSuperclassHavingPrimaryKey() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
    ResultSet mockTableResultSet = mock(ResultSet.class);
    ResultSet mockColumnResultSet = mock(ResultSet.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    when(mockMetaData.getTables(isNull(), isNull(), eq("child_entity"), any(String[].class)))
        .thenReturn(mockTableResultSet);
    when(mockTableResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockTableResultSet.getString("TABLE_NAME")).thenReturn("child_entity");

    when(mockMetaData.getColumns(isNull(), isNull(), anyString(), isNull()))
        .thenReturn(mockColumnResultSet);
    when(mockColumnResultSet.next())
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false);
    when(mockColumnResultSet.getString("COLUMN_NAME"))
        .thenReturn("ID")
        .thenReturn("PARENT_ID")
        .thenReturn("CHILD_NAME");

    assertDoesNotThrow(() -> SchemaValidator.assertMappings(mockDataSource, ChildEntity.class));

    verify(mockConnection).close();
  }

  // Test entity classes
  @PrimaryKey("id")
  @Named("test_entity")
  static class TestEntity implements Persistable {
    private Key refs;

    public String getName() {
      return "test";
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @Named("blank_column_entity")
  static class BlankColumnEntity implements Persistable {
    private Key refs;

    @Named("")
    public String getBlankField() {
      return "";
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @Named("ref_value_entity")
  static class RefValueEntityNoRef implements Persistable {
    private Key refs;

    public RefValue getRefField() {
      return null;
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @Named("ref_value_blank_entity")
  static class RefValueBlankRefEntity implements Persistable {
    private Key refs;

    @Ref(value = "", label = "")
    public RefValue getRefField() {
      return null;
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  static class NoPKEntity implements Persistable {
    private Key refs;

    public String getName() {
      return "";
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @Named("ref_null_pk_entity")
  static class RefNullPKEntity implements Persistable {
    private Key refs;

    @Ref
    public NoPKEntity getRelated() {
      return null;
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @Named("blank_named_entity")
  static class BlankNamedEntity implements Persistable {
    private Key refs;

    @Named("  ")
    public String getField() {
      return "";
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @Named("ref_blank_named_entity")
  static class RefBlankNamedEntity implements Persistable {
    private Key refs;

    @Ref
    @Named("  ")
    public TestEntity getRelated() {
      return null;
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @Named("camel_case_test_entity")
  static class CamelCaseTestEntity implements Persistable {
    private Key refs;

    public String getMyComplexField() {
      return "";
    }

    public String getAnotherValue() {
      return "";
    }

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key key) {
      this.refs = key;
    }
  }

  @PrimaryKey("parent_id")
  static class ParentEntity extends Model {}

  @Named("child_entity")
  static class ChildEntity extends ParentEntity {
    public String getChildName() {
      return "";
    }
  }
}
