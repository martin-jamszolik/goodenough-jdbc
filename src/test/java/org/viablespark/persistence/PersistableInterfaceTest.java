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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for Persistable interface default methods */
public class PersistableInterfaceTest {

  @Test
  public void testGetIdWithValidKey() {
    Contractor entity = new Contractor();
    entity.setRefs(Key.of("sc_key", 123L));

    assertEquals(123L, entity.getId());
  }

  @Test
  public void testGetIdWithKeyNone() {
    Contractor entity = new Contractor();
    entity.setRefs(Key.None);

    assertNull(entity.getId());
  }

  @Test
  public void testGetIdWithEmptyKeyInstance() {
    Contractor entity = new Contractor();
    entity.setRefs(new Key());

    assertNull(entity.getId());
  }

  @Test
  public void testSetIdWithValidKey() {
    Contractor entity = new Contractor();
    entity.setRefs(Key.of("sc_key", 100L));

    entity.setId(200L);

    assertEquals(200L, entity.getId());
    assertEquals(200L, entity.getRefs().primaryKey().getValue());
  }

  @Test
  public void testSetIdWithKeyNone() {
    Contractor entity = new Contractor();
    entity.setRefs(Key.None);

    // Should deduce key from annotation
    entity.setId(100L);
    assertEquals(100L, entity.getId());
    assertEquals("sc_key", entity.getRefs().primaryKey().getKey());
  }

  @Test
  public void testSetIdWithNullKey() {
    Contractor entity = new Contractor();
    entity.setRefs(null);

    // Should deduce key from annotation
    entity.setId(100L);
    assertEquals(100L, entity.getId());
    assertEquals("sc_key", entity.getRefs().primaryKey().getKey());
  }

  @Test
  public void testSetIdWithEmptyKeyInstance() {
    Contractor entity = new Contractor();
    entity.setRefs(new Key());

    // Should deduce key from annotation
    entity.setId(100L);
    assertEquals(100L, entity.getId());
    assertEquals("sc_key", entity.getRefs().primaryKey().getKey());
  }

  static class NoAnnotationEntity implements Persistable {
    private Key refs = Key.None;

    @Override
    public Key getRefs() {
      return refs;
    }

    @Override
    public void setRefs(Key refs) {
      this.refs = refs;
    }
  }

  @Test
  public void testSetIdWithoutAnnotation() {
    NoAnnotationEntity entity = new NoAnnotationEntity();
    entity.setRefs(Key.None);

    entity.setId(100L);
    assertEquals(Key.None, entity.getRefs());
  }

  @Test
  public void testJacksonHydrationOrder_RefsThenId() {
    // Scenario: JSON has "refs": {...}, "id": 123
    // Jackson calls setRefs(...) then setId(123)
    Contractor entity = new Contractor();
    Key originalKey = Key.of("sc_key", 999L);
    entity.setRefs(originalKey);

    entity.setId(123L);

    assertEquals(123L, entity.getId());
    assertEquals(123L, entity.getRefs().primaryKey().getValue());
    // Ensure it's the same key object, just mutated
    assertEquals(originalKey, entity.getRefs());
  }

  @Test
  public void testJacksonHydrationOrder_IdThenRefs() {
    // Scenario: JSON has "id": 123, "refs": {...}
    // Jackson calls setId(123) then setRefs(...)
    Contractor entity = new Contractor();

    // 1. setId triggers the new "deduction" logic
    entity.setId(123L);
    assertEquals(123L, entity.getId());

    // 2. setRefs overwrites it
    Key finalKey = Key.of("sc_key", 456L);
    entity.setRefs(finalKey);

    assertEquals(456L, entity.getId());
    assertEquals(finalKey, entity.getRefs());
  }

  @Test
  public void testIsNewWithKeyNone() {
    Contractor entity = new Contractor();
    entity.setRefs(Key.None);

    assertTrue(entity.isNew());
  }

  @Test
  public void testIsNewWithNullKey() {
    Contractor entity = new Contractor();
    entity.setRefs(null);

    assertTrue(entity.isNew());
  }

  @Test
  public void testIsNewWithValidKey() {
    Contractor entity = new Contractor();
    entity.setRefs(Key.of("sc_key", 1L));

    assertFalse(entity.isNew());
  }

  @Test
  public void testIsNewWithEmptyKeyInstance() {
    Contractor entity = new Contractor();
    entity.setRefs(new Key());

    assertTrue(entity.isNew());
  }
}
