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

import java.time.LocalDate;
import java.util.Objects;
import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.Skip;

@PrimaryKey("n_key")
public class Note extends Model {

  @Named("note")
  private String noteContent;

  @Named("additional")
  private String extra;

  @Named("note_date")
  private LocalDate dateTaken;

  // @Named("note_created") private LocalDate customNoteDateCreated;

  @Ref
  @Named("progress_id")
  private Progress progress;

  @Skip private String noGood;

  public String getNoteContent() {
    return noteContent;
  }

  public void setNoteContent(String noteContent) {
    this.noteContent = noteContent;
  }

  public String getExtra() {
    return extra;
  }

  public void setExtra(String extra) {
    this.extra = extra;
  }

  public Progress getProgress() {
    return progress;
  }

  public void setProgress(Progress progress) {
    this.progress = progress;
  }

  public String getNoGood() {
    return noGood;
  }

  public void setNoGood(String noGood) {
    this.noGood = noGood;
  }

  public LocalDate getDateTaken() {
    return dateTaken;
  }

  public void setDateTaken(LocalDate dateTaken) {
    this.dateTaken = dateTaken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Note note = (Note) o;
    return Objects.equals(getNoteContent(), note.getNoteContent())
        && Objects.equals(getExtra(), note.getExtra())
        && Objects.equals(getDateTaken(), note.getDateTaken());
  }

  @Override
  public int hashCode() {
    int hash = Objects.hash(getNoteContent(), getDateTaken());

    if (this.getId() != null) {
      hash = 53 * hash + this.getId().intValue();
    }
    return hash;
  }
}
