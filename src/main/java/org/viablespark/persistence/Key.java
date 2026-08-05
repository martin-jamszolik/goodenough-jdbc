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

public class Key implements Serializable {

  public static final Key None = new Key(true);
  private final Map<String, Pair<String, Long>> keys = new LinkedHashMap<>();
  private final boolean immutable;

  public Key() {
    this(false);
  }

  private Key(boolean immutable) {
    this.immutable = immutable;
  }

  public Key(Pair<String, Long> primaryKey) {
    this(false);
    keys.put(primaryKey.getKey(), primaryKey);
  }

  public static Key of(String key, Long value) {
    return new Key(new Pair<>(key, value));
  }

  public Key(Map<String, ?> map) {
    this(false);
    for (Entry<String, ?> e : map.entrySet()) {
      keys.put(e.getKey(), Pair.of(e.getKey(), ((Number) e.getValue()).longValue()));
    }
  }

  public Key add(String name, Number key) {
    return add(name, key.longValue());
  }

  public Key addIfValid(String name, Long key) {
    if (key != null) {
      add(name, key);
    }
    return this;
  }

  public Key add(String name, Long key) {
    requireMutable();
    keys.put(name, Pair.of(name, key));
    return this;
  }

  public int count() {
    return keys.size();
  }

  public Collection<Pair<String, Long>> getKeys() {
    return Collections.unmodifiableCollection(keys.values());
  }

  public void setKeys(Collection<Pair<String, Long>> _keys) {
    requireMutable();
    _keys.forEach(pair -> keys.put(pair.getKey(), pair));
  }

  public Pair<String, Long> getAt(int index) {
    int counter = 0;
    for (Entry<String, Pair<String, Long>> entry : keys.entrySet()) {
      if (counter == index) {
        return entry.getValue();
      }
      counter++;
    }
    return null;
  }

  public Long getKey(String name) {
    Pair<String, Long> found = keys.get(name);
    if (found == null) {
      throw new RuntimeException("Key " + name + " not found. Other keys?" + this);
    }
    return found.getValue();
  }

  public Optional<Pair<String, Long>> contains(String strKey) {
    if (!keys.containsKey(strKey)) {
      return Optional.empty();
    }
    return Optional.of(keys.get(strKey));
  }

  @Override
  public boolean equals(Object other) {

    if (!(other instanceof Key)) {
      return false;
    }

    return keys.equals(((Key) other).keys);
  }

  @Override
  public int hashCode() {
    return keys.hashCode();
  }

  public Pair<String, Long> primaryKey() {
    return getAt(0);
  }

  private void requireMutable() {
    if (immutable) {
      throw new UnsupportedOperationException("Key.None is immutable");
    }
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder("Key(s) ");
    for (Pair<String, Long> key : keys.values()) {
      str.append(key.getKey()).append("=").append(key.getValue()).append(" ");
    }
    return str.toString();
  }
}
