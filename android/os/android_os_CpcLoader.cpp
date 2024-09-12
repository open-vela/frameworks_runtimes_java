/*
 * Copyright (C) 2024 Xiaomi Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <nativehelper/JNIHelp.h>

extern int register_android_os_CpcProperties(JNIEnv* env);
extern int register_android_os_CpcRemote(JNIEnv* env);
#ifndef NO_CPC_BINDER
extern int register_android_os_CpcServiceManager(JNIEnv* env);
#endif
extern int register_android_net_RpmsgSocket(JNIEnv* env);

jint JNI_OnLoad(JavaVM* jvm, void*)
{
    JNIEnv* env = nullptr;
    if (jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6)) {
        return JNI_ERR;
    }

    if (register_android_os_CpcProperties(env) < 0) {
        return JNI_ERR;
    }

    if (register_android_os_CpcRemote(env) < 0) {
        return JNI_ERR;
    }

#ifndef NO_CPC_BINDER
    if (register_android_os_CpcServiceManager(env) < 0) {
        return JNI_ERR;
    }
#endif

    if (register_android_net_RpmsgSocket(env) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
