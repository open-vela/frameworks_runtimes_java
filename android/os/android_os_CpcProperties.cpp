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

#define LOG_TAG "CpcPropJNI"

#include <optional>
#include <utility>

#include <android-base/logging.h>
#include <android-base/parsebool.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <core_jni_helpers.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedPrimitiveArray.h>
#include <nativehelper/ScopedUtfChars.h>
#include <utils/Log.h>
#include <utils/misc.h>

#include <kvdb.h>
#include <unordered_set>

#include <pthread.h>
#include <sys/epoll.h>

#define CAPACITY 64

typedef void (*prop_change_cb_t)(const char* key, void* cookie);

typedef struct prop_param_s {
    std::string key;
    int fd;
    prop_change_cb_t cb;
    void* cookie;
} prop_param_t;

struct prop_param_hash {
    size_t operator()(const prop_param_t& p1) const
    {
        return std::hash<std::string>()(p1.key);
    }
};

struct prop_param_equal {
    bool operator()(const prop_param_t& p1, const prop_param_t& p2) const noexcept
    {
        return p1.key == p2.key;
    }
};

typedef struct prop_context_s {
    std::unordered_set<prop_param_t, prop_param_hash, prop_param_equal> prop_set;
    pthread_mutex_t prop_mutex;
    int epoll_fd;
} prop_context_t;

static prop_context_t g_ctx = {
    .prop_mutex = PTHREAD_MUTEX_INITIALIZER,
};

static void register_prop_change_cb(const char* key,
    void* cookie, void (*cb)(const char* key, void* cookie))
{
    ALOGD("register_prop_change_cb %s\n", key);
    if (strcmp(key, "")) {
        struct prop_context_s* ctx = &g_ctx;

        pthread_mutex_lock(&ctx->prop_mutex);

        int fd = property_monitor_open(key);
        prop_param_t param = { key, fd, cb, cookie };
        auto it = ctx->prop_set.find(param);
        if (it != ctx->prop_set.end()) {
            pthread_mutex_unlock(&ctx->prop_mutex);
            return;
        }
        ctx->prop_set.insert(param);
        it = ctx->prop_set.find(param);

        struct epoll_event event;
        event.events = EPOLLIN;
        event.data.ptr = const_cast<prop_param_t*>(&(*it));
        epoll_ctl(ctx->epoll_fd, EPOLL_CTL_ADD, fd, &event);

        pthread_mutex_unlock(&ctx->prop_mutex);
    }
}

static void unregister_prop_change_cb(const char* key)
{
    ALOGD("unregister_prop_change_cb %s\n", key);
    if (strcmp(key, "")) {
        struct prop_context_s* ctx = &g_ctx;

        pthread_mutex_lock(&ctx->prop_mutex);

        prop_param_t param = { key, -1, nullptr, nullptr };
        auto it = ctx->prop_set.find(param);
        if (it == ctx->prop_set.end()) {
            pthread_mutex_unlock(&ctx->prop_mutex);
            return;
        }
        param = *it;
        property_monitor_close(param.fd);
        ctx->prop_set.erase(it);

        struct epoll_event event;
        event.data.fd = param.fd;
        epoll_ctl(ctx->epoll_fd, EPOLL_CTL_DEL, param.fd, &event);

        pthread_mutex_unlock(&ctx->prop_mutex);
    }
}

static void* thread_monitor(void* p)
{
    prop_context_t* ctx = &g_ctx;
    struct epoll_event events[CAPACITY];

    while (true) {
        int count = epoll_wait(ctx->epoll_fd, events, CAPACITY, -1);
        if (count <= 0) {
            ALOGE("epoll_wait return %d, errno = %d\n", count, errno);
        } else {
            for (int i = 0; i < count; i++) {
                prop_param_t* param = (prop_param_t*)(events[i].data.ptr);
                property_monitor_read(param->fd, nullptr, nullptr);
                param->cb(param->key.c_str(), param->cookie);
            }
        }
    }

    return nullptr;
}

static void start_thread_monitor()
{
    pthread_t thread;
    prop_context_t* ctx = &g_ctx;

    ctx->epoll_fd = epoll_create1(EPOLL_CLOEXEC);

    pthread_create(&thread, nullptr, &thread_monitor, nullptr);
    pthread_detach(thread);
}

namespace android {

using android::base::ParseBoolResult;

static jstring CpcProperties_getSS(JNIEnv* env, jclass clazz, jstring keyJ,
    jstring defJ)
{
    jstring ret = defJ;
    char value[PROP_VALUE_MAX];
    ScopedUtfChars key(env, keyJ);

    if (!key.c_str()) {
        return 0;
    }

    int rc = property_get(key.c_str(), value, nullptr);
    if (rc > 0) {
        ret = env->NewStringUTF(value);
    } else if (ret == nullptr && !env->ExceptionCheck()) {
        ret = env->NewStringUTF("");
    }

    return ret;
}

static jint CpcProperties_get_integral_jint(JNIEnv* env, jclass, jstring keyJ,
    jint defJ)
{
    ScopedUtfChars key(env, keyJ);

    if (!key.c_str()) {
        return 0;
    }

    return property_get_int32(key.c_str(), defJ);
}

static jlong CpcProperties_get_integral_jlong(JNIEnv* env, jclass, jstring keyJ,
    jlong defJ)
{
    ScopedUtfChars key(env, keyJ);

    if (!key.c_str()) {
        return 0;
    }

    return property_get_int64(key.c_str(), defJ);
}

static jboolean CpcProperties_get_boolean(JNIEnv* env, jclass, jstring keyJ,
    jboolean defJ)
{
    ScopedUtfChars key(env, keyJ);

    if (!key.c_str()) {
        return 0;
    }

    int8_t ret = property_get_bool(key.c_str(), defJ != JNI_FALSE);
    return ret ? JNI_TRUE : JNI_FALSE;
}

static void CpcProperties_set(JNIEnv* env, jobject clazz, jstring keyJ,
    jstring valJ)
{
    ScopedUtfChars key(env, keyJ);
    if (!key.c_str()) {
        return;
    }
    std::optional<ScopedUtfChars> value;
    if (valJ != nullptr) {
        value.emplace(env, valJ);
        if (!value->c_str()) {
            return;
        }
    }
    bool success;
    success = !property_set(key.c_str(), value ? value->c_str() : "");
    if (!success) {
        jniThrowException(env, "java/lang/RuntimeException",
            "failed to set system property (check logcat for reason)");
    }
}

static JavaVM* sVM = nullptr;
static jclass sClazz = nullptr;
static jmethodID sCallPropChangeCallback;

static void properties_change_cb(const char* key, void* cookie)
{
    JNIEnv* env;
    char threadName[32];

    if (0 != pthread_getname_np(pthread_self(), threadName, sizeof(threadName))) {
        constexpr const char* defaultThreadName = "prop_change_notify_thread";
        memcpy(threadName, defaultThreadName,
            std::min<size_t>(sizeof(threadName), strlen(defaultThreadName) + 1));
    }

    JavaVMAttachArgs args;
    args.version = JNI_VERSION_1_4;
    args.name = threadName;
    args.group = nullptr;
    jint ret = sVM->AttachCurrentThread(&env, &args);
    if (ret != JNI_OK) {
        ALOGE("sVM->AttachCurrentThread() failed: %d\n", ret);
        return;
    }

    ret = sVM->GetEnv((void**)&env, JNI_VERSION_1_4);
    if (ret != JNI_OK) {
        ALOGE("sVM->GetEnv() failed: %d\n", ret);
        return;
    }

    jstring jKey = env->NewStringUTF(key);
    env->CallStaticVoidMethod(sClazz, sCallPropChangeCallback, jKey);
    env->DeleteLocalRef(jKey);

    sVM->DetachCurrentThread();
}

static void CpcProperties_add_prop_change_monitor(JNIEnv* env,
    jobject clazz, jstring keyJ)
{
    ScopedUtfChars keyUtf(env, keyJ);

    register_prop_change_cb(keyUtf.c_str(), env, properties_change_cb);
}

static void CpcProperties_remove_prop_change_monitor(JNIEnv* env,
    jobject clazz, jstring keyJ)
{
    ScopedUtfChars keyUtf(env, keyJ);

    unregister_prop_change_cb(keyUtf.c_str());
}

static void CpcProperties_add_prop_change_callback(JNIEnv* env, jobject clazz)
{
    // This is called with the Java lock held.
    if (sVM == nullptr) {
        env->GetJavaVM(&sVM);
    }

    if (sClazz == nullptr) {
        sClazz = (jclass)env->NewGlobalRef(clazz);
        sCallPropChangeCallback = env->GetStaticMethodID(sClazz, "callPropChangeCallback", "(Ljava/lang/String;)V");
        start_thread_monitor();
    }
}

static JNINativeMethod sMethods[] = {
    { "native_get",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        (void*)CpcProperties_getSS },
    { "native_get_int", "(Ljava/lang/String;I)I",
        (void*)CpcProperties_get_integral_jint },
    { "native_get_long", "(Ljava/lang/String;J)J",
        (void*)CpcProperties_get_integral_jlong },
    { "native_get_boolean", "(Ljava/lang/String;Z)Z",
        (void*)CpcProperties_get_boolean },
    { "native_set", "(Ljava/lang/String;Ljava/lang/String;)V",
        (void*)CpcProperties_set },
    { "native_add_prop_change_callback", "()V",
        (void*)CpcProperties_add_prop_change_callback },
    { "native_add_prop_change_monitor", "(Ljava/lang/String;)V",
        (void*)CpcProperties_add_prop_change_monitor },
    { "native_remove_prop_change_monitor", "(Ljava/lang/String;)V",
        (void*)CpcProperties_remove_prop_change_monitor },
};

} // namespace android

int register_android_os_CpcProperties(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "android/os/CpcProperties",
        android::sMethods, NELEM(android::sMethods));
}
