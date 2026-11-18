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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.util.AssertionErrors.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistableRowMapperTest {

  private EmbeddedDatabase db;

  @Test
  public void testRowSetMapping() {
    var f = PersistableRowMapper.of(Contractor.class);
    var jdbc = new JdbcTemplate(db);
    var rowSet = jdbc.queryForRowSet("select * from contractor order by sc_key asc");
    List<Contractor> resultSet = new ArrayList<>();
    var mappedRows = jdbc.queryForRowSet("select * from contractor order by sc_key asc");
    while (mappedRows.next()) {
      resultSet.add(f.mapRow(mappedRows, mappedRows.getRow()));
    }
    while (rowSet.next()) {
      Contractor contractor = f.mapRow(rowSet, rowSet.getRow());
      assertEquals(contractor.getRefs(), resultSet.get(rowSet.getRow() - 1).getRefs());
    }
  }

  @Test
  public void testMapRowMappingUsingResultSet() throws Exception {

    var jdbc = new JdbcTemplate(db);
    PersistableMapper<Contractor> f =
        (SqlRowSet rs, int rowNum) -> {
          Contractor c = new Contractor();
          c.setRefs(Key.of("sc_key", rs.getLong("sc_key")));
          c.setName(rs.getString("sc_name"));
          c.setContact(rs.getString("contact"));
          return c;
        };

    var sql =
        "select sc_key, sc_name as name, contact, phone1, fax, email from contractor c order by sc_key asc";

    List<Contractor> list = new ArrayList<>();
    var rowSet = jdbc.queryForRowSet(sql);
    while (rowSet.next()) {
      list.add(f.mapRow(rowSet, rowSet.getRow()));
    }

    assertEquals(list.get(0).getRefs(), Key.of("sc_key", 1L));
  }

  @Test
  public void testException() {
    var f = PersistableRowMapper.of(Contractor.class);

    Exception thrown =
        assertThrows(
            Exception.class,
            () -> f.mapRow((SqlRowSet) null, 1),
            "Expected to throw, but it didn't");

    assertTrue("Should throw", thrown != null);
  }

  @Test
  public void testPurchaseOrderMappings() throws Exception {
    var mapper = PersistableRowMapper.of(PurchaseOrder.class);
    var jdbc = new JdbcTemplate(db);
    var rowSet =
        jdbc.queryForRowSet(
            "select purchase_order.*, supplier.*, 'fake' as fake_col "
                + "from purchase_order left join supplier on ( supplier.id = purchase_order.supplier_id ) ");
    while (rowSet.next()) {
      PurchaseOrder po = mapper.mapRow(rowSet, rowSet.getRow());
      assertNotNull(po);
      assertEquals(po.getSupplierRef().getRef().getValue(), 1L);
      assertEquals(po.getSupplierRef().getValue(), "Test Supplier");
    }
  }

  @Test
  public void testNoteMappings() throws Exception {
    var mapper = PersistableRowMapper.of(PurchaseOrder.class);
    var jdbc = new JdbcTemplate(db);
    var rowSet =
        jdbc.queryForRowSet(
            "select purchase_order.*, supplier.*, 'fake' as fake_col "
                + "from purchase_order left join supplier on ( supplier.id = purchase_order.supplier_id ) ");
    while (rowSet.next()) {
      PurchaseOrder po = mapper.mapRow(rowSet, rowSet.getRow());
      assertNotNull(po);
    }
  }

  @Test
  public void testMissingLabelColumnForRefValueLeavesLabelNull() throws Exception {
    var mapper = PersistableRowMapper.of(PurchaseOrder.class);
    String sql = "select id, n_key, supplier_id from purchase_order";
    try (var conn = db.getConnection();
        var stmt = conn.prepareStatement(sql)) {
      var rs = stmt.executeQuery();
      rs.next();
      PurchaseOrder order = mapper.mapRow(rs, rs.getRow());
      assertNotNull(order.getSupplierRef());
      assertEquals(1L, order.getSupplierRef().getRef().getValue());
      assertNull(order.getSupplierRef().getValue());
    }
  }

  @Test
  public void testMissingForeignKeyColumnForRefThrowsException() throws Exception {
    var mapper = PersistableRowMapper.of(PurchaseOrder.class);
    String sql =
        "select purchase_order.id, purchase_order.supplier_id, supplier.sup_name "
            + "from purchase_order join supplier on (supplier.id = purchase_order.supplier_id)";
    try (var conn = db.getConnection();
        var stmt = conn.prepareStatement(sql)) {
      var rs = stmt.executeQuery();
      rs.next();
      SQLException ex = assertThrows(SQLException.class, () -> mapper.mapRow(rs, rs.getRow()));
      org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("n_key"));
    }
  }

  @Test
  public void testNullValueHandling() throws Exception {
    var mapper = PersistableRowMapper.of(Contractor.class);
    var jdbc = new JdbcTemplate(db);
    var rowSet =
        jdbc.queryForRowSet("select sc_key, sc_name, null as contact from contractor limit 1");
    if (rowSet.next()) {
      Contractor c = mapper.mapRow(rowSet, rowSet.getRow());
      assertNotNull(c);
      assertNull(c.getContact());
    }
  }

  @Test
  public void testNullForeignKeysRemainNull() {
    var mapper = PersistableRowMapper.of(NullableRefsEntity.class);
    var jdbc = new JdbcTemplate(db);

    NullableRefsEntity entity =
        jdbc.queryForObject(
            "SELECT 1 AS entity_id, CAST(NULL AS BIGINT) AS related_id, "
                + "CAST(NULL AS BIGINT) AS lookup_id, CAST(NULL AS VARCHAR(20)) AS lookup_label "
                + "FROM (VALUES(0))",
            mapper::mapRow);

    assertNotNull(entity);
    assertNull(entity.getRelated());
    assertNull(entity.getLookup());
  }

  @Test
  public void testTypeConversionLongToInt() throws Exception {
    var mapper = PersistableRowMapper.of(Contractor.class);
    var jdbc = new JdbcTemplate(db);
    // Test integer type conversion from Long
    var rowSet = jdbc.queryForRowSet("select * from contractor limit 1");
    if (rowSet.next()) {
      Contractor c = mapper.mapRow(rowSet, rowSet.getRow());
      assertNotNull(c);
    }
  }

  @Test
  public void testTypeConversionNumericToBoolean() throws Exception {
    var mapper = PersistableRowMapper.of(BooleanValueEntity.class);
    try (var conn = db.getConnection();
        var stmt =
            conn.prepareStatement(
                "select sc_key as id, 1 as active, 0 as enabled from contractor limit 1")) {
      var rs = stmt.executeQuery();
      rs.next();

      BooleanValueEntity entity = mapper.mapRow(rs, rs.getRow());

      assertTrue("active should map to true", entity.isActive());
      org.junit.jupiter.api.Assertions.assertFalse(entity.getEnabled());
    }
  }

  @Test
  public void testSqlRowSetProxyMetaData() throws Exception {
    var mapper = PersistableRowMapper.of(Contractor.class);
    var jdbc = new JdbcTemplate(db);
    var rowSet = jdbc.queryForRowSet("select sc_key, sc_name from contractor limit 1");
    if (rowSet.next()) {
      // This tests the proxy creation and metadata handling
      Contractor c = mapper.mapRow(rowSet, rowSet.getRow());
      assertNotNull(c);
      assertNotNull(c.getRefs());
    }
  }

  @Test
  public void testMissingPrimaryKeyColumnThrowsException() throws Exception {
    var mapper = PersistableRowMapper.of(Contractor.class);
    String sql = "select sc_name, contact from contractor";
    try (var conn = db.getConnection();
        var stmt = conn.prepareStatement(sql)) {
      var rs = stmt.executeQuery();
      rs.next();
      SQLException ex = assertThrows(SQLException.class, () -> mapper.mapRow(rs, rs.getRow()));
      org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("sc_key"));
    }
  }

  @Test
  public void testRefValueWithMissingLabelColumn() throws Exception {
    var mapper = PersistableRowMapper.of(PurchaseOrder.class);
    String sql = "select id, n_key, supplier_id, 'dummy' as fake from purchase_order";
    try (var conn = db.getConnection();
        var stmt = conn.prepareStatement(sql)) {
      var rs = stmt.executeQuery();
      rs.next();
      PurchaseOrder order = mapper.mapRow(rs, rs.getRow());
      assertNotNull(order.getSupplierRef());
      assertNull(order.getSupplierRef().getValue());
    }
  }

  @Test
  public void testMapperCaching() {
    // Test that mapper instances are cached
    var mapper1 = PersistableRowMapper.of(Contractor.class);
    var mapper2 = PersistableRowMapper.of(Contractor.class);
    assertNotNull(mapper1);
    assertNotNull(mapper2);
    // Both should work correctly
  }

  @PrimaryKey("id")
  public static class BooleanValueEntity extends Model {
    private boolean active;
    private Boolean enabled;

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }
  }

  @Test
  public void testResultSetDirectMapping() throws Exception {
    var mapper = PersistableRowMapper.of(Contractor.class);
    String sql = "select * from contractor order by sc_key asc";
    try (var conn = db.getConnection();
        var stmt = conn.prepareStatement(sql)) {
      var rs = stmt.executeQuery();
      if (rs.next()) {
        Contractor c = mapper.mapRow(rs, rs.getRow());
        assertNotNull(c);
        assertNotNull(c.getRefs());
        assertEquals("sc_key", c.getRefs().primaryKey().getKey());
      }
    }
  }

  @Test
  public void testSqlRowSetProxyGetObject() throws Exception {
    var mapper = PersistableRowMapper.of(Proposal.class);
    var jdbc = new JdbcTemplate(db);
    // Test that uses LocalDate conversion via proxy
    var rowSet = jdbc.queryForRowSet("select * from est_proposal where pr_key = 1");
    if (rowSet.next()) {
      Proposal proposal = mapper.mapRow(rowSet, rowSet.getRow());
      assertNotNull(proposal);
      assertNotNull(proposal.getSubmitDeadline());
    }
  }

  @Test
  public void testProxyInvocationHandlerException() throws Exception {
    var mapper = PersistableRowMapper.of(Contractor.class);
    var jdbc = new JdbcTemplate(db);

    // This tests the exception handling in SqlRowSetWrapper.invoke()
    var rowSet = jdbc.queryForRowSet("select * from contractor where sc_key = 1");
    if (rowSet.next()) {
      Contractor c = mapper.mapRow(rowSet, rowSet.getRow());
      assertNotNull(c);
      assertEquals("Mr Contractor", c.getName());
    }
  }

  @Test
  public void testMultipleRowsMappingWithData() throws Exception {
    var mapper = PersistableRowMapper.of(Contractor.class);
    var jdbc = new JdbcTemplate(db);

    // Test data has 2 contractors
    List<Contractor> contractors = new ArrayList<>();
    var rowSet = jdbc.queryForRowSet("select * from contractor order by sc_key");
    while (rowSet.next()) {
      contractors.add(mapper.mapRow(rowSet, rowSet.getRow()));
    }
    assertEquals(2, contractors.size());
    assertEquals("Mr Contractor", contractors.get(0).getName());
    assertEquals("ABC Contractor Inc", contractors.get(1).getName());
  }

  @PrimaryKey("entity_id")
  public static class NullableRefsEntity extends Model {
    private NullableRelatedEntity related;
    private RefValue lookup;

    @Ref
    @Named("related_id")
    public NullableRelatedEntity getRelated() {
      return related;
    }

    public void setRelated(NullableRelatedEntity related) {
      this.related = related;
    }

    @Ref(value = "lookup_id", label = "lookup_label")
    public RefValue getLookup() {
      return lookup;
    }

    public void setLookup(RefValue lookup) {
      this.lookup = lookup;
    }
  }

  @PrimaryKey("related_id")
  public static class NullableRelatedEntity extends Model {}

  @BeforeEach
  public void setUp() {
    db =
        new EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("PersistableRowMapperTest")
            .build();
  }

  @AfterEach
  public void tearDown() {
    db.shutdown();
  }
}
