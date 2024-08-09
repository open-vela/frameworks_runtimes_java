/*
** Copyright (C) 2024 Xiaomi Corporation
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "RpmsgSocket"

#include <jni.h>
#include <nativehelper/JNIPlatformHelp.h>
#include <utils/Log.h>
#include <utils/misc.h>

#include <errno.h>
#include <netpacket/rpmsg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#include <android-base/macros.h>
#include <cutils/sockets.h>
#include <nativehelper/ScopedUtfChars.h>

namespace android {

/* private native FileDescriptor native_create(int domain,
 * int type, int protocol) throws IOException
 */
static jobject
rpmsg_socket_create(JNIEnv* env, jobject object,
    jint type, jint protocol)
{
    int fd = socket(PF_RPMSG, type, protocol);
    if (fd < 0) {
        jniThrowIOException(env, errno);
    }

    jobject jifd = jniCreateFileDescriptor(env, fd);
    if (jifd == NULL) {
        // OOME prevented allocation of j.i.FileDescriptor instance, close fd to avoid leak.
        close(fd);
    }
    return jifd;
}

/* private native void native_connect(FileDescriptor fd,
 * String rpCpu, String rpName) throws IOException
 */
static void
rpmsg_socket_connect(JNIEnv* env, jobject object,
    jobject fileDescriptor, jstring rpCpu, jstring rpName)
{
    int ret;
    int fd;

    if (rpCpu == NULL || rpName == NULL) {
        jniThrowNullPointerException(env, NULL);
        return;
    }

    fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

    if (env->ExceptionCheck()) {
        return;
    }

    ScopedUtfChars rpCpuUtf8(env, rpCpu);
    ScopedUtfChars rpNameUtf8(env, rpName);

    struct sockaddr_rpmsg sockaddr;
    memset(&sockaddr, 0, sizeof(sockaddr));
    sockaddr.rp_family = AF_RPMSG;
    strlcpy(sockaddr.rp_cpu, rpCpuUtf8.c_str(), RPMSG_SOCKET_CPU_SIZE);
    strlcpy(sockaddr.rp_name, rpNameUtf8.c_str(), RPMSG_SOCKET_NAME_SIZE);

    ret = connect(fd, (struct sockaddr*)&sockaddr, sizeof(sockaddr));
    if (ret < 0) {
        jniThrowIOException(env, errno);
    }
}

/* private native void native_bind(FileDescriptor fd, String rpCpu, String rpName)
 * throws IOException;
 */
static void
rpmsg_socket_bind(JNIEnv* env, jobject object, jobject fileDescriptor,
    jstring rpCpu, jstring rpName)
{
    int ret;
    int fd;

    if (rpCpu == NULL || rpName == NULL) {
        jniThrowNullPointerException(env, NULL);
        return;
    }

    fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

    if (env->ExceptionCheck()) {
        return;
    }

    ScopedUtfChars rpCpuUtf8(env, rpCpu);
    ScopedUtfChars rpNameUtf8(env, rpName);

    struct sockaddr_rpmsg sockaddr;
    memset(&sockaddr, 0, sizeof(sockaddr));
    sockaddr.rp_family = AF_RPMSG;
    strlcpy(sockaddr.rp_cpu, rpCpuUtf8.c_str(), RPMSG_SOCKET_CPU_SIZE);
    strlcpy(sockaddr.rp_name, rpNameUtf8.c_str(), RPMSG_SOCKET_NAME_SIZE);

    ret = bind(fd, (struct sockaddr*)&sockaddr, sizeof(sockaddr));

    if (ret < 0) {
        jniThrowIOException(env, errno);
    }
}

/* private native void native_read(byte[] b, int off, int len,
 * FileDescriptor fd) throws IOException;
 */
static jint rpmsg_socket_read(JNIEnv* env, jobject object,
    jbyteArray buffer, jint off, jint len, jobject fileDescriptor)
{
    int fd;
    jbyte* byteBuffer;
    int ret;

    if (fileDescriptor == NULL || buffer == NULL) {
        jniThrowNullPointerException(env, NULL);
        return -1;
    }

    if (off < 0 || len < 0 || off + len > env->GetArrayLength(buffer)) {
        jniThrowException(env, "java/lang/ArrayIndexOutOfBoundsException", NULL);
        return -1;
    }

    if (len == 0) {
        // because read returns 0 on EOF
        return 0;
    }

    fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

    if (env->ExceptionCheck()) {
        return -1;
    }

    byteBuffer = env->GetByteArrayElements(buffer, NULL);
    if (NULL == byteBuffer) {
        // an exception will have been thrown
        return -1;
    }

    ret = read(fd, byteBuffer + off, len);
    if (ret < 0) {
        jniThrowIOException(env, errno);
    }

    env->ReleaseByteArrayElements(buffer, byteBuffer, 0);

    return ret;
}

/* private native int native_read_one_byte(FileDescriptor fd)
 * throws IOException;
 */
static jint
rpmsg_socket_read_one_byte(JNIEnv* env, jobject object,
    jobject fileDescriptor)
{
    int fd;
    int ret;

    if (fileDescriptor == NULL) {
        jniThrowNullPointerException(env, NULL);
        return -1;
    }

    fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

    if (env->ExceptionCheck()) {
        return 0;
    }

    unsigned char buf;

    ret = read(fd, &buf, 1);
    if (ret < 0) {
        jniThrowIOException(env, errno);
    }

    if (ret == 0) {
        // end of file
        return -1;
    }

    return buf;
}

/* private native int native_write(byte[] b, int off, int len,
 * FileDescriptor fd) throws IOException;
 */
static jint
rpmsg_socket_write(JNIEnv* env, jobject object,
    jbyteArray buffer, jint off, jint len, jobject fileDescriptor)
{
    int fd;
    jbyte* byteBuffer;
    int ret;

    if (fileDescriptor == NULL || buffer == NULL) {
        jniThrowNullPointerException(env, NULL);
        return -1;
    }

    if (off < 0 || len < 0 || off + len > env->GetArrayLength(buffer)) {
        jniThrowException(env, "java/lang/ArrayIndexOutOfBoundsException", NULL);
        return -1;
    }

    if (len == 0) {
        return 0;
    }

    fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

    if (env->ExceptionCheck()) {
        return -1;
    }

    byteBuffer = env->GetByteArrayElements(buffer, NULL);
    if (NULL == byteBuffer) {
        // an exception will have been thrown
        return -1;
    }

    ret = write(fd, byteBuffer + off, len);
    if (ret < 0) {
        jniThrowIOException(env, errno);
    }

    env->ReleaseByteArrayElements(buffer, byteBuffer, 0);

    return ret;
}

/* private native int native_write_one_byte(int b, FileDescriptor fd)
 * throws IOException;
 */
static jint
rpmsg_socket_write_one_byte(JNIEnv* env, jobject object,
    jint b, jobject fileDescriptor)
{
    int fd;
    int ret;

    if (fileDescriptor == NULL) {
        jniThrowNullPointerException(env, NULL);
        return -1;
    }

    fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

    if (env->ExceptionCheck()) {
        return 0;
    }

    ret = write(fd, &b, 1);

    if (ret < 0) {
        jniThrowIOException(env, errno);
    }

    if (ret == 0) {
        // end of file
        return -1;
    }

    return ret;
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "native_create", "(II)Ljava/io/FileDescriptor;", (void*)rpmsg_socket_create },
    { "native_connect", "(Ljava/io/FileDescriptor;Ljava/lang/String;Ljava/lang/String;)V",
        (void*)rpmsg_socket_connect },
    { "native_bind", "(Ljava/io/FileDescriptor;Ljava/lang/String;Ljava/lang/String;)V",
        (void*)rpmsg_socket_bind },
    { "native_read", "([BIILjava/io/FileDescriptor;)I", (void*)rpmsg_socket_read },
    { "native_read_one_byte", "(Ljava/io/FileDescriptor;)I",
        (void*)rpmsg_socket_read_one_byte },
    { "native_write", "([BIILjava/io/FileDescriptor;)I", (void*)rpmsg_socket_write },
    { "native_write_one_byte", "(ILjava/io/FileDescriptor;)I",
        (void*)rpmsg_socket_write_one_byte },
};

};

int register_android_net_RpmsgSocket(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "android/net/RpmsgSocketImpl",
        android::gMethods, NELEM(android::gMethods));
}
