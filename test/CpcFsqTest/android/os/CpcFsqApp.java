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

package android.os;

import android.util.Log;

public class CpcFsqApp {

    private long mCpcFsqContext;
    private long mService;
    private String mSunPath;
    private int mNetPort;
    private String mNetAddr;
    private String mRpCpu;
    private String mRpName;

    private static final int mQueueSize = 1024;

    private native void nativeCpcFsqInit();
    private native void nativeCpcFsqCreate(int queueSize);
    private native void nativeCpcFsqWriteFile(String path);
    private native void nativeCpcFsqWrite(byte[] buffer, int size);
    private native void nativeCpcFsqRead(byte[] buffer, int size);
    private native void nativeCpcFsqDestroy();

    static {
        System.loadLibrary("cpcfsq_jni");
    }

    private boolean checkData(byte[] buffer, int size) {
        for (int i = 0; i < size; ++i) {
            if (buffer[i] != (byte) (i % 255)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("[usage]: CpcFsqApp [local | net | rpmsg]\n");
            for (int i = 0; i < args.length; ++i) {
                System.out.println("arg " + i + " val: " + args[i] + "\n");
            }
            return;
        }

        CpcFsqApp cpcFsqApp = new CpcFsqApp();

        if (args[0].equals("local")) {
            cpcFsqApp.mSunPath = "./socket_server";
        } else if (args[0].equals("net")) {
            cpcFsqApp.mNetPort = 12345;
            cpcFsqApp.mNetAddr = "127.0.0.1";
        } else if (args[0].equals("rpmsg")) {
            cpcFsqApp.mRpCpu = "ap";
            cpcFsqApp.mRpName = "Hello";
        } else {
            System.out.println("Invalid arg: " + args[0]);
            System.out.println("[usage]: CpcFsqApp [local | net | rpmsg]\n");
        }

        int queueSize = CpcFsqApp.mQueueSize;

        cpcFsqApp.nativeCpcFsqInit();

        System.out.println("Start create...\n");
        cpcFsqApp.nativeCpcFsqCreate(queueSize);
        System.out.println("Create success\n");

        System.out.println("Start write...\n");
        byte [] writeBuffer = new byte[queueSize];
        for (int i = 0; i < queueSize; ++i) {
            writeBuffer[i] = (byte) (i % 255);
        }
        cpcFsqApp.nativeCpcFsqWrite(writeBuffer, queueSize);

        System.out.println("Start read...\n");
        byte [] readBuffer = new byte[queueSize];
        cpcFsqApp.nativeCpcFsqRead(readBuffer, queueSize);
        boolean ret = cpcFsqApp.checkData(readBuffer, queueSize);
        if (ret) {
            System.out.println("Server write Client read case pass!\n");
        } else {
            System.out.println("Server write Client read case fail\n");
        }

        cpcFsqApp.nativeCpcFsqWriteFile("libcpcfsq_jni.so");

        cpcFsqApp.nativeCpcFsqDestroy();
    }
}
