package info.jdavid.ok.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Sink;
import okio.Timeout;

@SuppressWarnings("unused")
/**
 * SSE Event Stream body implementation.
 */
public class SSEBody extends ResponseBody {

  /**
   * SSE Event loop responsible for sending events.
   */
  public static interface EventLoop {

    /**
     * Event loop step. This method should figure out if an event should be sent, and if so, create it and
     * write it (with SSEBody.writeEventData), and then return how much time to wait before running this
     * step again. If a negative delay is returned, the loop will be stopped.
     * @return the delay before running the loop step again.
     */
    public int loop(final SSEBody body);

  }

  private final Buffer mBuffer;
  private final SSESource mSource;

  /**
   * Creates an SSE body with the default retry delay and the specified event loop.
   * @param eventLoop the event loop.
   */
  public SSEBody(final EventLoop eventLoop) {
    this(5, eventLoop, 0);
  }

  /**
   * Creates an SSE body with the specified retry delay and the specified event loop.
   * @param retrySecs the retry delay in seconds.
   * @param eventLoop the event loop.
   * @param initialDelay the initial delay before starting the event loop in seconds.
   */
  public SSEBody(final int retrySecs, final EventLoop eventLoop, final int initialDelay) {
    super();
    mBuffer = new Buffer();
    mSource = new SSESource(mBuffer);
    mBuffer.writeUtf8("retry: " + retrySecs + "\n");
    new Thread() {
      public void run() {
        int delay = initialDelay;
        while (true) {
          if (delay < 0) {
            mSource.stop();
            break;
          }
          else if (delay > 0) {
            try { Thread.sleep(delay * 1000); } catch (final InterruptedException ignore) {}
          }
          delay = eventLoop.loop(SSEBody.this);
        }
      }
    }.start();
  }
  @Override public MediaType contentType() { return MediaTypes.SSE; }
  @Override public long contentLength() { return -1; }
  @Override public BufferedSource source() { return mSource; }

  /**
   * Writes an event data to the stream.
   * @param data the event data.
   */
  public void writeEventData(final String data) {
    mBuffer.writeUtf8("data: " + data + "\n\n").flush();
  }

  private static class SSESource implements BufferedSource {
    public boolean mStopped = false;
    public final Buffer mBuffer;
    public SSESource(final Buffer buffer) {
      this.mBuffer = buffer;
    }
    public void stop() {
      mStopped = true;
    }
    @Override public Buffer buffer() { return mBuffer; }
    @Override public boolean exhausted() { return mStopped && mBuffer.exhausted(); }
    @Override public void require(final long byteCount) throws IOException {
      if (!request(byteCount)) throw new EOFException();
    }
    @Override public boolean request(final long byteCount) { return true; }
    @Override public byte readByte() throws IOException { return mBuffer.readByte(); }
    @Override public short readShort() throws IOException { return mBuffer.readShort(); }
    @Override public short readShortLe() throws IOException { return mBuffer.readShortLe(); }
    @Override public int readInt() throws IOException { return mBuffer.readInt(); }
    @Override public int readIntLe() throws IOException { return mBuffer.readIntLe(); }
    @Override public long readLong() throws IOException { return mBuffer.readLong(); }
    @Override public long readLongLe() throws IOException { return mBuffer.readLongLe(); }
    @Override public long readDecimalLong() throws IOException { return mBuffer.readDecimalLong(); }
    @Override public long readHexadecimalUnsignedLong() throws IOException {
      return mBuffer.readHexadecimalUnsignedLong();
    }
    @Override public void skip(long byteCount) throws IOException { mBuffer.skip(byteCount); }
    @Override public ByteString readByteString() throws IOException { return mBuffer.readByteString(); }
    @Override public ByteString readByteString(long byteCount) throws IOException {
      return mBuffer.readByteString(byteCount);
    }
    @Override public byte[] readByteArray() throws IOException { return mBuffer.readByteArray(); }
    @Override public byte[] readByteArray(long byteCount) throws IOException {
      return mBuffer.readByteArray(byteCount);
    }
    @Override public int read(byte[] sink) throws IOException { return Math.max(0, mBuffer.read(sink)); }
    @Override public void readFully(byte[] sink) throws IOException { mBuffer.readFully(sink); }
    @Override public int read(byte[] sink, int offset, int byteCount) throws IOException {
      return Math.max(mStopped ? -1 : 0, mBuffer.read(sink, offset, byteCount));
    }
    @Override public void readFully(Buffer sink, long byteCount) throws IOException {
      mBuffer.readFully(sink, byteCount);
    }
    @Override public long readAll(Sink sink) throws IOException {
      return Math.max(mStopped ? -1 : 0, mBuffer.readAll(sink));
    }
    @Override public String readUtf8() throws IOException { return mBuffer.readUtf8(); }
    @Override public String readUtf8(long byteCount) throws IOException { return mBuffer.readUtf8(byteCount); }
    @Override public String readUtf8Line() throws IOException { return mBuffer.readUtf8Line(); }
    @Override public String readUtf8LineStrict() throws IOException { return mBuffer.readUtf8LineStrict(); }
    @Override public int readUtf8CodePoint() throws IOException { return mBuffer.readUtf8CodePoint(); }
    @Override public String readString(Charset charset) throws IOException {
      return mBuffer.readString(charset);
    }
    @Override public String readString(long byteCount, Charset charset) throws IOException {
      return mBuffer.readString(byteCount, charset);
    }
    @Override public long indexOf(byte b) throws IOException { return mBuffer.indexOf(b); }
    @Override public long indexOf(byte b, long fromIndex) throws IOException {
      return mBuffer.indexOf(b, fromIndex);
    }
    @Override public long indexOf(ByteString bytes) throws IOException { return mBuffer.indexOf(bytes); }
    @Override public long indexOf(ByteString bytes, long fromIndex) throws IOException {
      return mBuffer.indexOf(bytes, fromIndex);
    }
    @Override public long indexOfElement(ByteString targetBytes) throws IOException {
      return mBuffer.indexOfElement(targetBytes);
    }
    @Override public long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException {
      return mBuffer.indexOfElement(targetBytes, fromIndex);
    }
    @Override public InputStream inputStream() { return mBuffer.inputStream(); }
    @Override public long read(Buffer sink, long byteCount) throws IOException {
      return Math.max(mStopped ? -1 : 0, mBuffer.read(sink, byteCount));
    }
    @Override public Timeout timeout() { return Timeout.NONE; }
    @Override public void close() throws IOException { mBuffer.close(); }
  }

}
