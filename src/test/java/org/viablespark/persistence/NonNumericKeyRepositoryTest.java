package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.SqlQuery;
import org.viablespark.persistence.validation.SchemaValidator;

class NonNumericKeyRepositoryTest {
  private EmbeddedDatabase database;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder().addDefaultScripts().setName("NonNumericKeyTest").build();
    jdbc = new JdbcTemplate(database);
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  void supportsStringAndUuidRepositoryCrud() {
    BaseRepository<StringKeyEntity> strings = new BaseRepository<>(jdbc) {};
    BaseRepository<UuidKeyEntity> uuids = new BaseRepository<>(jdbc) {};
    UUID uuid = UUID.randomUUID();

    StringKeyEntity stringEntity = new StringKeyEntity();
    stringEntity.setCode("customer-one");
    stringEntity.setName("Customer One");
    UuidKeyEntity uuidEntity = new UuidKeyEntity();
    uuidEntity.setEntityId(uuid);
    uuidEntity.setName("UUID One");

    assertEquals(Key.of("code", "customer-one"), strings.save(stringEntity).orElseThrow());
    assertEquals(Key.of("entity_id", uuid), uuids.save(uuidEntity).orElseThrow());
    assertEquals("customer-one", stringEntity.getIdentifier(String.class));
    assertEquals(uuid, uuidEntity.getIdentifier(UUID.class));
    assertThrows(IllegalStateException.class, stringEntity::getId);

    stringEntity.setName("Customer Updated");
    uuidEntity.setName("UUID Updated");
    assertEquals(1, strings.updateAll(List.of(stringEntity))[0]);
    uuids.save(uuidEntity);

    assertEquals(
        "Customer Updated",
        strings.get(Key.of("code", "customer-one"), StringKeyEntity.class).orElseThrow().getName());
    assertEquals(
        "UUID Updated",
        uuids.get(Key.of("entity_id", uuid), UuidKeyEntity.class).orElseThrow().getName());
    assertTrue(strings.exists(Key.of("code", "customer-one"), StringKeyEntity.class));

    strings.delete(stringEntity);
    uuids.delete(uuidEntity);
    assertTrue(strings.get(Key.of("code", "customer-one"), StringKeyEntity.class).isEmpty());
    assertTrue(uuids.get(Key.of("entity_id", uuid), UuidKeyEntity.class).isEmpty());
  }

  @Test
  void supportsStringEntityReferencesAndUuidRefValues() {
    BaseRepository<StringKeyEntity> strings = new BaseRepository<>(jdbc) {};
    BaseRepository<UuidKeyEntity> uuids = new BaseRepository<>(jdbc) {};
    BaseRepository<NonNumericRefHolder> holders = new BaseRepository<>(jdbc) {};
    UUID uuid = UUID.randomUUID();

    StringKeyEntity stringEntity = new StringKeyEntity();
    stringEntity.setCode("supplier-code");
    stringEntity.setName("String Supplier");
    strings.save(stringEntity);
    UuidKeyEntity uuidEntity = new UuidKeyEntity();
    uuidEntity.setEntityId(uuid);
    uuidEntity.setName("UUID Supplier");
    uuids.save(uuidEntity);

    NonNumericRefHolder holder = new NonNumericRefHolder();
    holder.setHolderId("holder-one");
    holder.setStringEntity(stringEntity);
    holder.setUuidRef(RefValue.of("not persisted", "uuid_id", uuid));
    Key holderKey = holders.save(holder).orElseThrow();

    NonNumericRefHolder generated = holders.get(holderKey, NonNumericRefHolder.class).orElseThrow();
    assertEquals("supplier-code", generated.getStringEntity().getIdentifier(String.class));
    assertEquals(uuid, generated.getUuidRef().referenceValue(UUID.class));
    assertNull(generated.getUuidRef().getValue());

    NonNumericRefHolder joined =
        holders
            .query(
                SqlQuery.statement(
                    "SELECT h.*, u.name AS uuid_name FROM non_numeric_ref_holder h "
                        + "JOIN uuid_key_entity u ON u.entity_id = h.uuid_id "
                        + "WHERE h.holder_id = ?",
                    "holder-one"),
                PersistableRowMapper.of(NonNumericRefHolder.class))
            .get(0);
    assertEquals("UUID Supplier", joined.getUuidRef().getValue());

    generated.setStringEntity(null);
    generated.setUuidRef(null);
    holders.save(generated);
    NonNumericRefHolder cleared = holders.get(holderKey, NonNumericRefHolder.class).orElseThrow();
    assertNull(cleared.getStringEntity());
    assertNull(cleared.getUuidRef());

    SchemaValidator.assertMappings(
        database, StringKeyEntity.class, UuidKeyEntity.class, NonNumericRefHolder.class);
  }

  @Test
  void mergesAssignedStringAndGeneratedCompositeKeyParts() {
    BaseRepository<MixedStringKeyEntity> repository = new BaseRepository<>(jdbc) {};
    MixedStringKeyEntity entity = new MixedStringKeyEntity();
    entity.setTenantCode("tenant-one");
    entity.setName("Mixed String Key");

    Key key = repository.save(entity).orElseThrow();

    assertEquals("tenant-one", key.value("tenant_code", String.class));
    assertEquals(1L, key.value("generated_id", Long.class));
    assertEquals(
        "Mixed String Key",
        repository.get(key, MixedStringKeyEntity.class).orElseThrow().getName());
    SchemaValidator.assertMappings(database, MixedStringKeyEntity.class);
  }

  @Named("string_key_entity")
  @PrimaryKey("code")
  public static class StringKeyEntity extends Model {
    private String code;
    private String name;

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @Named("uuid_key_entity")
  @PrimaryKey("entity_id")
  public static class UuidKeyEntity extends Model {
    private UUID entityId;
    private String name;

    public UUID getEntityId() {
      return entityId;
    }

    public void setEntityId(UUID entityId) {
      this.entityId = entityId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @Named("non_numeric_ref_holder")
  @PrimaryKey("holder_id")
  public static class NonNumericRefHolder extends Model {
    private String holderId;
    private StringKeyEntity stringEntity;
    private RefValue uuidRef;

    public String getHolderId() {
      return holderId;
    }

    public void setHolderId(String holderId) {
      this.holderId = holderId;
    }

    @Named("string_code")
    @Ref
    public StringKeyEntity getStringEntity() {
      return stringEntity;
    }

    public void setStringEntity(StringKeyEntity stringEntity) {
      this.stringEntity = stringEntity;
    }

    @Ref(value = "uuid_id", label = "uuid_name")
    public RefValue getUuidRef() {
      return uuidRef;
    }

    public void setUuidRef(RefValue uuidRef) {
      this.uuidRef = uuidRef;
    }
  }

  @Named("mixed_string_key_entity")
  @PrimaryKey("tenant_code")
  @PrimaryKey("generated_id")
  public static class MixedStringKeyEntity extends Model {
    @Named("tenant_code")
    private String tenantCode;

    private String name;

    public String getTenantCode() {
      return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
      this.tenantCode = tenantCode;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
