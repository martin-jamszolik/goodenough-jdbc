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

package org.viablespark.persistence.dsl;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.viablespark.persistence.*;

/**
 * @author mjamsz
 */
public class WithSqlTest {

  public WithSqlTest() {
    new WithSql();
  }

  @Test
  public void testGetSQLSelectClause() {
    String select = WithSql.getSelectClause(Proposal.class);
    assertEquals(
        "sc_key,dist as \"distance\",prop_date,prop_id,proposal_name as \"prop_name\",submit_deadline",
        select);
    assertFalse(select.contains("tasks"));

    String notesSelect = WithSql.getSelectClause(Note.class, "n_key");
    assertEquals(
        "n_key,note_date as \"date_taken\",additional as \"extra\",note as \"note_content\",progress_id",
        notesSelect);

    String poSelect = WithSql.getSelectClause(PurchaseOrder.class);
    assertEquals(
        "some_fake_field as \"fake_field\",long_id as \"long_id\",n_key,po_number_id as \"po_number_id\",primitive_id as \"primitive_example_id\",requester as \"requester\",supplier_id",
        poSelect);
  }

  @Test
  public void testGetUpdateClause() throws Exception {
    Proposal e = new Proposal();
    e.setRefs(Key.of("pr_key", 233L));
    e.setDistance(344);
    e.setPropDate(new Date());
    e.setPropId("propId");
    e.setSubmitDeadline(LocalDate.now());
    e.setPropName("TestingName");
    e.setContractor(new Contractor("sc_key", 1L));

    SqlClause update = WithSql.getUpdateClause(e);
    assertNotNull(update);

    assertEquals(
        "SET sc_key=?,dist=?,prop_date=?,prop_id=?,proposal_name=?,submit_deadline=? WHERE pr_key=?",
        update.clause());

    Exception thrown =
        assertThrows(
            Exception.class, () -> WithSql.getUpdateClause(null), "Should throw Exception");

    assertTrue("Should throw", thrown != null);
  }

  @Test
  public void testGetUpdateClauseForPurchaseOrder() throws Exception {
    PurchaseOrder order = new PurchaseOrder();
    order.setRefs(Key.of("id", 233L));
    order.setNote(new Note());
    SqlClause update = WithSql.getUpdateClause(order);
    assertNotNull(update);

    assertEquals(
        "SET some_fake_field=?,long_id=?,n_key=?,po_number_id=?,primitive_id=?,requester=?,supplier_id=? WHERE id=?",
        update.clause());
  }

  @Test
  public void testGetInsertClauseForPurchaseOrder() throws Exception {
    PurchaseOrder order = new PurchaseOrder();
    order.setRefs(Key.of("id", 233L));
    order.setRequester("Requester Name");

    SqlClause insert = WithSql.getInsertClause(order);
    assertNotNull(insert);

    assertEquals(
        "(some_fake_field,long_id,n_key,po_number_id,primitive_id,requester,supplier_id) VALUES (?,?,?,?,?,?,?)",
        insert.clause());
  }

  @Test
  public void testGetInsertClause() throws Exception {
    Proposal e = new Proposal();
    e.setRefs(Key.of("pri_key", 233L));
    e.setDistance(344);
    e.setPropDate(new Date());
    e.setPropId("propId");
    e.setSubmitDeadline(LocalDate.now());
    e.setPropName("TestingName");
    e.setContractor(new Contractor("sc_key", 1L));

    SqlClause insert = WithSql.getInsertClause(e);
    assertNotNull(insert);

    assertEquals(
        "(sc_key,dist,prop_date,prop_id,proposal_name,submit_deadline) VALUES (?,?,?,?,?,?)",
        insert.clause());

    Exception thrown =
        assertThrows(
            Exception.class, () -> WithSql.getInsertClause(null), "Should throw Exception");

    assertTrue("Should throw", thrown != null);
  }

  @Test
  public void testCollectionGetterIgnoredByConvention() throws Exception {
    var entity = new CollectionHolder();
    entity.setName("Grouped");
    entity.setRefs(Key.of("id", 10L));
    entity.setChildren(List.of(new ProposalTask()));

    assertEquals("name as \"name\"", WithSql.getSelectClause(CollectionHolder.class));
    assertEquals("(name) VALUES (?)", WithSql.getInsertClause(entity).clause());
    assertEquals("SET name=? WHERE id=?", WithSql.getUpdateClause(entity).clause());
  }

  @Test
  void rejectsEntitiesWithoutMappedPropertiesOrUpdateIdentity() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> WithSql.getSelectClause(EmptyEntity.class));
    assertThrows(java.sql.SQLException.class, () -> WithSql.getInsertClause(new EmptyEntity()));
    assertThrows(java.sql.SQLException.class, () -> WithSql.getUpdateClause(new EmptyEntity()));

    UnkeyedValueEntity valueEntity = new UnkeyedValueEntity();
    valueEntity.setName("value");
    assertEquals(Key.None, WithSql.getEntityKey(valueEntity));
    assertThrows(java.sql.SQLException.class, () -> WithSql.getUpdateClause(valueEntity));
  }

  @Test
  void supportsPropertyPrimaryKeysAndChildFieldAnnotationOverrides() throws Exception {
    PropertyKeyEntity entity = new PropertyKeyEntity();
    entity.setId(9L);
    entity.setName("property key");

    assertEquals(List.of("account_id"), WithSql.getPrimaryKeys(PropertyKeyEntity.class));
    assertEquals("account_id as \"id\",name", WithSql.getSelectClause(PropertyKeyEntity.class));
    assertEquals("(account_id,name) VALUES (?,?)", WithSql.getInsertClause(entity).clause());
    assertEquals(Key.of("account_id", 9L), WithSql.getEntityKey(entity));
    assertEquals("child_name as \"name\"", WithSql.getSelectClause(ChildOverride.class));
    assertEquals("url", WithSql.getSelectClause(AcronymEntity.class));
  }

  static class EmptyEntity extends Model {}

  static class UnkeyedValueEntity extends Model {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  static class PropertyKeyEntity extends Model {
    private Long id;
    private String name;

    @Override
    @PrimaryKey("account_id")
    public Long getId() {
      return id;
    }

    @Override
    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  static class ParentOverride extends Model {
    @Named("parent_name")
    public String getName() {
      return null;
    }

    public void setName(String name) {}
  }

  static class ChildOverride extends ParentOverride {
    @Named("child_name")
    private String name;

    @Override
    public String getName() {
      return name;
    }

    @Override
    public void setName(String name) {
      this.name = name;
    }
  }

  static class AcronymEntity extends Model {
    private String url;

    public String getURL() {
      return url;
    }

    public void setURL(String url) {
      this.url = url;
    }
  }

  @PrimaryKey("id")
  static class CollectionHolder extends Model {
    private String name;
    private List<ProposalTask> children = new ArrayList<>();

    @Named("name")
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<ProposalTask> getChildren() {
      return children;
    }

    public void setChildren(List<ProposalTask> children) {
      this.children = children;
    }
  }
}
