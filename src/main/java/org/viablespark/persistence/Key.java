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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

/** Ordered database key supporting integral, UUID, and String components. */
public class Key implements Serializable {

  private static final long serialVersionUID = 1L;
  public static final Key None = new Key(true);
  private final Map<String, Pair<String, Object>> keys = new LinkedHashMap<>();
  private final boolean immutable;

  public Key() {
    this(false);
  }

  private Key(boolean immutable) {
    this.immutable = immutable;
  }

  public Key(Pair<String, ?> primaryKey) {
    this(false);
    add(primaryKey.getKey(), primaryKey.getValue());
  }

  /** Retained numeric factory for source and binary compatibility. */
  public static Key of(String key, Long value) {
    return new Key(Pair.of(key, value));
  }

  /** Creates a UUID or String key, while also accepting integral numeric values. */
  public static Key of(String key, Object value) {
    return new Key(Pair.of(key, value));
  }

  public Key(Map<String, ?> map) {
    this(false);
    for (Entry<String, ?> entry : map.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
  }

  /** Retained numeric mutator for source and binary compatibility. */
  public Key add(String name, Number key) {
    return add(name, (Object) key);
  }

  public Key addIfValid(String name, Long key) {
    return addIfValid(name, (Object) key);
  }

  public Key addIfValid(String name, Object key) {
    if (key != null) {
      add(name, key);
    }
    return this;
  }

  /** Retained numeric mutator for source and binary compatibility. */
  public Key add(String name, Long key) {
    return add(name, (Object) key);
  }

  public Key add(String name, Object key) {
    requireMutable();
    keys.put(name, Pair.of(name, normalize(key)));
    return this;
  }

  public int count() {
    return keys.size();
  }

  /** Type-neutral key parts used by persistence internals and nonnumeric callers. */
  public Collection<Pair<String, Object>> parts() {
    return keys.values().stream().map(Key::copy).toList();
  }

  public void setParts(Collection<? extends Pair<String, ?>> parts) {
    requireMutable();
    parts.forEach(part -> add(part.getKey(), part.getValue()));
  }

  public Pair<String, Object> partAt(int index) {
    Pair<String, Object> part = internalPartAt(index);
    return part == null ? null : copy(part);
  }

  private Pair<String, Object> internalPartAt(int index) {
    int counter = 0;
    for (Entry<String, Pair<String, Object>> entry : keys.entrySet()) {
      if (counter == index) {
        return entry.getValue();
      }
      counter++;
    }
    return null;
  }

  public Object value(String name) {
    Pair<String, Object> found = keys.get(name);
    if (found == null) {
      throw new IllegalArgumentException("Key " + name + " not found. Other keys?" + this);
    }
    return found.getValue();
  }

  public <T> T value(String name, Class<T> type) {
    return castValue(value(name), type, name);
  }

  public Optional<Pair<String, Object>> findPart(String name) {
    return Optional.ofNullable(keys.get(name)).map(Key::copy);
  }

  public Pair<String, Object> primary() {
    return partAt(0);
  }

  public void setPrimaryValue(Object value) {
    requireMutable();
    Pair<String, Object> primary = internalPartAt(0);
    if (primary == null) {
      throw new IllegalStateException("Cannot set a value on an empty key");
    }
    primary.setValue(normalize(value));
  }

  /** Numeric compatibility view. Use {@link #parts()} for UUID or String keys. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Collection<Pair<String, Long>> getKeys() {
    return (Collection) Collections.unmodifiableCollection(keys.values());
  }

  /** Numeric compatibility mutator. Use {@link #setParts(Collection)} for other key types. */
  public void setKeys(Collection<Pair<String, Long>> parts) {
    setParts(parts);
  }

  /** Numeric compatibility accessor. Use {@link #partAt(int)} for UUID or String keys. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Pair<String, Long> getAt(int index) {
    Pair<String, Object> part = internalPartAt(index);
    if (part == null) {
      return null;
    }
    requireLong(part.getValue(), part.getKey());
    return (Pair) part;
  }

  /** Numeric compatibility accessor. Use {@link #value(String)} for UUID or String keys. */
  public Long getKey(String name) {
    return requireLong(value(name), name);
  }

  /** Numeric compatibility accessor. Use {@link #findPart(String)} for other key types. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Optional<Pair<String, Long>> contains(String name) {
    Optional<Pair<String, Object>> part = Optional.ofNullable(keys.get(name));
    part.ifPresent(value -> requireLong(value.getValue(), name));
    return (Optional) part;
  }

  /** Numeric compatibility accessor. Use {@link #primary()} for UUID or String keys. */
  public Pair<String, Long> primaryKey() {
    return getAt(0);
  }

  private static Object normalize(Object value) {
    if (value == null
        || value instanceof Long
        || value instanceof UUID
        || value instanceof String) {
      return value;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new IllegalArgumentException(
        "Key values must be integral numbers, UUID, or String; found "
            + value.getClass().getName());
  }

  private static Pair<String, Object> copy(Pair<String, Object> part) {
    return Pair.of(part.getKey(), part.getValue());
  }

  private static Long requireLong(Object value, String name) {
    if (value == null || value instanceof Long) {
      return (Long) value;
    }
    throw new IllegalStateException(
        "Key '"
            + name
            + "' contains "
            + value.getClass().getSimpleName()
            + "; use value(...) or primary() instead of the numeric compatibility API");
  }

  private static <T> T castValue(Object value, Class<T> type, String name) {
    if (value == null) {
      return null;
    }
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException(
          "Key '" + name + "' contains " + value.getClass().getName() + ", not " + type.getName());
    }
    return type.cast(value);
  }

  private void requireMutable() {
    if (immutable) {
      throw new UnsupportedOperationException("Key.None is immutable");
    }
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Key key && keys.equals(key.keys);
  }

  @Override
  public int hashCode() {
    return keys.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder string = new StringBuilder("Key(s) ");
    for (Pair<String, Object> key : keys.values()) {
      string.append(key.getKey()).append("=").append(key.getValue()).append(" ");
    }
    return string.toString();
  }
}
