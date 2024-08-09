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

package android.net;

import android.test.MoreAsserts;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import java.io.FileDescriptor;
import java.io.IOException;

public class CpcSocketTest {

    public void testServer() throws Exception {
        RpmsgServerSocket ss;
        RpmsgSocket ls;

        ss = new RpmsgServerSocket("hello");
        ls = ss.accept();

        byte[] src_buffer = new byte[16];
        for (int i = 0; i < 16; i++) {
            src_buffer[i] = (byte) i;
        }

        ls.getOutputStream().write(src_buffer, 0, 16);

        byte[] dst_buffer = new byte[16];
        int countRead;

        countRead = ls.getInputStream().read(dst_buffer, 0, 16);
        MoreAsserts.assertEquals(dst_buffer, src_buffer);

        ls.close();
        ss.close();
   }

    public void testClient() throws Exception {
        RpmsgSocket ls;

        ls = new RpmsgSocket();

        ls.connect(new RpmsgSocketAddress("ap", "hello"));

        byte[] src_buffer = new byte[16];
        for (int i = 0; i < 16; i++) {
            src_buffer[i] = (byte) i;
        }

        byte[] dst_buffer = new byte[16];
        int countRead;

        countRead = ls.getInputStream().read(dst_buffer, 0, 16);
        MoreAsserts.assertEquals(dst_buffer, src_buffer);

        ls.getOutputStream().write(src_buffer, 0, 16);
        ls.close();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: CpcSocketTest <server|client>");
            return;
        }

        CpcSocketTest test = new CpcSocketTest();
        try {
            if (args[0].equals("server")) {
                test.testServer();
            } else if (args[0].equals("client")) {
                test.testClient();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
