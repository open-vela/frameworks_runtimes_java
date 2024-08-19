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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.util.Log;
import android.util.MutableInt;

import libcore.util.HexEncoding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class CpcProperties {
    private static final String TAG = "CpcProperties";
    private static final boolean TRACK_KEY_ACCESS = false;

    public static final int PROP_NAME_MAX = Integer.MAX_VALUE;

    /** @hide */
    public static final int PROP_VALUE_MAX = 91;

    static {
        System.loadLibrary("cpc_extension_jni.xiaomi");
    }

    private static final HashMap<String, ArrayList<Runnable>> sPropChangeCallbacks = new HashMap<>();

    private static final HashMap<String, MutableInt> sRoReads =
            TRACK_KEY_ACCESS ? new HashMap<>() : null;

    private static void onKeyAccess(String key) {
        if (!TRACK_KEY_ACCESS) return;

        if (key != null && key.startsWith("ro.")) {
            synchronized (sRoReads) {
                MutableInt numReads = sRoReads.getOrDefault(key, null);
                if (numReads == null) {
                    numReads = new MutableInt(0);
                    sRoReads.put(key, numReads);
                }
                numReads.value++;
                if (numReads.value > 3) {
                    Log.d(TAG, "Repeated read (count=" + numReads.value
                            + ") of a read-only system property '" + key + "'",
                            new Exception());
                }
            }
        }
    }

    // The one-argument version of native_get used to be a regular native function. Nowadays,
    // we use the two-argument form of native_get all the time, but we can't just delete the
    // one-argument overload: apps use it via reflection, as the UnsupportedAppUsage annotation
    // indicates. Let's just live with having a Java function with a very unusual name.
    private static String native_get(String key) {
        return native_get(key, "");
    }

    private static native String native_get(String key, String def);
    private static native int native_get_int(String key, int def);
    private static native long native_get_long(String key, long def);
    private static native boolean native_get_boolean(String key, boolean def);

    // _NOT_ FastNative: native_set performs IPC and can block
    private static native void native_set(String key, String def);

    private static native void native_add_prop_change_callback();
    private static native void native_add_prop_change_monitor(String key);
    private static native void native_remove_prop_change_monitor(String key);

    /**
     * Get the String value for the given {@code key}.
     *
     * @param key the key to lookup
     * @return an empty string if the {@code key} isn't found
     * @hide
     */
    @NonNull
    @SystemApi
    public static String get(@NonNull String key) {
        if (TRACK_KEY_ACCESS) onKeyAccess(key);
        return native_get(key);
    }

    /**
     * Get the String value for the given {@code key}.
     *
     * @param key the key to lookup
     * @param def the default value in case the property is not set or empty
     * @return if the {@code key} isn't found, return {@code def} if it isn't null, or an empty
     * string otherwise
     * @hide
     */
    @NonNull
    @SystemApi
    public static String get(@NonNull String key, @Nullable String def) {
        if (TRACK_KEY_ACCESS) onKeyAccess(key);
        return native_get(key, def);
    }

    /**
     * Get the value for the given {@code key}, and return as an integer.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as an integer, or def if the key isn't found or
     *         cannot be parsed
     * @hide
     */
    @SystemApi
    public static int getInt(@NonNull String key, int def) {
        if (TRACK_KEY_ACCESS) onKeyAccess(key);
        return native_get_int(key, def);
    }

    /**
     * Get the value for the given {@code key}, and return as a long.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a long, or def if the key isn't found or
     *         cannot be parsed
     * @hide
     */
    @SystemApi
    public static long getLong(@NonNull String key, long def) {
        if (TRACK_KEY_ACCESS) onKeyAccess(key);
        return native_get_long(key, def);
    }

    /**
     * Get the value for the given {@code key}, returned as a boolean.
     * Values 'n', 'no', '0', 'false' or 'off' are considered false.
     * Values 'y', 'yes', '1', 'true' or 'on' are considered true.
     * (case sensitive).
     * If the key does not exist, or has any other value, then the default
     * result is returned.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a boolean, or def if the key isn't found or is
     *         not able to be parsed as a boolean.
     * @hide
     */
    @SystemApi
    public static boolean getBoolean(@NonNull String key, boolean def) {
        if (TRACK_KEY_ACCESS) onKeyAccess(key);
        return native_get_boolean(key, def);
    }

    /**
     * Set the value for the given {@code key} to {@code val}.
     *
     * @throws IllegalArgumentException for non read-only properties if the {@code val} exceeds
     * 91 characters
     * @throws RuntimeException if the property cannot be set, for example, if it was blocked by
     * SELinux. libc will log the underlying reason.
     * @hide
     */
    @SystemApi
    public static void set(@NonNull String key, @Nullable String val) {
        if (val != null && !key.startsWith("ro.") && val.getBytes(StandardCharsets.UTF_8).length
                > PROP_VALUE_MAX) {
            throw new IllegalArgumentException("value of system property '" + key
                    + "' is longer than " + PROP_VALUE_MAX + " bytes: " + val);
        }
        if (TRACK_KEY_ACCESS) onKeyAccess(key);
        native_set(key, val);
    }

    /**
     * Add a callback that will be run whenever specified key property changes.
     *
     * @param key the key to monitor
     * @param callback The {@link Runnable} that should be executed when a system property
     * changes.
     * @hide
     */
    @SystemApi
    public static void addPropChangeCallback(@NonNull String key, @NonNull Runnable callback) {
        synchronized (sPropChangeCallbacks) {
            if (sPropChangeCallbacks.size() == 0) {
                native_add_prop_change_callback();
            }

            if (!sPropChangeCallbacks.containsKey(key)) {
                ArrayList<Runnable> callbacks = new ArrayList<Runnable>();
                callbacks.add(callback);
                sPropChangeCallbacks.put(key, callbacks);
                native_add_prop_change_monitor(key);
            } else {
                sPropChangeCallbacks.get(key).add(callback);
            }
        }
    }

    /**
     * Remove the target key callback.
     *
     * @param key the key to monitor
     * @hide
     */
    @SystemApi
    public static void removePropChangeCallback(@NonNull String key, @NonNull Runnable callback) {
        synchronized (sPropChangeCallbacks) {
            if (sPropChangeCallbacks.containsKey(key)) {
                sPropChangeCallbacks.get(key).remove(callback);
                if (sPropChangeCallbacks.get(key).size() == 0) {
                    sPropChangeCallbacks.remove(key);
                    native_remove_prop_change_monitor(key);
                }
            }
        }
    }

    private static void callPropChangeCallback(@NonNull String key) {
        ArrayList<Runnable> callbacks = null;
        synchronized (sPropChangeCallbacks) {
            //Log.i("foo", "Calling " + sChangeCallbacks.size() + " change callbacks!");
            if (sPropChangeCallbacks.size() == 0) {
                return;
            }
            callbacks = sPropChangeCallbacks.get(key);
        }

        if (callbacks == null) {
            return;
        }

        final long token = Binder.clearCallingIdentity();
        try {
            for (int i = 0; i < callbacks.size(); i++) {
                try {
                    callbacks.get(i).run();
                } catch (Throwable t) {
                    // Ignore and try to go on. Don't use wtf here: that
                    // will cause the process to exit on some builds and break tests.
                    Log.e(TAG, "Exception in CpcProperties change callback", t);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Add a callback that will be run whenever any system property changes.
     *
     * @param callback The {@link Runnable} that should be executed when a system property
     * changes.
     * @hide
     */
    @SystemApi
    public static void addChangeCallback(@NonNull Runnable callback) {
        addPropChangeCallback("*", callback);
    }

    /**
     * Remove the target callback.
     *
     * @param callback The {@link Runnable} that should be removed.
     * @hide
     */
    @SystemApi
    public static void removeChangeCallback(@NonNull Runnable callback) {
        removePropChangeCallback("*", callback);
    }

    /**
     * Return a {@code SHA-1} digest of the given keys and their values as a
     * hex-encoded string. The ordering of the incoming keys doesn't change the
     * digest result.
     *
     * @hide
     */
    public static @NonNull String digestOf(@NonNull String... keys) {
        Arrays.sort(keys);
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (String key : keys) {
                final String item = key + "=" + get(key) + "\n";
                digest.update(item.getBytes(StandardCharsets.UTF_8));
            }
            return HexEncoding.encodeToString(digest.digest()).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private CpcProperties() {
    }

    /**
     * Look up a property location by name.
     * @name name of the property
     * @return property handle or {@code null} if property isn't set
     * @hide
     */
    @Nullable public static Handle find(@NonNull String name) {
        String handle = get(name, null);
        if (handle.equals("")) {
            return null;
        }

        return new Handle(name);
    }

    /**
     * Handle to a pre-located property. Looking up a property handle in advance allows
     * for optimal repeated lookup of a single property.
     * @hide
     */
    public static final class Handle {

        private final String mNativeHandle;

        /**
         * @return Value of the property
         */
        @NonNull public String get() {
            return native_get(mNativeHandle);
        }
        /**
         * @param def default value
         * @return value or {@code def} on parse error
         */
        public int getInt(int def) {
            return native_get_int(mNativeHandle, def);
        }
        /**
         * @param def default value
         * @return value or {@code def} on parse error
         */
        public long getLong(long def) {
            return native_get_long(mNativeHandle, def);
        }
        /**
         * @param def default value
         * @return value or {@code def} on parse error
         */
        public boolean getBoolean(boolean def) {
            return native_get_boolean(mNativeHandle, def);
        }

        private Handle(String nativeHandle) {
            mNativeHandle = nativeHandle;
        }
    }
}
