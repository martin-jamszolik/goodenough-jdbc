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

import org.viablespark.persistence.dsl.WithSql;

public interface Persistable {
  Key getRefs();

  void setRefs(Key refs);

  default Long getId() {
    Object identifier = getIdentifier();
    if (identifier == null || identifier instanceof Long) {
      return (Long) identifier;
    }
    throw new IllegalStateException(
        "Entity identifier is "
            + identifier.getClass().getSimpleName()
            + "; use getIdentifier() for nonnumeric keys");
  }

  default void setId(Long value) {
    setIdentifier(value);
  }

  default Object getIdentifier() {
    if (getRefs() == null || getRefs().count() == 0) {
      return null;
    }
    return getRefs().primary().getValue();
  }

  default <T> T getIdentifier(Class<T> type) {
    Object identifier = getIdentifier();
    if (identifier == null) {
      return null;
    }
    if (!type.isInstance(identifier)) {
      throw new IllegalArgumentException(
          "Entity identifier is " + identifier.getClass().getName() + ", not " + type.getName());
    }
    return type.cast(identifier);
  }

  default void setIdentifier(Object value) {
    if (getRefs() == null || getRefs().count() == 0) {
      WithSql.getPrimaryKey(this.getClass()).ifPresent(pk -> setRefs(Key.of(pk, value)));
      return;
    }
    getRefs().setPrimaryValue(value);
  }

  default boolean isNew() {
    return (getRefs() == null || getRefs().count() == 0);
  }
}
