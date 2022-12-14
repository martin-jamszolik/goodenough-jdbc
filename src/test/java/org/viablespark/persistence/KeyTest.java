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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.viablespark.persistence.Key;
import org.viablespark.persistence.Pair;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;


/**
 *
 * @author mjamsz
 */
@TestInstance(Lifecycle.PER_CLASS)
public class KeyTest {

    Key key;
    
    @BeforeEach
    public void setupKey(){
        key = Key.of("primary", 123L);
        key.add("second", 345L);
        key.add("third", 567L);

    }

    @Test
    public void testConstructorWithMap(){
        var map = new LinkedHashMap<String,Long>();
        map.put("primary",123L);
        map.put("second",345L);
        Key key = new Key(map);
        assertEquals(123L,key.getPrimaryKey().value);
        assertEquals(123L,key.getKey("primary"));
    }

    @Test
    public void testOf() {
        assertEquals(key.getPrimaryKey(), Key.of("primary", 123L).getPrimaryKey());
    }

    @Test
    public void testAdd_String_Number() {
        key.add("Test",234L);
        key.add("Failed",null);
        key.add("ThirdDecimal",new BigDecimal("23345.23"));

        assertEquals( Long.valueOf("234"),key.getKey("Test"));
        assertEquals( Integer.MIN_VALUE,key.getKey("Failed"));
    }

    @Test
    public void testGetCount() {
        assertTrue("Should count as 3", key.getCount() ==3);
    }


    @Test
    public void testGetKeys() {
        Key key = Key.of("primary", 123L);
        key.add("second", 345L);
        key.add("third", 567L);
        key.add("primary", 234L);
        
        Collection<Pair<String, Long>> values = key.getKeys();
        assertNotNull( values );
        
    }

    @Test
    public void testGetAt() {
        Key key = Key.of("primary", 123L);
        key.add("second", 345L);
        key.add("third", 567L);
        key.add("primary", 234L);

        assertEquals(key.getPrimaryKey(), key.getAt(0));
        assertEquals("third", key.getAt(2).key);
        assertEquals( "second", key.getAt(1).key);
        assertEquals(3, key.getCount());

        assertNull(key.getAt(23));
    }

    @Test
    public void testGetKey() {
        assertEquals( Long.valueOf(567L),key.getKey("third"));

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> key.getKey("fake"),
            "Expected getKey() to throw, but it didn't"
        );

        assertTrue("Should be missing key",
            thrown.getMessage().contains("Key fake not found. Other keys?"));


    }


    @Test
    public void testContains() {
        assertTrue("Expect contains",key.contains("second").isPresent());
        assertEquals(Pair.of("second", 345L),key.contains("second").get());
        assertFalse(key.contains("fake").isPresent() );
    }

    @Test
    public void testEquals() {
        assertTrue("Should have the same hash",
            Key.of("same",2322L).hashCode() == Key.of("same",2322L).hashCode() );
        assertEquals(Key.of("IamKey",1L), Key.of("IamKey",1L));
        assertNotEquals(Key.of("IamKey",1L), Key.of("IamDifferent",2L));
        assertNotEquals(Key.of("IamKey",1L), Pair.of("IamPair",1L));
        assertNotEquals(Key.of("IamKey",1L),Key.of("IamDifferent",2L).add("second",23L) );
    }

    @Test
    public void testGetPrimaryKey() {
        assertEquals(Pair.of("primary", 123L),key.getPrimaryKey());
    }


}
