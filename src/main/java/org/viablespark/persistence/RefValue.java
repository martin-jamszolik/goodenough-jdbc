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

import java.util.Objects;

public class RefValue {
  private String value;
  private Pair<String, Long> ref;

  public RefValue() {}

  public RefValue(String value, Pair<String, Long> ref) {
    this.value = value;
    this.ref = ref;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Pair<String, Long> getRef() {
    return ref;
  }

  public void setRef(Pair<String, Long> ref) {
    this.ref = ref;
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
