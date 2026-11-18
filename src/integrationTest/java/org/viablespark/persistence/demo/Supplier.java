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

package org.viablespark.persistence.demo;

import org.viablespark.persistence.Model;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;

/**
 * Supplier in the {@code demo} database ({@code supplier}).
 *
 * <p>Derived per {@code docs/from-schema.md}: single auto-increment PK ({@code sup_key}, KEY_SEQ 1
 * of 1), one single-column FK ({@code scat_key} to {@code supplier_category}), mapped as
 * {@code @Ref}. {@code sl_pr_key} carries no FK constraint in the schema, so it stays a plain
 * property rather than a reference.
 */
@PrimaryKey("sup_key")
public class Supplier extends Model {

  private String supName;
  private String supAddress;
  private String supCity;
  private String supState;
  private Integer supZip;
  private String supContact;
  private String supPhone1;
  private String supPhone2;
  private String notes;

  @Ref private SupplierCategory category;

  private Integer slPrKey;

  public String getSupName() {
    return supName;
  }

  public void setSupName(String supName) {
    this.supName = supName;
  }

  public String getSupAddress() {
    return supAddress;
  }

  public void setSupAddress(String supAddress) {
    this.supAddress = supAddress;
  }

  public String getSupCity() {
    return supCity;
  }

  public void setSupCity(String supCity) {
    this.supCity = supCity;
  }

  public String getSupState() {
    return supState;
  }

  public void setSupState(String supState) {
    this.supState = supState;
  }

  public Integer getSupZip() {
    return supZip;
  }

  public void setSupZip(Integer supZip) {
    this.supZip = supZip;
  }

  public String getSupContact() {
    return supContact;
  }

  public void setSupContact(String supContact) {
    this.supContact = supContact;
  }

  public String getSupPhone1() {
    return supPhone1;
  }

  public void setSupPhone1(String supPhone1) {
    this.supPhone1 = supPhone1;
  }

  public String getSupPhone2() {
    return supPhone2;
  }

  public void setSupPhone2(String supPhone2) {
    this.supPhone2 = supPhone2;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public SupplierCategory getCategory() {
    return category;
  }

  public void setCategory(SupplierCategory category) {
    this.category = category;
  }

  public Integer getSlPrKey() {
    return slPrKey;
  }

  public void setSlPrKey(Integer slPrKey) {
    this.slPrKey = slPrKey;
  }
}
