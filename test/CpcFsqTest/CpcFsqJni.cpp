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

#define LOG_TAG "CpcFsqJni"

#include <inttypes.h>
#include <string>

#include <binder/ICpcServiceManager.h>
#include <binder/IServiceManager.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>

#include <binder/AidlSocketQueue.h>
#include <binder/SocketDescriptor.h>

#include <utils/Log.h>
#include <utils/String8.h>

#include "ICpcFsqJni.h"

namespace android {

typedef android::AidlSocketQueue<uint8_t> AidlSocketQ;

struct fields_t {
    jfieldID mCpcFsqContext;
    jfieldID mService;
    jfieldID mSunPath;
    jfieldID mNetPort;
    jfieldID mNetAddr;
    jfieldID mRpCpu;
    jfieldID mRpName;
};

static fields_t gFields;

static void nativeCpcFsqInit(JNIEnv* env, jobject object)
{
    jclass clazz = env->FindClass("android/os/CpcFsqApp");
    if (clazz == nullptr) {
        ALOGE("Can't find android/os/CpcFsqJni");
        return;
    }

    gFields.mCpcFsqContext = env->GetFieldID(clazz, "mCpcFsqContext", "J");
    gFields.mService = env->GetFieldID(clazz, "mService", "J");
    gFields.mSunPath = env->GetFieldID(clazz, "mSunPath", "Ljava/lang/String;");
    gFields.mNetPort = env->GetFieldID(clazz, "mNetPort", "I");
    gFields.mNetAddr = env->GetFieldID(clazz, "mNetAddr", "Ljava/lang/String;");
    gFields.mRpCpu = env->GetFieldID(clazz, "mRpCpu", "Ljava/lang/String;");
    gFields.mRpName = env->GetFieldID(clazz, "mRpName", "Ljava/lang/String;");
}

static std::string getStringFromField(JNIEnv* env, jobject obj, jfieldID fieldId,
    const char* defaultValue)
{
    jstring fieldValue = (jstring)(env->GetObjectField(obj, fieldId));
    if (fieldValue == nullptr) {
        return std::string(defaultValue);
    }

    const char* buf = env->GetStringUTFChars(fieldValue, nullptr);
    if (buf == nullptr) {
        return std::string(defaultValue);
    }

    std::string str(buf);
    env->ReleaseStringUTFChars(fieldValue, buf);
    return str;
}

static void nativeCpcFsqCreate(JNIEnv* env, jobject obj, int queueSize)
{
    AidlSocketQ* mQueue = nullptr;
    ::binder::SocketDescriptor desc;
    bool isIpc = true;

    std::string sunPath = getStringFromField(env, obj, gFields.mSunPath, "");
    std::string netAddr = getStringFromField(env, obj, gFields.mNetAddr, "");
    std::string rpCpu = getStringFromField(env, obj, gFields.mRpCpu, "");
    std::string rpName = getStringFromField(env, obj, gFields.mRpName, "");
    jint port = env->GetIntField(obj, gFields.mNetPort);

    if (strcmp(sunPath.c_str(), "")) {
        ::binder::SocketDescriptor::LocalSockAddr local_sock_addr;
        local_sock_addr.sun_path = String16(sunPath.c_str());
        desc.sock_addr = local_sock_addr;
    } else if (strcmp(netAddr.c_str(), "")) {
        ::binder::SocketDescriptor::NetSockAddr net_sock_addr;
        net_sock_addr.net_port = (int)port;
        net_sock_addr.net_addr = String16(netAddr.c_str());
        desc.sock_addr = net_sock_addr;
    } else if (strcmp(rpCpu.c_str(), "") && strcmp(rpName.c_str(), "")) {
        ::binder::SocketDescriptor::RpmsgSockAddr rpmsg_sock_addr;
        rpmsg_sock_addr.rp_cpu = String16(rpCpu.c_str());
        rpmsg_sock_addr.rp_name = String16(rpName.c_str());
        desc.sock_addr = rpmsg_sock_addr;
        isIpc = false;
    } else {
        ALOGE("Invalid CpcFsqJni parameters!\n");
        return;
    }

    // obtain service manager
    IServiceManager* sm;
    sp<IServiceManager> sm_ipc(defaultServiceManager());
    sp<IServiceManager> sm_rpc(defaultCpcServiceManager());

    if (isIpc) {
        sm = sm_ipc.get();
    } else {
        sm = sm_rpc.get();
    }

    // obtain socketQ.service
    sp<IBinder> binder = sm->getService(String16("socketQ.service"));
    if (binder == NULL) {
        ALOGE("socketQ service binder is null, abort...");
        return;
    }
    ALOGI("socketQ service binder is %p", binder.get());

    // by interface_cast restore ICpcFsqJni
    sp<ICpcFsqJni> service = interface_cast<ICpcFsqJni>(binder);
    ALOGI("socketQ service is %p", service.get());

    auto ret = service->createSocketQ(desc);
    if (!ret.isOk()) {
        ALOGI("Call mService->configureSocketQReadWrite Failed!\n");
        return;
    }

    mQueue = new (std::nothrow) AidlSocketQ(desc, false);
    if (mQueue == nullptr) {
        ALOGE("mQueue is null, abort...");
        return;
    }

    // set CpcFsqContext
    env->SetLongField(obj, gFields.mCpcFsqContext, (jlong)mQueue);
    service->incStrong(obj);
    env->SetLongField(obj, gFields.mService, (jlong)service.get());
}

static void nativeCpcFsqDestroy(JNIEnv* env, jobject obj)
{
    AidlSocketQ* mQueue = (AidlSocketQ*)env->GetLongField(obj, gFields.mCpcFsqContext);

    ICpcFsqJni* pService = (ICpcFsqJni*)env->GetLongField(obj, gFields.mService);
    sp<ICpcFsqJni> service = sp<ICpcFsqJni>::fromExisting(pService);

    service->destroySocketQ();
    service->decStrong(obj);
    service = nullptr;

    delete mQueue;
}

static void nativeCpcFsqWriteFile(JNIEnv* env, jobject obj, jstring jPath)
{
    ScopedUtfChars utfName(env, jPath);
    String16 path(utfName.c_str());
    constexpr int bufferLen = 1024;
    FILE* fd = fopen(utfName.c_str(), "rb");
    if (fd == nullptr) {
        return;
    }

    fseek(fd, 0, SEEK_END);
    int length = ftell(fd);
    fseek(fd, 0, SEEK_SET);

    AidlSocketQ* mQueue = (AidlSocketQ*)env->GetLongField(obj, gFields.mCpcFsqContext);
    ICpcFsqJni* pService = (ICpcFsqJni*)env->GetLongField(obj, gFields.mService);
    sp<ICpcFsqJni> service = sp<ICpcFsqJni>::fromExisting(pService);

    service->requestWriteFilePath(path, length);

    char* buffer = new char[bufferLen];

    while (length > 0) {
        int len = length >= bufferLen ? bufferLen : length;
        int readFileLen = len;
        char* tmp = buffer;
        while (readFileLen > 0) {
            int ret = fread(tmp, 1, readFileLen, fd);
            if (ret <= 0) {
                break;
            }
            readFileLen -= ret;
            tmp += ret;
        }
        auto ret = service->requestReadSocketQAsync(len);
        if (!ret.isOk()) {
            ALOGE("Call mService->requestReadSocketQAsync Failed!\n");
            delete[] buffer;
            fclose(fd);
            return;
        }
        mQueue->write(reinterpret_cast<uint8_t*>(buffer), len);
        length -= len;
    }

    delete[] buffer;
    fclose(fd);
}

static void nativeCpcFsqWrite(JNIEnv* env, jobject obj,
    jbyteArray buffer, jint length)
{
    AidlSocketQ* mQueue = (AidlSocketQ*)env->GetLongField(obj, gFields.mCpcFsqContext);
    ICpcFsqJni* pService = (ICpcFsqJni*)env->GetLongField(obj, gFields.mService);
    sp<ICpcFsqJni> service = sp<ICpcFsqJni>::fromExisting(pService);
    jboolean isCopy;

    jbyte* dst = env->GetByteArrayElements(buffer, &isCopy);
    if (dst == nullptr) {
        ALOGE("Failed to get byte array elements\n");
        return;
    }

    auto ret = service->requestReadSocketQAsync(length);
    if (!ret.isOk()) {
        ALOGE("Call mService->requestReadSocketQAsync Failed!\n");
        return;
    }

    mQueue->write(reinterpret_cast<uint8_t*>(dst), length);

    env->ReleaseByteArrayElements(buffer, dst, 0);
}

static void nativeCpcFsqRead(JNIEnv* env, jobject obj,
    jbyteArray buffer, jint length)
{
    AidlSocketQ* mQueue = (AidlSocketQ*)env->GetLongField(obj, gFields.mCpcFsqContext);
    ICpcFsqJni* pService = (ICpcFsqJni*)env->GetLongField(obj, gFields.mService);
    sp<ICpcFsqJni> service = sp<ICpcFsqJni>::fromExisting(pService);
    jboolean isCopy;

    jbyte* dst = env->GetByteArrayElements(buffer, &isCopy);
    if (dst == nullptr) {
        ALOGE("Failed to get byte array elements\n");
        return;
    }

    auto ret = service->requestWriteSocketQAsync(length);
    if (!ret.isOk()) {
        ALOGE("Call mService->requestWriteSocketQAsync Failed!\n");
        return;
    }

    mQueue->read(reinterpret_cast<uint8_t*>(dst), length);

    uint8_t* pDst = reinterpret_cast<uint8_t*>(dst);

    env->ReleaseByteArrayElements(buffer, dst, 0);
}

static JNINativeMethod sMethods[] = {
    { "nativeCpcFsqInit", "()V", (void*)nativeCpcFsqInit },
    { "nativeCpcFsqCreate", "(I)V", (void*)nativeCpcFsqCreate },
    { "nativeCpcFsqWriteFile", "(Ljava/lang/String;)V", (void*)nativeCpcFsqWriteFile },
    { "nativeCpcFsqWrite", "([BI)V", (void*)nativeCpcFsqWrite },
    { "nativeCpcFsqRead", "([BI)V", (void*)nativeCpcFsqRead },
    { "nativeCpcFsqDestroy", "()V", (void*)nativeCpcFsqDestroy },
};

} // namespace android

jint JNI_OnLoad(JavaVM* jvm, void*)
{
    JNIEnv* env = nullptr;
    if (jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6)) {
        return JNI_ERR;
    }

    if (jniRegisterNativeMethods(env, "android/os/CpcFsqApp",
            android::sMethods, NELEM(android::sMethods))
        < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
