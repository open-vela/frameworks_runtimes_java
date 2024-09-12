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
import android.annotation.SystemApi;
import android.util.Log;

public class CpcRemote {
    private static final String TAG = "CpcRemote";

    static {
        System.loadLibrary("cpc_extension_jni.xiaomi");
    }

    private static native boolean native_check_remote(String cpuName);
    private static native String[] native_list_remote();


    /**
     * Check whether the remote cpu could connect with us
     *
     * @param cpuName the name of the remote cpu
     * @return true if the remote cpu could connect with us
     * @hide
     */
    @NonNull
    @SystemApi
    public static boolean checkRemote(@NonNull String cpuName) {
        return native_check_remote(cpuName);
    }

    /**
     * Get the remote cpu list
     *
     * @return the remote cpu list
     * @hide
     */
    @NonNull
    @SystemApi
    public static String[] listRemote() {
        return native_list_remote();
    }

    private CpcRemote() {
    }
}
