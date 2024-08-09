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

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Non-standard class for creating an inbound RPMSG-domain socket
 * in the Linux abstract namespace.
 */

public class RpmsgServerSocket implements Closeable {
    private final RpmsgSocketImpl impl;
    private final RpmsgSocketAddress rpmsgAddress;

    private static final int LISTEN_BACKLOG = 16;

    /**
     * Creates a new server socket listening at specified name.
     * On the Android platform, the name is created in the Linux
     * abstract namespace (instead of on the filesystem).
     *
     * @param name for the socket name
     * @throws IOException
     */
    public RpmsgServerSocket(String name) throws IOException
    {
        impl = new RpmsgSocketImpl();

        impl.create(RpmsgSocket.SOCKET_STREAM);

        rpmsgAddress = new RpmsgSocketAddress(name);
        impl.bind(rpmsgAddress);

        impl.listen(LISTEN_BACKLOG);
    }

    /**
     * Create a RpmsgServerSocket from a file descriptor that's already
     * been created and bound. listen() will be called immediately on it.
     * Used for cases where file descriptors are passed in via environment
     * variables. The passed-in FileDescriptor is not managed by this class
     * and must be closed by the caller. Calling {@link #close()} on a socket
     * created by this method has no effect.
     *
     * @param fd bound file descriptor
     * @throws IOException
     */
    public RpmsgServerSocket(FileDescriptor fd) throws IOException
    {
        impl = new RpmsgSocketImpl(fd);
        impl.listen(LISTEN_BACKLOG);
        rpmsgAddress = impl.getSockAddress();
    }

    /**
     * Obtains the socket's rpmsg address
     *
     * @return rpmsg address
     */
    public RpmsgSocketAddress getRpmsgSocketAddress()
    {
        return rpmsgAddress;
    }

    /**
     * Accepts a new connection to the socket. Blocks until a new
     * connection arrives.
     *
     * @return a socket representing the new connection.
     * @throws IOException
     */
    public RpmsgSocket accept() throws IOException
    {
        RpmsgSocketImpl acceptedImpl = new RpmsgSocketImpl();

        impl.accept(acceptedImpl);

        return RpmsgSocket.createRpmsgSocketForAccept(acceptedImpl);
    }

    /**
     * Returns file descriptor or null if not yet open/already closed
     *
     * @return fd or null
     */
    public FileDescriptor getFileDescriptor() {
        return impl.getFileDescriptor();
    }

    /**
     * Closes server socket.
     *
     * @throws IOException
     */
    @Override public void close() throws IOException
    {
        impl.close();
    }
}
