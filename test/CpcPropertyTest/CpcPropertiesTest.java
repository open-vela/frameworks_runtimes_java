/*
 * Copyright (C) 2024 Xiaomi Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CpcPropertiesTest {
    private static final String KEY = "remote.testkey";
    private static final String UNSET_KEY = "A1B2C3D4E5F6";
    private static final String PERSIST_KEY = "persist.sys.testkey";
    private boolean PropChangeCalled = false;

    public void testProperties() throws Exception {
        String value;
        CpcProperties.set(KEY, "");
        value = CpcProperties.get(KEY, "default");
        assertEquals("default", value);

        // null default value is the same as "".
        CpcProperties.set(KEY, null);
        value = CpcProperties.get(KEY, "default");
        assertEquals("default", value);

        CpcProperties.set(KEY, "SA");
        value = CpcProperties.get(KEY, "default");
        assertEquals("SA", value);

        value = CpcProperties.get(KEY);
        assertEquals("SA", value);

        CpcProperties.set(KEY, "");
        value = CpcProperties.get(KEY, "default");
        assertEquals("default", value);

        // null value is the same as "".
        CpcProperties.set(KEY, "SA");
        CpcProperties.set(KEY, null);
        value = CpcProperties.get(KEY, "default");
        assertEquals("default", value);

        value = CpcProperties.get(KEY);
        assertEquals("", value);
    }

    private static void testInt(String setVal, int defValue, int expected) {
      CpcProperties.set(KEY, setVal);
      int value = CpcProperties.getInt(KEY, defValue);
      assertEquals(expected, value);
    }

    private static void testLong(String setVal, long defValue, long expected) {
      CpcProperties.set(KEY, setVal);
      long value = CpcProperties.getLong(KEY, defValue);
      assertEquals(expected, value);
    }

    public void testHandle() throws Exception {
        String value;
        CpcProperties.Handle handle = CpcProperties.find("doesnotexist_2341431");
        assertNull(handle);
        CpcProperties.set(KEY, "abc");
        handle = CpcProperties.find(KEY);
        assertNotNull(handle);
        value = handle.get();
        assertEquals("abc", value);
        CpcProperties.set(KEY, "blarg");
        value = handle.get();
        assertEquals("blarg", value);
        CpcProperties.set(KEY, "1");
        assertEquals(1, handle.getInt(-1));
        assertEquals(1, handle.getLong(-1));
        assertEquals(true, handle.getBoolean(false));
        CpcProperties.set(KEY, "");
        assertEquals(12345, handle.getInt(12345));
    }

    public void testIntegralProperties() throws Exception {
        testInt("", 123, 123);
        testInt("", 0, 0);
        testInt("", -123, -123);

        testInt("123", 124, 123);
        testInt("0", 124, 0);
        testInt("-123", 124, -123);

        testLong("", 3147483647L, 3147483647L);
        testLong("", 0, 0);
        testLong("", -3147483647L, -3147483647L);

        testLong("3147483647", 124, 3147483647L);
        testLong("0", 124, 0);
        testLong("-3147483647", 124, -3147483647L);
    }

    public void testUnset() throws Exception {
        assertEquals("abc", CpcProperties.get(UNSET_KEY, "abc"));
        assertEquals(true, CpcProperties.getBoolean(UNSET_KEY, true));
        assertEquals(false, CpcProperties.getBoolean(UNSET_KEY, false));
        assertEquals(5, CpcProperties.getInt(UNSET_KEY, 5));
        assertEquals(-10, CpcProperties.getLong(UNSET_KEY, -10));
    }

    public void testNullKey() throws Exception {
        try {
            CpcProperties.get(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException npe) {
        }

        try {
            CpcProperties.get(null, "default");
            fail("Expected NullPointerException");
        } catch (NullPointerException npe) {
        }

        try {
            CpcProperties.set(null, "value");
            fail("Expected NullPointerException");
        } catch (NullPointerException npe) {
        }

        try {
            CpcProperties.getInt(null, 0);
            fail("Expected NullPointerException");
        } catch (NullPointerException npe) {
        }

        try {
            CpcProperties.getLong(null, 0);
            fail("Expected NullPointerException");
        } catch (NullPointerException npe) {
        }
    }

    public void testDigestOf() {
        final String empty = CpcProperties.digestOf();
        final String finger = CpcProperties.digestOf("ro.build.fingerprint");
        final String fingerBrand = CpcProperties.digestOf(
                "ro.build.fingerprint", "ro.product.brand");
        final String brandFinger = CpcProperties.digestOf(
                "ro.product.brand", "ro.build.fingerprint");

        // Shouldn't change over time
        assertTrue(Objects.equals(finger, CpcProperties.digestOf("ro.build.fingerprint")));

        // Different properties means different results
        assertFalse(Objects.equals(empty, finger));
        assertFalse(Objects.equals(empty, fingerBrand));
        assertFalse(Objects.equals(finger, fingerBrand));

        // Same properties means same result
        assertTrue(Objects.equals(fingerBrand, brandFinger));
    }

    public void testCallbacks() {
        Runnable callback1 = new Runnable() {
            @Override
            public void run() {
                System.out.println("Callback1 called!");
            }
        };

        Runnable callback2 = new Runnable() {
            @Override
            public void run() {
                System.out.println("Callback2 called!");
                PropChangeCalled = true;
            }
        };

        CpcProperties.addPropChangeCallback("abc", callback1);
        CpcProperties.addPropChangeCallback("abc", callback2);

        while (!PropChangeCalled) {
            Thread.yield();
        }
        CpcProperties.removePropChangeCallback("abc", callback1);

        PropChangeCalled = false;
        while (!PropChangeCalled) {
            Thread.yield();
        }
        CpcProperties.removePropChangeCallback("abc", callback2);
    }

    public static void main(String[] args) {
        CpcPropertiesTest test = new CpcPropertiesTest();
        try {
            System.out.println("Test testProperties()\n");
            test.testProperties();
            System.out.println("Test testHandle()\n");
            test.testHandle();
            System.out.println("Test testIntegralProperties()\n");
            test.testIntegralProperties();
            System.out.println("Test testUnset()\n");
            test.testUnset();
            System.out.println("Test testNullKey()\n");
            test.testNullKey();
            System.out.println("Test testDigestOf()\n");
            test.testDigestOf();
            System.out.println("Test testCallbacks\n");
            test.testCallbacks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
