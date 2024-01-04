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

import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.Ref;

public class PurchaseOrder extends Model {

    @Named("requester")
    private String requester;
    @Named("po_number_id")
    private Integer poNumberId;
    @Named("primitive_id")
    private int primitiveExampleId;

    @Named("long_id")
    private Long longId;

    @Named("some_fake_field")
    private int fakeField;

    @Ref
    private Note note;

    @Ref
    private Supplier supplier;

    @Ref("supplier_id")
    @Named("sup_name")
    private RefValue supplierRef; // valid case. Expect SQL data

    @Ref("supplier_id")
    private RefValue supplierRefInvalid; //Invalid case, skip

    @Named("supplier_id")
    private RefValue supplierRefInvalidAgain; //Invalid case, skip

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public Integer getPoNumberId() {
        return poNumberId;
    }

    public void setPoNumberId(Integer poNumberId) {
        this.poNumberId = poNumberId;
    }

    public int getFakeField() {
        return fakeField;
    }

    public int getPrimitiveExampleId() {
        return primitiveExampleId;
    }

    public void setPrimitiveExampleId(int primitiveExampleId) {
        this.primitiveExampleId = primitiveExampleId;
    }

    public Long getLongId() {
        return longId;
    }

    public void setLongId(Long longId) {
        this.longId = longId;
    }

    public void setFakeField(int fakeField) {
        this.fakeField = fakeField;
    }

    public RefValue getSupplierRef() {
        return supplierRef;
    }

    public void setSupplierRef(RefValue supplierRef) {
        this.supplierRef = supplierRef;
    }

    public RefValue getSupplierRefInvalid() {
        return supplierRefInvalid;
    }

    public void setSupplierRefInvalid(RefValue supplierRefInvalid) {
        this.supplierRefInvalid = supplierRefInvalid;
    }

    public RefValue getSupplierRefInvalidAgain() {
        return supplierRefInvalidAgain;
    }

    public void setSupplierRefInvalidAgain(RefValue supplierRefInvalidAgain) {
        this.supplierRefInvalidAgain = supplierRefInvalidAgain;
    }
}
