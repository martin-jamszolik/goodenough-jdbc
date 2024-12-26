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

import java.time.LocalDate;

@Named("note")
public class NotePersistable implements Persistable {
    private Key key = Key.None;

    private String note;
    private String additional;

    private LocalDate note_date;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getAdditional() {
        return additional;
    }

    public void setAdditional(String additional) {
        this.additional = additional;
    }

    public LocalDate getNote_date() {
        return note_date;
    }

    public void setNote_date(LocalDate note_date) {
        this.note_date = note_date;
    }

    public Key getRefs() {
        return key;
    }

    @Override
    public void setRefs(Key refs) {
        this.key = refs;
    }
}

