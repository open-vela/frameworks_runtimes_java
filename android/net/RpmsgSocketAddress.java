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

package android.net;

/**
 * A RPMSG-domain (AF_RPMSG) socket address. For use with
 * android.net.RpmsgSocket and android.net.RpmsgServerSocket.
 *
 * On the Android system, these names refer to names in the Linux
 * abstract (non-filesystem) Rpmsg domain namespace.
 */
public class RpmsgSocketAddress {
    private final String rpCpu;
    private final String rpName;

    /**
     * Creates an instance with a given name.
     *
     * @param rpCpu non-null rpmsg socket cpu name
     * @param rpName non-numm rpmsg socket name
     */
    public RpmsgSocketAddress(String rpCpu, String rpName) {
        this.rpCpu = rpCpu;
        this.rpName = rpName;
    }

    /**
     * Creates an instance with a given name in the {@link Namespace#ABSTRACT}
     * namespace
     *
     * @param rpName non-null rpmsg socket name
     */
    public RpmsgSocketAddress(String rpName) {
        this("", rpName);
    }

    /**
     * Retrieves the string rpCpu
     * @return string rpCpu
     */
    public String getCpuName()
    {
        return rpCpu;
    }

    /**
     * Retrieves the string rpName
     * @return string rpName
     */
    public String getSocketName()
    {
        return rpName;
    }
}
