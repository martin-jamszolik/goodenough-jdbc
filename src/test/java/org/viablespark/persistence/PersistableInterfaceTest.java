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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    // Should not throw, just return without setting
    assertDoesNotThrow(() -> entity.setId(100L));
    assertEquals(Key.None, entity.getRefs());
  }

  @Test
  public void testSetIdWithNullKey() {
    Contractor entity = new Contractor();
    entity.setRefs(null);

    // Should not throw, just return without setting
    assertDoesNotThrow(() -> entity.setId(100L));
    assertNull(entity.getRefs());
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
}
