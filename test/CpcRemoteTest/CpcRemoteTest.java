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

public class CpcRemoteTest {

    public void testRemote() throws Exception {
        boolean ret;
        String[] cpuNameArray = {
            "ap", "sensor", "audio"
        };

        for (int i = 0; i < cpuNameArray.length; i++) {
            ret = CpcRemote.checkRemote(cpuNameArray[i]);
            System.out.println("checkRemote: " + cpuNameArray[i] +
                               " " + (ret ? "online" : "offline"));
        }

        String[] remoteCpuList = CpcRemote.listRemote();
        System.out.println("remote online cpus: ");
        for (String cpu : remoteCpuList) {
            System.out.println(cpu + "\n");
        }
    }

    public static void main(String[] args) {
        CpcRemoteTest test = new CpcRemoteTest();
        try {
            test.testRemote();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
