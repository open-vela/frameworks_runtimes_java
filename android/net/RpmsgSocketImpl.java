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

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketOptions;

/**
 * Socket implementation used for android.net.RpmsgSocket and
 * android.net.RpmsgServerSocket. Supports only AF_RPMSG sockets.
 */
class RpmsgSocketImpl
{
    private SocketInputStream fis;
    private SocketOutputStream fos;
    private Object readMonitor = new Object();
    private Object writeMonitor = new Object();

    /** null if closed or not yet created */
    private FileDescriptor fd;
    /** whether fd is created internally */
    private boolean fdCreatedInternally;

    static {
        System.loadLibrary("cpc_extension_jni");
    }

    /**
     * An input stream for rpmsg sockets. Needed because we may
     * need to read ancillary data.
     */
    class SocketInputStream extends InputStream {
        /** {@inheritDoc} */
        @Override
        public int available() throws IOException {
            try {
                return Os.ioctlInt(fd, OsConstants.FIONREAD);
            } catch (ErrnoException e) {
                throw e.rethrowAsIOException();
            }
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws IOException {
            RpmsgSocketImpl.this.close();
        }

        /** {@inheritDoc} */
        @Override
        public int read() throws IOException {
            synchronized (readMonitor) {
                return native_read_one_byte(fd);
            }
        }

        /** {@inheritDoc} */
        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        /** {@inheritDoc} */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            synchronized (readMonitor) {
                return native_read(b, off, len, fd);
            }
        }
    }

    /**
     * An output stream for rpmsg sockets. Needed because we may
     * need to read ancillary data.
     */
    class SocketOutputStream extends OutputStream {
        /** {@inheritDoc} */
        @Override
        public void close() throws IOException {
            RpmsgSocketImpl.this.close();
        }

        /** {@inheritDoc} */
        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        /** {@inheritDoc} */
        @Override
        public void write(int b) throws IOException {
            synchronized (writeMonitor) {
                native_write_one_byte(b, fd);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            synchronized (writeMonitor) {
                native_write(b, off, len, fd);
            }
        }
    }

    public native FileDescriptor native_create(int type, int protocol)
            throws ErrnoException;
    private native void native_connect(FileDescriptor fd, String rpCpu,
            String rpName) throws IOException;
    private native void native_bind(FileDescriptor fd, String rpCpu,
            String rpName) throws IOException;
    private native int native_read_one_byte(FileDescriptor fd) throws IOException;
    private native int native_read(byte[] b, int off, int len,
            FileDescriptor fd) throws IOException;
    private native int native_write_one_byte(int b, FileDescriptor fd)
            throws IOException;
    private native int native_write(byte[] b, int off, int len,
            FileDescriptor fd) throws IOException;

    /**
     * Create a new instance.
     */
    /*package*/ RpmsgSocketImpl()
    {
    }

    /**
     * Create a new instance from a file descriptor representing
     * a bound socket. The state of the file descriptor is not checked here
     *  but the caller can verify socket state by calling listen().
     *
     * @param fd non-null; bound file descriptor
     */
    /*package*/ RpmsgSocketImpl(FileDescriptor fd)
    {
        this.fd = fd;
    }

    public String toString() {
        return super.toString() + " fd:" + fd;
    }

    /**
     * Creates a socket in the underlying OS.
     *
     * @param sockType either {@link RpmsgSocket#SOCKET_DGRAM}, {@link RpmsgSocket#SOCKET_STREAM}
     * @throws IOException
     */
    public void create(int sockType) throws IOException {
        if (fd != null) {
            throw new IOException("RpmsgSocketImpl already has an fd");
        }

        int osType;
        switch (sockType) {
            case RpmsgSocket.SOCKET_DGRAM:
                osType = OsConstants.SOCK_DGRAM;
                break;
            case RpmsgSocket.SOCKET_STREAM:
                osType = OsConstants.SOCK_STREAM;
                break;
            default:
                throw new IllegalStateException("unknown sockType");
        }
        try {
            fd = native_create(osType, 0);
            fdCreatedInternally = true;
        } catch (ErrnoException e) {
            e.rethrowAsIOException();
        }
    }

    /**
     * Closes the socket.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        synchronized (RpmsgSocketImpl.this) {
            try {
                Os.close(fd);
            } catch (ErrnoException e) {
                e.rethrowAsIOException();
            }
            fd = null;
        }
    }

    /** note timeout presently ignored */
    protected void connect(RpmsgSocketAddress address, int timeout)
                        throws IOException
    {
        if (fd == null) {
            throw new IOException("socket not created");
        }

        native_connect(fd, address.getCpuName(), address.getSocketName());
    }

   /**
     * Binds this socket to an endpoint name. May only be called on an instance
     * that has not yet been bound.
     *
     * @param endpoint endpoint address
     * @throws IOException
     */
    public void bind(RpmsgSocketAddress endpoint) throws IOException
    {
        if (fd == null) {
            throw new IOException("socket not created");
        }

        native_bind(fd, endpoint.getCpuName(), endpoint.getSocketName());
    }

    protected void listen(int backlog) throws IOException
    {
        if (fd == null) {
            throw new IOException("socket not created");
        }
        try {
            Os.listen(fd, backlog);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        }
    }

    /**
     * Accepts a new connection to the socket. Blocks until a new
     * connection arrives.
     *
     * @param s a socket that will be used to represent the new connection.
     * @throws IOException
     */
    protected void accept(RpmsgSocketImpl s) throws IOException {
        if (fd == null) {
            throw new IOException("socket not created");
        }

        try {
            s.fd = Os.accept(fd, null /* address */);
            s.fdCreatedInternally = true;
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        }
    }

    /**
     * Retrieves the input stream for this instance.
     *
     * @return input stream
     * @throws IOException if socket has been closed or cannot be created.
     */
    protected InputStream getInputStream() throws IOException
    {
        if (fd == null) {
            throw new IOException("socket not created");
        }

        synchronized (this) {
            if (fis == null) {
                fis = new SocketInputStream();
            }

            return fis;
        }
    }

    /**
     * Retrieves the output stream for this instance.
     *
     * @return output stream
     * @throws IOException if socket has been closed or cannot be created.
     */
    protected OutputStream getOutputStream() throws IOException
    {
        if (fd == null) {
            throw new IOException("socket not created");
        }

        synchronized (this) {
            if (fos == null) {
                fos = new SocketOutputStream();
            }

            return fos;
        }
    }

    /**
     * Returns the number of bytes available for reading without blocking.
     *
     * @return >= 0 count bytes available
     * @throws IOException
     */
    protected int available() throws IOException
    {
        return getInputStream().available();
    }

    /**
     * Shuts down the input side of the socket.
     *
     * @throws IOException
     */
    protected void shutdownInput() throws IOException
    {
        try {
            Os.shutdown(fd, OsConstants.SHUT_RD);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        }
    }

    /**
     * Shuts down the output side of the socket.
     *
     * @throws IOException
     */
    protected void shutdownOutput() throws IOException
    {
        try {
            Os.shutdown(fd, OsConstants.SHUT_WR);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        }
    }

    protected FileDescriptor getFileDescriptor()
    {
        return fd;
    }

    protected boolean supportsUrgentData()
    {
        return false;
    }

    public Object getOption(int optID) throws IOException
    {
        try {
            Object toReturn;
            switch (optID) {
                case SocketOptions.SO_TIMEOUT:
                    StructTimeval timeval = Os.getsockoptTimeval(fd, OsConstants.SOL_SOCKET,
                            OsConstants.SO_SNDTIMEO);
                    toReturn = (int)timeval.toMillis();
                    break;
                case SocketOptions.SO_RCVBUF:
                case SocketOptions.SO_SNDBUF:
                    int osOpt = javaSoToOsOpt(optID);
                    toReturn = Os.getsockoptInt(fd, OsConstants.SOL_SOCKET, osOpt);
                    break;
                default:
                    throw new IOException("Unknown option: " + optID);
            }
            return toReturn;
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        }
    }

    public void setOption(int optID, Object value)
            throws IOException {
        /*
         * Boolean.FALSE is used to disable some options, so it
         * is important to distinguish between FALSE and unset.
         * We define it here that -1 is unset, 0 is FALSE, and 1
         * is TRUE.
         */
        int boolValue = -1;
        int intValue = 0;
        if (value instanceof Integer) {
            intValue = (Integer)value;
        } else if (value instanceof Boolean) {
            boolValue = ((Boolean)value) ? 1 : 0;
        } else {
            throw new IOException("bad value: " + value);
        }

        try {
            switch (optID) {
                case SocketOptions.SO_TIMEOUT:
                    // The option must set both send and receive timeouts.
                    // Note: The incoming timeout value is in milliseconds.
                    StructTimeval timeval = StructTimeval.fromMillis(intValue);
                    Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO,
                            timeval);
                    Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_SNDTIMEO,
                            timeval);
                    break;
                default:
                    int osOpt = javaSoToOsOpt(optID);
                    Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, osOpt, intValue);
                    break;
            }
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        }
    }

    /**
     * Retrieves the socket name from the OS.
     *
     * @return non-null; socket name
     * @throws IOException on failure
     */
    public RpmsgSocketAddress getSockAddress() throws IOException {
        // This method has never been implemented.
        return null;
    }

    @Override
    protected void finalize() throws IOException {
        close();
    }

    private static int javaSoToOsOpt(int optID) {
        switch (optID) {
            case SocketOptions.SO_SNDBUF:
                return OsConstants.SO_SNDBUF;
            case SocketOptions.SO_RCVBUF:
                return OsConstants.SO_RCVBUF;
            default:
                throw new UnsupportedOperationException("Unknown option: " + optID);
        }
    }
}
