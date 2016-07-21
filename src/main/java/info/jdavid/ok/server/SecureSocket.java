package info.jdavid.ok.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketImpl;

class SecureSocket extends Socket {

  private ReplayableInputStream mBuffered = null;

  SecureSocket() throws IOException {
    super((SocketImpl)null);
  }

  @Override public ReplayableInputStream getInputStream() throws IOException {
    return mBuffered == null ? mBuffered = new ReplayableInputStream(super.getInputStream()) : mBuffered;
  }

  @Override
  public synchronized void close() throws IOException {
    mBuffered = null;
    super.close();
  }

  static class ReplayableInputStream extends InputStream {

    private final InputStream delegate;
    private final byte buf[] = new byte[4096];
    private int pos = 0;
    private int size = 0;
    private boolean reset = false;

    ReplayableInputStream(final InputStream inputStream) {
      super();
      this.delegate = inputStream;
    }

    @Override public int read() throws IOException {
      if (reset) {
        if (pos == size) {
          return delegate.read();
        }
        else {
          return buf[pos++] & 0xff;
        }
      }
      else {
        if (buf.length == size) throw new IOException();
        final int n = delegate.read(buf, size, 1);
        if (n == -1) return -1;
        final byte b = buf[pos];
        pos += n;
        size += n;
        return b & 0xff;
      }
    }

    @Override public int read(final byte[] b) throws IOException {
      return read(b, 0, b.length);
    }

    @Override public int read(final byte[] b, final int off, final int len) throws IOException {
      if (reset) {
        if (pos == size) {
          return delegate.read(b, off, len);
        }
        else {
          final int n = Math.min(len, buf.length - size);
          System.arraycopy(buf, pos, b, off, n);
          pos += n;
          return n;
        }
      }
      else {
        if (buf.length == size) throw new IOException();
        final int n = delegate.read(buf, pos, Math.min(len, buf.length - size));
        if (n == -1) return -1;
        System.arraycopy(buf, pos, b, off, n);
        pos += n;
        size += n;
        return n;
      }
    }

    @Override public int available() throws IOException {
      if (reset) {
        if (pos == size) {
          return delegate.available();
        }
        else {
          return size - pos;
        }
      }
      else {
        return delegate.available();
      }
    }

    @Override public void close() throws IOException {
      delegate.close();
    }

    @Override public synchronized void mark(final int readlimit) {}

    @Override public synchronized void reset() throws IOException {
      if (reset) throw new IOException();
      pos = 0;
      reset = true;
    }

    @Override public boolean markSupported() {
      return false;
    }

  }

}
