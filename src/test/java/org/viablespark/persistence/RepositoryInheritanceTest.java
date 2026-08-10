package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.validation.SchemaValidator;

class RepositoryInheritanceTest {
  private EmbeddedDatabase database;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("RepositoryInheritanceTest")
            .build();
    jdbc = new JdbcTemplate(database);
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  void inheritsTableKeyAndFieldsFromAnnotatedParent() {
    BaseRepository<ParentAnnotatedContractor> repository = new BaseRepository<>(jdbc) {};
    ParentAnnotatedContractor contractor = new ParentAnnotatedContractor();
    contractor.setName("Parent annotation");
    contractor.setContact("child field");

    Key key = repository.save(contractor).orElseThrow();
    ParentAnnotatedContractor loaded =
        repository.get(key, ParentAnnotatedContractor.class).orElseThrow();

    assertEquals("Parent annotation", loaded.getName());
    assertEquals("child field", loaded.getContact());

    loaded.setName("Parent annotation updated");
    loaded.setContact("child field updated");
    repository.save(loaded);
    ParentAnnotatedContractor updated =
        repository.get(key, ParentAnnotatedContractor.class).orElseThrow();
    assertEquals("Parent annotation updated", updated.getName());
    assertEquals("child field updated", updated.getContact());
    SchemaValidator.assertMappings(database, ParentAnnotatedContractor.class);
  }

  @Test
  void inheritsFieldsWhenOnlyChildDefinesTableAndKey() {
    BaseRepository<ChildAnnotatedContractor> repository = new BaseRepository<>(jdbc) {};
    ChildAnnotatedContractor contractor = new ChildAnnotatedContractor();
    contractor.setName("Child annotation");
    contractor.setContact("child field");

    Key key = repository.save(contractor).orElseThrow();
    ChildAnnotatedContractor loaded =
        repository.get(key, ChildAnnotatedContractor.class).orElseThrow();

    assertEquals("Child annotation", loaded.getName());
    assertEquals("child field", loaded.getContact());

    loaded.setName("Child annotation updated");
    loaded.setContact("child field updated");
    repository.save(loaded);
    ChildAnnotatedContractor updated =
        repository.get(key, ChildAnnotatedContractor.class).orElseThrow();
    assertEquals("Child annotation updated", updated.getName());
    assertEquals("child field updated", updated.getContact());
    SchemaValidator.assertMappings(database, ChildAnnotatedContractor.class);
  }

  @Test
  void mergesAssignedAndGeneratedCompositeKeyParts() {
    BaseRepository<MixedKeyEntity> repository = new BaseRepository<>(jdbc) {};
    MixedKeyEntity entity = new MixedKeyEntity();
    entity.setTenantId(7L);
    entity.setName("mixed identity");

    Key key = repository.save(entity).orElseThrow();

    assertEquals(7L, key.getKey("tenant_id"));
    assertEquals(1L, key.getKey("generated_id"));
    assertEquals(
        "mixed identity", repository.get(key, MixedKeyEntity.class).orElseThrow().getName());
    SchemaValidator.assertMappings(database, MixedKeyEntity.class);
  }

  @Named("contractor")
  @PrimaryKey("sc_key")
  public abstract static class AnnotatedContractorParent extends Model {
    @Named("sc_name")
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class ParentAnnotatedContractor extends AnnotatedContractorParent {
    private String contact;

    public String getContact() {
      return contact;
    }

    public void setContact(String contact) {
      this.contact = contact;
    }
  }

  public abstract static class UnannotatedContractorParent extends Model {
    @Named("sc_name")
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @Named("contractor")
  @PrimaryKey("sc_key")
  public static class ChildAnnotatedContractor extends UnannotatedContractorParent {
    private String contact;

    public String getContact() {
      return contact;
    }

    public void setContact(String contact) {
      this.contact = contact;
    }
  }

  @Named("mixed_key_entity")
  @PrimaryKey("tenant_id")
  @PrimaryKey("generated_id")
  public static class MixedKeyEntity extends Model {
    @Named("tenant_id")
    private Long tenantId;

    private String name;

    public Long getTenantId() {
      return tenantId;
    }

    public void setTenantId(Long tenantId) {
      this.tenantId = tenantId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
