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

import java.io.Serializable;
import java.util.Objects;

public class RefValue implements Serializable {
  private static final long serialVersionUID = 1L;
  private String value;
  private Pair<String, Object> ref;

  public RefValue() {}

  public RefValue(String value, Pair<String, ?> ref) {
    this.value = value;
    setReference(ref);
  }

  public static RefValue of(String value, String column, Object referenceValue) {
    return new RefValue(value, Pair.of(column, referenceValue));
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Pair<String, Long> getRef() {
    if (ref == null) {
      return null;
    }
    return (Pair) ref;
  }

  public void setRef(Pair<String, Long> ref) {
    setReference(ref);
  }

  public Pair<String, Object> reference() {
    return ref == null ? null : Pair.of(ref.getKey(), ref.getValue());
  }

  public void setReference(Pair<String, ?> ref) {
    if (ref == null) {
      this.ref = null;
      return;
    }
    Key normalized = Key.of(ref.getKey(), ref.getValue());
    this.ref = Pair.of(ref.getKey(), normalized.value(ref.getKey()));
  }

  public Object referenceValue() {
    return ref == null ? null : ref.getValue();
  }

  public <T> T referenceValue(Class<T> type) {
    if (ref == null) {
      return null;
    }
    return Key.of(ref.getKey(), ref.getValue()).value(ref.getKey(), type);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RefValue refValue = (RefValue) o;
    if (!Objects.equals(value, refValue.value)) {
      return false;
    }
    return Objects.equals(ref, refValue.ref);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, ref);
  }
}
