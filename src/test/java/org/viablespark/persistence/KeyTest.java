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

import java.util.Collection;

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
    public void testOf() {
        assertEquals(key.getPrimaryKey(), Key.of("primary", 123L).getPrimaryKey());
    }

    @Test
    public void testAdd_String_Number() {
        key.add("Test",234L);
        
        assertEquals( Long.valueOf("234"),key.getKey("Test"));
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
    }

    @Test
    public void testContains() {
        assertTrue("Expect contains",key.contains("second").isPresent());
        assertEquals(Pair.of("second", 345L),key.contains("second").get());
    }

    @Test
    public void testEquals() {
        assertEquals(Key.of("IamKey",1L), Key.of("IamKey",1L));
        assertNotEquals(Key.of("IamKey",1L), Key.of("IamDifferent",2L));
    }

    @Test
    public void testGetPrimaryKey() {
        assertEquals(Pair.of("primary", 123L),key.getPrimaryKey());
    }


}
