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

#define LOG_TAG "CpcRemoteJNI"

//#include <core_jni_helpers.h>
#include <dirent.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <string>
#include <sys/stat.h>
#include <utils/Log.h>
#include <utils/misc.h>
#include <vector>

namespace android {

static jboolean CpcRemote_connection_check(JNIEnv* env, jclass, jstring cpuNameJ)
{
    ScopedUtfChars cpuName(env, cpuNameJ);
    jboolean ret = JNI_FALSE;

    if (!cpuName.c_str()) {
        return 0;
    }

    char devPath[256];
    struct stat devStat;

    sprintf(devPath, "/sys/devices/virtual/misc/rpmsg-ping-%s", cpuName.c_str());

    if (stat(devPath, &devStat) == 0) {
        ALOGI("DevPath: %s exists\n", devPath);
        ret = JNI_TRUE;
    } else {
        ALOGI("DevPath: %s does not exist\n", devPath);
    }

    return ret;
}

static jobjectArray CpcRemote_connection_list(JNIEnv* env, jclass /*clazz*/)
{
    DIR* dir;
    size_t idx = 0;
    char devPath[256];
    struct dirent* ent;
    std::vector<std::string> vec;

    sprintf(devPath, "/sys/devices/virtual/misc/");

    if ((dir = opendir(devPath)) == NULL) {
        return nullptr;
    }

    const int prefixLen = strlen("rpmsg-ping-");

    while ((ent = readdir(dir)) != NULL) {
        if (strncmp(ent->d_name, "rpmsg-ping-", prefixLen) == 0) {
            vec.push_back(std::string(ent->d_name + prefixLen));
        }
    }

    jobjectArray array = env->NewObjectArray(vec.size(),
        env->FindClass("java/lang/String"), nullptr);
    if (array == nullptr) {
        goto end;
    }

    for (auto& str : vec) {
        jstring java_string = env->NewStringUTF(str.c_str());
        if (java_string == nullptr) {
            goto end;
        }
        env->SetObjectArrayElement(array, idx++, env->NewStringUTF(str.c_str()));
        env->DeleteLocalRef(java_string);
    }

end:
    closedir(dir);
    return array;
}

static JNINativeMethod sMethods[] = {
    { "native_check_remote", "(Ljava/lang/String;)Z",
        (void*)CpcRemote_connection_check },
    { "native_list_remote", "()[Ljava/lang/String;",
        (void*)CpcRemote_connection_list },
};

} // namespace android

int register_android_os_CpcRemote(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "android/os/CpcRemote",
        android::sMethods, NELEM(android::sMethods));
}
