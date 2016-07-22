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

    private final InputStream mDelegate;
    private final byte mBuffer[] = new byte[4096];
    private int mBufferPosition = 0;
    private int mBufferSize = 0;
    private boolean mReset = false;

    ReplayableInputStream(final InputStream inputStream) {
      super();
      this.mDelegate = inputStream;
    }

    @Override public int read() throws IOException {
      if (mReset) {
        if (mBufferPosition == mBufferSize) {
          return mDelegate.read();
        }
        else {
          return mBuffer[mBufferPosition++] & 0xff;
        }
      }
      else {
        if (mBuffer.length == mBufferSize) throw new IOException();
        final int n = mDelegate.read(mBuffer, mBufferSize, 1);
        if (n == -1) return -1;
        final byte b = mBuffer[mBufferPosition];
        mBufferPosition += n;
        mBufferSize += n;
        return b & 0xff;
      }
    }

    @Override public int read(final byte[] b) throws IOException {
      return read(b, 0, b.length);
    }

    @Override public int read(final byte[] b, final int off, final int len) throws IOException {
      if (mReset) {
        if (mBufferPosition == mBufferSize) {
          return mDelegate.read(b, off, len);
        }
        else {
          final int n = Math.min(len, mBuffer.length - mBufferSize);
          System.arraycopy(mBuffer, mBufferPosition, b, off, n);
          mBufferPosition += n;
          return n;
        }
      }
      else {
        if (mBuffer.length == mBufferSize) throw new IOException();
        final int n = mDelegate.read(mBuffer, mBufferPosition, Math.min(len, mBuffer.length - mBufferSize));
        if (n == -1) return -1;
        System.arraycopy(mBuffer, mBufferPosition, b, off, n);
        mBufferPosition += n;
        mBufferSize += n;
        return n;
      }
    }

    @Override public int available() throws IOException {
      if (mReset) {
        if (mBufferPosition == mBufferSize) {
          return mDelegate.available();
        }
        else {
          return mBufferSize - mBufferPosition;
        }
      }
      else {
        return mDelegate.available();
      }
    }

    @Override public void close() throws IOException {
      mDelegate.close();
    }

    @Override public synchronized void mark(final int readlimit) {}

    @Override public synchronized void reset() throws IOException {
      if (mReset) throw new IOException();
      mBufferPosition = 0;
      mReset = true;
    }

    @Override public boolean markSupported() {
      return false;
    }

  }

}
