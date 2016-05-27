package info.jdavid.ok.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import okhttp3.internal.http.StatusLine;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;


@SuppressWarnings({ "unused", "WeakerAccess" })
public abstract class Response {

  private final Protocol protocol;
  private final int code;
  private final String message;
  private final Headers headers;
  private final ResponseBody body;

  private Response(final Builder builder) {
    this(builder.protocol, builder.code, builder.message, builder.headers.build(), builder.body);
  }

  private Response(final Protocol protocol, final int code, final String message,
                   final Headers headers, final ResponseBody body) {
    this.protocol = protocol;
    this.code = code;
    this.message = message;
    this.headers = headers;
    this.body = body;
  }

  public Protocol protocol() {
    return protocol;
  }

  public int code() {
    return code;
  }

  public boolean isSuccessful() {
    return code >= 200 && code < 300;
  }

  public String message() {
    return message;
  }

  List<String> headers(String name) {
    return headers.values(name);
  }

  public String header(String name) {
    return header(name, null);
  }

  public String header(String name, String defaultValue) {
    String result = headers.get(name);
    return result != null ? result : defaultValue;
  }

  Headers headers() {
    return headers;
  }

  ResponseBody body() {
    return body;
  }

  public Builder newBuilder() {
    throw new UnsupportedOperationException();
  }

  abstract void writeBody(final BufferedSource in, final BufferedSink out,
                          final Socket socket) throws IOException;

  @Override public String toString() {
    return "Response{protocol="
           + protocol
           + ", code="
           + code
           + ", message="
           + message
           + '}';
  }

  public static final class Builder {

    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String CONTENT_TYPE = "Content-Type";
    private Protocol protocol = null;
    private int code = -1;
    private String message = null;
    private ResponseBody body = null;
    private Headers.Builder headers;

    public Builder() {
      headers = new Headers.Builder();
    }

    private Builder(final Response response) {
      this.protocol = response.protocol;
      this.code = response.code;
      this.message = response.message;
      this.body = response.body;
      this.headers = response.headers.newBuilder();
    }

    public Builder statusLine(final StatusLine statusLine) {
      this.protocol = statusLine.protocol;
      this.code = statusLine.code;
      this.message = statusLine.message;
      return this;
    }

    public Builder noCache(final String etag) {
      if (etag != null) headers.set("ETag", etag);
      headers.set("Cache-Control", "no-cache");
      return this;
    }

    public Builder noStore() {
      headers.removeAll("ETag").set("Cache-Control", "no-store");
      return this;
    }

    public Builder etag(final String etag) {
      if (etag == null) {
        headers.removeAll("ETag");
      }
      else {
        headers.set("ETag", etag);
      }
      return this;
    }

    public Builder priv() {
      headers.add("Cache-Control", "private");
      return this;
    }

    public Builder maxAge(final long secs) {
      headers.add("Cache-Control", "max-age=" + secs);
      return this;
    }

    public Builder header(final String name, final String value) {
      headers.set(name, value);
      return this;
    }

    public Builder addHeader(final String name, final String value) {
      headers.add(name, value);
      return this;
    }

    public Builder removeHeader(final String name) {
      headers.removeAll(name);
      return this;
    }

    public Builder headers(final Headers headers) {
      this.headers = headers.newBuilder();
      return this;
    }

    public Builder contentLength(final long length) {
      headers.set(CONTENT_LENGTH, String.valueOf(length));
      return this;
    }

    public Builder contentType(final MediaType contentType) {
      headers.set(CONTENT_TYPE, contentType.toString());
      return this;
    }

    public Builder noBody() {
      this.body = null;
      contentLength(0);
      return this;
    }

    public Builder body(final ResponseBody body) {
      this.body = body;
      if (body == null) {
        contentLength(0);
      }
      else {
        contentType(body.contentType());
        contentLength(body.contentLength());
      }
      return this;
    }

    public Builder body(final String text) {
      return body(text, MediaTypes.TEXT);
    }

    public Builder body(final String text, final MediaType contentType) {
      if (text == null) return body((ResponseBody)null);
      final Buffer buffer = new Buffer().writeUtf8(text);
      this.body = new BufferResponse(contentType, buffer);
      contentLength(buffer.size());
      contentType(contentType);
      return this;
    }

    public Response build() {
      if (protocol == null) {
        throw new IllegalStateException("protocol == null");
      }
      if (code < 0) {
        throw new IllegalStateException("code < 0: " + code);
      }
      if (message == null) {
        throw new IllegalStateException("message == null");
      }
      return new SyncResponse(this);
    }
  }

  public static class SSE extends Response {

    /**
     * SSE Event loop responsible for sending events.
     */
    public static interface EventLoop {

      /**
       * Event loop step. This method should figure out if an event should be sent, and if so, create it and
       * write it (with Body.writeEventData), and then return how much time to wait before running this
       * step again. If a negative delay is returned, the loop will be stopped.
       * @param body the SSE body to write to.
       * @return the delay before running the loop step again.
       */
      public int loop(final Body body);

    }

    /**
     * SSE Event source responsive for sending events.
     */
    public static interface EventSource {

      /**
       * Registers the sse body for new message notifications.
       * The event source should not send any message after the body has been closed.
       * @param body the see body to register.
       */
      public void connect(final Body body);

    }

    public static class DefaultEventSource implements EventSource {
      private final Collection<Body> mBodies = Collections.synchronizedCollection(new ArrayList<Body>());
      @Override final public void connect(Body body) {
        mBodies.add(body);
      }
      public final void write(final String data) {
        final Iterator<Body> i = mBodies.iterator();
        while (i.hasNext()) {
          final Body body = i.next();
          if (body.isStopped()) {
            i.remove();
          }
          else {
            body.writeEventData(data);
          }
        }
      }
      public final void end(final String data) {
        final Iterator<Body> i = mBodies.iterator();
        while (i.hasNext()) {
          final Body body = i.next();
          if (body.isStopped()) {
            i.remove();
          }
          else {
            body.writeEventData(data);
            body.stop();
            i.remove();
          }
        }
      }
      public final void end() {
        final Iterator<Body> i = mBodies.iterator();
        while (i.hasNext()) {
          final Body body = i.next();
          if (!body.isStopped()) {
            body.stop();
          }
          i.remove();
        }
      }
    }

    private final int mRetrySecs;
    private final EventSource mEventSource;
    /**
     * Creates an SSE response with the default retry delay and the specified event loop.
     * @param eventLoop the event loop.
     */
    public SSE(final EventLoop eventLoop) {
      this(5, eventLoop, 0);
    }

    /**
     * Creates an SSE response with the default retry delay and the specified event source.
     * @param eventSource the event source.
     */
    public SSE(final EventSource eventSource) {
      this(5, eventSource);
    }

    /**
     * Creates an SSE response with the specified retry delay and the specified event loop.
     * @param retrySecs the retry delay in seconds.
     * @param eventLoop the event loop.
     * @param initialDelay the initial delay before starting the event loop in seconds.
     */
    public SSE(final int retrySecs, final EventLoop eventLoop, final int initialDelay) {
      this(
        retrySecs,
         new EventSource() {
          @Override public void connect(final Body body) {
            new Thread() {
              public void run() {
                int delay = initialDelay;
                while (true) {
                  if (delay < 0) {
                    body.stop();
                    break;
                  }
                  else if (delay > 0) {
                    try { Thread.sleep(delay * 1000); } catch (final InterruptedException ignore) {}
                  }
                  delay = eventLoop.loop(body);
                }
              }
            }.start();
          }
        }
      );
    }

    /**
     * Creates an SSE response with the specified retry delay and the specified event source.
     * @param retrySecs the retry delay in seconds.
     * @param eventSource the event source.
     */
    public SSE(final int retrySecs, final EventSource eventSource) {
      super(
        Protocol.HTTP_1_1,
        StatusLines.OK.code,
        StatusLines.OK.message,
        new Headers.Builder().
          set("Content-Type", MediaTypes.SSE.toString()).
          set("Cache-Control", "no-cache").
          set("Connection", "keep-alive").
          set("Access-Control-Allow-Origin", "*").
          set("Access-Control-Allow-Methods", "GET").
          set("Access-Control-Allow-Headers", "Content-Type, Accept").
          build(),
        null
      );
      mRetrySecs = retrySecs;
      mEventSource = eventSource;
    }

    @Override
    void writeBody(final BufferedSource in, final BufferedSink out, final Socket socket) throws IOException {
      out.writeUtf8("retry: " + mRetrySecs + "\n").flush();
      mEventSource.connect(new Body(in, out, socket));
    }

    public final class Body extends ResponseBody {

      private final BufferedSource in;
      private final BufferedSink out;
      private final Socket socket;

      private Body(final BufferedSource in, final BufferedSink out, final Socket socket) {
        this.in = in;
        this.out = out;
        this.socket = socket;
      }
      @Override public MediaType contentType() { return MediaTypes.SSE; }
      @Override public long contentLength() { return -1; }
      @Override public BufferedSource source() { return null; }

      /**
       * Writes an event data to the stream.
       * @param data the event data.
       */
      public void writeEventData(final String data) {
        //mBuffer.writeUtf8("data: " + data + "\n\n").flush();
        try {
          out.writeUtf8("data: " + data + "\n\n").flush();
        }
        catch (final IOException e) {
          stop();
        }
      }

      /**
       * Stops the SSE stream.
       */
      public void stop() {
        try { in.close(); } catch (final IOException ignore) {}
        try { out.close(); } catch (final IOException ignore) {}
        try { socket.close(); } catch (final IOException ignore) {}
      }

      /**
       * Returns whether the SSE stream has been stopped and no more events should be sent.
       * @return true if the stream has been stopped, false if it is still running.
       */
      public boolean isStopped() {
        return socket.isClosed();
      }

    }
  }

  private static class SyncResponse extends Response {
    private SyncResponse(final Builder builder) {
      super(builder);
    }
    @Override
    void writeBody(final BufferedSource in, final BufferedSink out, final Socket socket) throws IOException {
      try {
        final ResponseBody data = body();
        if (data != null) {
          final long length = data.contentLength();
          if (length > 0) {
            out.write(data.source(), data.contentLength());
            out.flush();
          }
          else if (length < 0) {
            final Buffer buffer = new Buffer();
            final long step = 65536;
            long read;
            while ((read = data.source().read(buffer, step)) > -1) {
              buffer.flush();
              out.write(buffer, read);
              out.flush();
            }
          }
        }
      }
      finally {
        try { in.close(); } catch (final IOException ignore) {}
        try { out.close(); } catch (final IOException ignore) {}
        try { socket.close(); } catch (final IOException ignore) {}
      }
    }
    @Override
    public Builder newBuilder() {
      return new Builder(this);
    }
  }

  static final class BufferResponse extends ResponseBody {
    public MediaType contentType;
    public Buffer buffer;
    BufferResponse(final MediaType contentType, final Buffer buffer) {
      this.contentType = contentType;
      this.buffer = buffer;
    }
    @Override public MediaType contentType() { return contentType; }
    @Override public long contentLength() { return buffer.size(); }
    @Override public BufferedSource source() { return buffer; }
  }
}
