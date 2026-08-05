package org.viablespark.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
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

class ReferenceCrudTest {
  private EmbeddedDatabase database;
  private JdbcTemplate jdbc;
  private ReferencePurchaseOrderRepository repository;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder().addDefaultScripts().setName("ReferenceCrudTest").build();
    jdbc = new JdbcTemplate(database);
    repository = new ReferencePurchaseOrderRepository(jdbc);
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  void generatedCrudPersistsRefValueAndClearsNullableReferences() {
    ReferencePurchaseOrder order = new ReferencePurchaseOrder();
    order.setRequester("Generated reference CRUD");
    order.setSupplier(new RefValue("not persisted", Pair.of("supplier_id", 1L)));
    Note note = new Note();
    note.setRefs(Key.of("n_key", 1L));
    order.setNote(note);

    Key key = repository.save(order).orElseThrow();
    assertEquals(1L, column(key, "supplier_id"));
    assertEquals(1L, column(key, "n_key"));

    ReferencePurchaseOrder generated =
        repository.get(key, ReferencePurchaseOrder.class).orElseThrow();
    assertEquals(Pair.of("supplier_id", 1L), generated.getSupplier().getRef());
    assertNull(generated.getSupplier().getValue());
    assertEquals(1L, generated.getNote().getId());

    List<ReferencePurchaseOrder> joined =
        repository.query(
            SqlQuery.statement(
                "SELECT purchase_order.*, supplier.sup_name "
                    + "FROM purchase_order JOIN supplier "
                    + "ON supplier.id = purchase_order.supplier_id WHERE purchase_order.id = ?",
                key.primaryKey().getValue()),
            PersistableRowMapper.of(ReferencePurchaseOrder.class));
    assertEquals("Test Supplier", joined.get(0).getSupplier().getValue());

    generated.setSupplier(null);
    generated.setNote(null);
    repository.save(generated);

    ReferencePurchaseOrder cleared =
        repository.get(key, ReferencePurchaseOrder.class).orElseThrow();
    assertNull(cleared.getSupplier());
    assertNull(cleared.getNote());
    assertNull(column(key, "supplier_id"));
    assertNull(column(key, "n_key"));
  }

  private Long column(Key key, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM purchase_order WHERE id = ?",
        Long.class,
        key.primaryKey().getValue());
  }

  @Named("purchase_order")
  @PrimaryKey("id")
  public static class ReferencePurchaseOrder extends Model {
    private String requester;
    private Note note;
    private RefValue supplier;

    public String getRequester() {
      return requester;
    }

    public void setRequester(String requester) {
      this.requester = requester;
    }

    @Named("n_key")
    @Ref
    public Note getNote() {
      return note;
    }

    public void setNote(Note note) {
      this.note = note;
    }

    @Ref(value = "supplier_id", label = "sup_name")
    public RefValue getSupplier() {
      return supplier;
    }

    public void setSupplier(RefValue supplier) {
      this.supplier = supplier;
    }
  }

  private static class ReferencePurchaseOrderRepository
      extends BaseRepository<ReferencePurchaseOrder> {
    private ReferencePurchaseOrderRepository(JdbcTemplate jdbc) {
      super(jdbc);
    }
  }
}
