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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefValueTest {

    @Test
    void getValue() {
        var value = new RefValue("Test", Pair.of("t_key", 1L));
        assertEquals(value.getValue(), "Test");
    }

    @Test
    void setValue() {
        var value = new RefValue("SetTest", Pair.of("t_key", 1L));
        assertEquals(value.getValue(), "SetTest");
        value.setValue("anotherTest");
        assertEquals(value.getValue(), "anotherTest");
    }

    @Test
    void getRef() {
        var value = new RefValue("SetTest", Pair.of("t_key", 1L));
        assertEquals(value.getRef(), Pair.of("t_key", 1L));
    }

    @Test
    void setRef() {
        var value = new RefValue();
        value.setRef(Pair.of("t_key", 1L));
        assertEquals(value.getRef(), Pair.of("t_key", 1L));
    }

    @Test
    void testEqualsWithSameInstance() {
        var value = new RefValue("SetTest", Pair.of("t_key", 1L));
        assertTrue(value.equals(value));
    }

    @Test
    void testEqualsWithNull() {
        var value = new RefValue("SetTest", Pair.of("t_key", 1L));
        assertFalse(value.equals(null));
    }

    @Test
    void testEqualsWithDifferentClass() {
        var value = new RefValue("SetTest", Pair.of("t_key", 1L));
        assertFalse(value.equals("SomeStringObject"));
        assertFalse(value.equals(new Object()));
    }

    @Test
    void testEqualsWithSameValues() {
        var value1 = new RefValue("SetTest", Pair.of("t_key", 1L));
        var value2 = new RefValue("SetTest", Pair.of("t_key", 1L));
        assertTrue(value1.equals(value2));
    }

    @Test
    void testEqualsWithDifferentValues() {
        var value1 = new RefValue("SetTest", Pair.of("t_key", 1L));
        var value3 = new RefValue("SetTestValue3", Pair.of("t_key", 3L));
        assertFalse(value1.equals(value3));
    }

    @Test
    void testEqualsWithDifferentValueButSameRef() {
        var value1 = new RefValue("SetTest", Pair.of("t_key", 1L));
        var value4 = new RefValue("DifferentValue", Pair.of("t_key", 1L));
        assertFalse(value1.equals(value4));
    }

    @Test
    void testEqualsWithDifferentRefButSameValue() {
        var value1 = new RefValue("SetTest", Pair.of("t_key", 1L));
        var value5 = new RefValue("SetTest", Pair.of("different_key", 2L));
        assertFalse(value1.equals(value5));
    }

    @Test
    void testHashCodeWithSameValues() {
        var value1 = new RefValue("SetTest", Pair.of("t_key", 1L));
        var value2 = new RefValue("SetTest", Pair.of("t_key", 1L));
        assertEquals(value1.hashCode(), value2.hashCode());
    }

    @Test
    void testHashCodeWithDifferentValues() {
        var value1 = new RefValue("SetTest", Pair.of("t_key", 1L));
        var value3 = new RefValue("SetTestValue3", Pair.of("t_key", 3L));
        assertNotEquals(value1.hashCode(), value3.hashCode());
    }
}