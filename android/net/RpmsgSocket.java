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

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.system.ErrnoException;
import android.system.Os;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketOptions;

/**
 * Creates a (non-server) socket in the Rpmsg-domain namespace. The interface
 * here is not entirely unlike that of java.net.Socket. This class and the streams
 * returned from it may be used from multiple threads.
 */
public class RpmsgSocket implements Closeable {

    private final RpmsgSocketImpl impl;
    /** false if impl.create() needs to be called */
    private volatile boolean implCreated;
    private RpmsgSocketAddress address;
    private boolean isBound;
    private boolean isConnected;
    private final int sockType;

    /** unknown socket type (used for constructor with existing file descriptor) */
    /* package */ static final int SOCKET_UNKNOWN = 0;
    /** Datagram socket type */
    public static final int SOCKET_DGRAM = 1;
    /** Stream socket type */
    public static final int SOCKET_STREAM = 2;

    /**
     * Creates a AF_RPMSG domain stream socket.
     */
    public RpmsgSocket() {
        this(SOCKET_STREAM);
    }

    /**
     * Creates a AF_RPMSG domain stream socket with given socket type
     *
     * @param sockType either {@link #SOCKET_DGRAM}, {@link #SOCKET_STREAM}
     */
    public RpmsgSocket(int sockType) {
        this(new RpmsgSocketImpl(), sockType);
    }

    private RpmsgSocket(RpmsgSocketImpl impl, int sockType) {
        this.impl = impl;
        this.sockType = sockType;
        this.isConnected = false;
        this.isBound = false;
    }

    private void setConnected() {
        isConnected = true;
        isBound = true;
        implCreated = true;
    }

    /**
     * Creates a RpmsgSocket instance using the {@link FileDescriptor} for an already-connected
     * AF_RPMSG domain stream socket. The passed-in FileDescriptor is not managed by this class
     * and must be closed by the caller. Calling {@link #close()} on a socket created by this
     * method has no effect.
     *
     * @param fd the filedescriptor to adopt
     *
     * @hide
     */
    public RpmsgSocket(@NonNull FileDescriptor fd) {
        this(new RpmsgSocketImpl(fd), SOCKET_UNKNOWN);
        setConnected();
    }

    /**
     * for use with RpmsgServerSocket.accept()
     */
    static RpmsgSocket createRpmsgSocketForAccept(RpmsgSocketImpl impl) {
        RpmsgSocket socket = new RpmsgSocket(impl, SOCKET_UNKNOWN);
        socket.setConnected();
        return socket;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return super.toString() + " impl:" + impl;
    }

    /**
     * It's difficult to discern from the spec when impl.create() should be
     * called, but it seems like a reasonable rule is "as soon as possible,
     * but not in a context where IOException cannot be thrown"
     *
     * @throws IOException from SocketImpl.create()
     */
    private void createIfNeeded() throws IOException {
        if (!implCreated) {
            synchronized (this) {
                if (!implCreated) {
                    try {
                        impl.create(sockType);
                    } finally {
                        implCreated = true;
                    }
                }
            }
        }
    }

    /**
     * Connects this socket to an endpoint. May only be called on an instance
     * that has not yet been connected.
     *
     * @param endpoint endpoint address
     * @throws IOException if socket is in invalid state or the address does
     * not exist.
     */
    public void connect(RpmsgSocketAddress endpoint) throws IOException {
        synchronized (this) {
            if (isConnected) {
                throw new IOException("already connected");
            }

            createIfNeeded();
            impl.connect(endpoint, 0);
            isConnected = true;
            isBound = true;
        }
    }

    /**
     * Binds this socket to an endpoint name. May only be called on an instance
     * that has not yet been bound.
     *
     * @param bindpoint endpoint address
     * @throws IOException
     */
    public void bind(RpmsgSocketAddress bindpoint) throws IOException {
        createIfNeeded();

        synchronized (this) {
            if (isBound) {
                throw new IOException("already bound");
            }

            address = bindpoint;
            impl.bind(address);
            isBound = true;
        }
    }

    /**
     * Retrieves the name that this socket is bound to, if any.
     *
     * @return Rpmsg address or null if anonymous
     */
    public RpmsgSocketAddress getRpmsgSocketAddress() {
        return address;
    }

    /**
     * Retrieves the input stream for this instance. Closing this stream is equivalent to closing
     * the entire socket and its associated streams using {@link #close()}.
     *
     * @return input stream
     * @throws IOException if socket has been closed or cannot be created.
     */
    public InputStream getInputStream() throws IOException {
        createIfNeeded();
        return impl.getInputStream();
    }

    /**
     * Retrieves the output stream for this instance. Closing this stream is equivalent to closing
     * the entire socket and its associated streams using {@link #close()}.
     *
     * @return output stream
     * @throws IOException if socket has been closed or cannot be created.
     */
    public OutputStream getOutputStream() throws IOException {
        createIfNeeded();
        return impl.getOutputStream();
    }

    /**
     * Closes the socket.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        createIfNeeded();
        impl.close();
    }

    /**
     * Shuts down the input side of the socket.
     *
     * @throws IOException
     */
    public void shutdownInput() throws IOException {
        createIfNeeded();
        impl.shutdownInput();
    }

    /**
     * Shuts down the output side of the socket.
     *
     * @throws IOException
     */
    public void shutdownOutput() throws IOException {
        createIfNeeded();
        impl.shutdownOutput();
    }

    public int getReceiveBufferSize() throws IOException {
        return ((Integer)impl.getOption(SocketOptions.SO_RCVBUF)).intValue();
    }

    public void setSoTimeout(int n) throws IOException {
        impl.setOption(SocketOptions.SO_TIMEOUT, Integer.valueOf(n));
    }

    public int getSoTimeout() throws IOException {
        return ((Integer)impl.getOption(SocketOptions.SO_TIMEOUT)).intValue();
    }

    public int getSendBufferSize() throws IOException {
        return ((Integer)impl.getOption(SocketOptions.SO_SNDBUF)).intValue();
    }

    public RpmsgSocketAddress getSocketAddress() {
        throw new UnsupportedOperationException();
    }

    public synchronized boolean isConnected() {
        return isConnected;
    }

    public boolean isClosed() {
        throw new UnsupportedOperationException();
    }

    public synchronized boolean isBound() {
        return isBound;
    }

    public boolean isOutputShutdown() {
        throw new UnsupportedOperationException();
    }

    public boolean isInputShutdown() {
        throw new UnsupportedOperationException();
    }

    public void connect(RpmsgSocketAddress endpoint, int timeout)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns file descriptor or null if not yet open/already closed
     *
     * @return fd or null
     */
    public FileDescriptor getFileDescriptor() {
        return impl.getFileDescriptor();
    }
}
