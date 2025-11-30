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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.Ref;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistableRowMapperLabelTest {

  private EmbeddedDatabase db;

  @BeforeEach
  public void setUp() {
    db = new EmbeddedDatabaseBuilder().addDefaultScripts().build();
  }

  @AfterEach
  public void tearDown() {
    db.shutdown();
  }

  public static class PurchaseOrderWithLabel extends Model {

    @Named("requester")
    private String requester;

    @Named("po_number_id")
    private Integer poNumberId;

    @Ref(value = "supplier_id", label = "supplier.sup_name")
    private RefValue supplierRef;

    public String getRequester() {
      return requester;
    }

    public void setRequester(String requester) {
      this.requester = requester;
    }

    public Integer getPoNumberId() {
      return poNumberId;
    }

    public void setPoNumberId(Integer poNumberId) {
      this.poNumberId = poNumberId;
    }

    public RefValue getSupplierRef() {
      return supplierRef;
    }

    public void setSupplierRef(RefValue supplierRef) {
      this.supplierRef = supplierRef;
    }
  }

  @Test
  public void testPurchaseOrderLabelMapping() {
    var mapper = PersistableRowMapper.of(PurchaseOrderWithLabel.class);
    var jdbc = new JdbcTemplate(db);

    var sql =
        """
            SELECT purchase_order.*,
            supplier.id,
            supplier.sup_name as "supplier.sup_name"
            FROM purchase_order
            LEFT JOIN supplier ON (supplier.id = purchase_order.supplier_id)
            WHERE purchase_order.id = 1
        """;

    var po = jdbc.queryForObject(sql, mapper);

    assertNotNull(po);
    assertEquals("Mr. Requester", po.getRequester());
    assertEquals("Test Supplier", po.getSupplierRef().getValue());
    assertEquals(1L, po.getSupplierRef().getRef().getValue());
  }
}
