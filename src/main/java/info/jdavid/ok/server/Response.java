package info.jdavid.ok.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import okhttp3.internal.http.StatusLine;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * HTTP Response object.
 */
@SuppressWarnings({ "unused", "WeakerAccess" })
public abstract class Response {

  private final Protocol protocol;
  private final int code;
  private final String message;
  private final Headers headers;
  private final ResponseBody body;
  private final ResponseBody[] chunks;
  private final List<HttpUrl> push;

  private Response(final Builder builder) {
    this(builder.mProtocol, builder.mCode, builder.mMessage, builder.mHeaders.build(), builder.mBody,
         builder.mChunks, builder.mPush);
  }

  private Response(final Protocol protocol, final int code, final String message,
                   final Headers headers, final ResponseBody body, final ResponseBody[] chunks,
                   final List<HttpUrl> push) {
    this.protocol = protocol;
    this.code = code;
    this.message = message;
    this.headers = headers;
    this.body = body;
    this.chunks = chunks;
    this.push = push;
  }

  /**
   * Returns the protocol (HTTP 1.0 or 1.1).
   * @return the protocol.
   */
  public Protocol protocol() {
    return protocol;
  }

  /**
   * Resturns the response code.
   * @return the code.
   */
  public int code() {
    return code;
  }

  /**
   * Returns whether the request was successful or not.
   * @return true for success, false for failure.
   */
  public boolean isSuccessful() {
    return code >= 200 && code < 300;
  }

  /**
   * Returns the response message.
   * @return the message.
   */
  public String message() {
    return message;
  }

  /**
   * Returns the values for the specified header name.
   * @param name the header name.
   * @return the list of values.
   */
  public List<String> headers(String name) {
    return headers.values(name);
  }

  /**
   * Returns the value for the specified header name.
   * @param name the header name.
   * @return the header value.
   */
  public String header(String name) {
    return header(name, null);
  }

  /**
   * Returns the value for the specified header name, or the specified default value is the header isn't set.
   * @param name the header name.
   * @param defaultValue the default value.
   * @return the header name.
   */
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

  ResponseBody[] chunks() {
    return chunks;
  }

  List<HttpUrl> pushUrls() {
    return push;
  }

  abstract void writeBody(final BufferedSource in, final BufferedSink out) throws IOException;

  @Override public String toString() {
    return "Response{protocol="
           + protocol
           + ", code="
           + code
           + ", message="
           + message
           + '}';
  }

  /**
   * Builder for the Response class.
   */
  public static final class Builder {

    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String LOCATION = "Location";
    private static final String TRANSFER_ENCODING = "Transfer-Encoding";
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String ETAG = "Etag";
    private static final String CORS_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String CORS_ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String CORS_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String CORS_MAX_AGE = "Access-Control-Max-Age";
    private static final String CORS_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    private Protocol mProtocol = null;
    private int mCode = -1;
    private String mMessage = null;
    private ResponseBody mBody = null;
    private ResponseBody[] mChunks = null;
    private Headers.Builder mHeaders;
    private List<HttpUrl> mPush;

    /**
     * Creates a new Builder.
     */
    public Builder() {
      mHeaders = new Headers.Builder();
      mPush = null;
    }

    /**
     * Creates a new Builder, using an existing response as a template.
     * @param response the response template.
     */
    private Builder(final Response response) {
      mProtocol = response.protocol;
      mCode = response.code;
      mMessage = response.message;
      mBody = response.body;
      mHeaders = response.headers.newBuilder();
      if (response.push == null) {
        mPush = null;
      }
      else {
        mPush = new ArrayList<HttpUrl>(response.push);
      }
    }

    /**
     * Sets the status line.
     * @param statusLine the status line.
     * @return this
     */
    public Builder statusLine(final StatusLine statusLine) {
      this.mProtocol = statusLine.protocol;
      this.mCode = statusLine.code;
      this.mMessage = statusLine.message;
      return this;
    }

    /**
     * Sets the etag (optional) and the cache control header to no-cache.
     * @param etag the etag (optional).
     * @return this
     */
    public Builder noCache(final String etag) {
      if (etag != null) mHeaders.set(ETAG, etag);
      mHeaders.set(CACHE_CONTROL, "no-cache");
      return this;
    }

    /**
     * Sets the cache control header to no-store and removes the etag.
     * @return this
     */
    public Builder noStore() {
      mHeaders.removeAll(ETAG).set(CACHE_CONTROL, "no-store");
      return this;
    }

    /**
     * Sets the etag.
     * @param etag the etag.
     * @return this
     */
    public Builder etag(final String etag) {
      if (etag == null) {
        mHeaders.removeAll(ETAG);
      }
      else {
        mHeaders.set(ETAG, etag);
      }
      return this;
    }

    /**
     * Sets the cache control header to private.
     * @return this
     */
    public Builder priv() {
      mHeaders.add(CACHE_CONTROL, "private");
      return this;
    }

    /**
     * Sets the cache control max-age value.
     * @param secs the max-age value in seconds.
     * @return this
     */
    public Builder maxAge(final long secs) {
      mHeaders.add(CACHE_CONTROL, "max-age=" + secs);
      return this;
    }

    /**
     * Sets the CORS headers for allowing cross domain requests.
     * @param origin the origin.
     * @param methods the methods.
     * @param headers the headers.
     * @param secs the max-age for the cors headers.
     * @return this
     */
    public Builder cors(final String origin, final List<String> methods, final List<String> headers,
                        final long secs) {
      this.mHeaders.set(CORS_ALLOW_ORIGIN, origin == null ? "null" : origin);
      if (methods != null) {
        this.mHeaders.set(CORS_ALLOW_METHODS, methods.isEmpty() ? "null" : join(methods));
      }
      if (headers != null) {
        this.mHeaders.set(CORS_ALLOW_HEADERS, headers.isEmpty() ? "null" : join(headers));
      }
      this.mHeaders.set(CORS_EXPOSE_HEADERS, join(Arrays.asList(ETAG, STRICT_TRANSPORT_SECURITY)));
      this.mHeaders.set(CORS_MAX_AGE, String.valueOf(secs));
      return this;
    }

    /**
     * Sets the CORS headers for allowing cross domain requests.
     * @param origin the origin.
     * @param methods the methods.
     * @param headers the headers.
     * @return this
     */
    public Builder cors(final String origin, final List<String> methods, final List<String> headers) {
      return cors(origin, methods, headers, 31536000);
    }

    /**
     * Sets the CORS headers for allowing cross domain requests.
     * @param origin the origin.
     * @param methods the methods.
     * @param secs the max-age for the cors headers.
     * @return this
     */
    public Builder cors(final String origin, final List<String> methods, final long secs) {
      return cors(origin, methods, null, secs);
    }

    /**
     * Sets the CORS headers for allowing cross domain requests.
     * @param origin the origin.
     * @param methods the methods.
     * @return this
     */
    public Builder cors(final String origin, final List<String> methods) {
      return cors(origin, methods, null, 31536000);
    }

    /**
     * Sets the CORS headers for allowing cross domain requests.
     * @param origin the origin.
     * @param secs the max-age for the cors headers.
     * @return this
     */
    public Builder cors(final String origin, final long secs) {
      return cors(origin, Collections.singletonList("GET"), null, secs);
    }

    /**
     * Sets the CORS headers for allowing cross domain requests.
     * @param origin the origin.
     * @return this
     */
    public Builder cors(final String origin) {
      return cors(origin, Collections.singletonList("GET"), null, 31536000);
    }

    /**
     * Sets the CORS headers for allowing cross domain requests.
     * @return this
     */
    public Builder cors() {
      return cors("*", Collections.singletonList("GET"), null, 31536000);
    }

    /**
     * Sets the strict transport security header, with the specified max-age.
     * @param secs the max-age in seconds.
     * @return this
     */
    public Builder hsts(final long secs) {
      mHeaders.set(STRICT_TRANSPORT_SECURITY, "max-age=" + secs);
      return this;
    }

    /**
     * Sets the strict transport security header.
     * @return this
     */
    public Builder hsts() {
      return hsts(31536000);
    }

    /**
     * Sets the location header.
     * @param url the location.
     * @return this
     */
    public Builder location(final HttpUrl url) {
      mHeaders.set(LOCATION, url.toString());
      return this;
    }

    /**
     * Sets the location header.
     * @param path the location path.
     * @return this
     */
    public Builder location(final String path) {
      mHeaders.set(LOCATION, path);
      return this;
    }

    /**
     * Sets the value for the header with the specified name. It replaces any previous value set for the same
     * header name.
     * @param name the header name.
     * @param value the header value.
     * @return this
     */
    public Builder header(final String name, final String value) {
      mHeaders.set(name, value);
      return this;
    }

    /**
     * Adds the value for the header with the specified name. It doesn't replaces any previous value set for
     * the same header name.
     * @param name the header name.
     * @param value the header value.
     * @return this
     */
    public Builder addHeader(final String name, final String value) {
      mHeaders.add(name, value);
      return this;
    }

    /**
     * Removes all values for the specified header name.
     * @param name the header name.
     * @return this
     */
    public Builder removeHeader(final String name) {
      mHeaders.removeAll(name);
      return this;
    }

    /**
     * Sets the headers from a template. All previous headers set are removed.
     * @param headers the headers template.
     * @return this
     */
    public Builder headers(final Headers headers) {
      this.mHeaders = headers.newBuilder();
      return this;
    }

    /**
     * Sets the content length header to the specified length.
     * @param length the content length.
     * @return this
     */
    public Builder contentLength(final long length) {
      mHeaders.set(CONTENT_LENGTH, String.valueOf(length));
      return this;
    }

    /**
     * Sets the content type header to the specified media type.
     * @param contentType the media type.
     * @return this
     */
    public Builder contentType(final MediaType contentType) {
      mHeaders.set(CONTENT_TYPE, contentType.toString());
      return this;
    }

    /**
     * Sets an empty body.
     * @return this
     */
    public Builder noBody() {
      this.mBody = null;
      contentLength(0);
      return this;
    }

    /**
     * Sets the response body.
     * @param body the response body.
     * @return this
     */
    public Builder body(final ResponseBody body) {
      this.mBody = body;
      if (body == null) {
        contentLength(0);
        removeHeader(CONTENT_TYPE);
      }
      else {
        contentType(body.contentType());
        contentLength(body.contentLength());
      }
      return this;
    }

    /**
     * Sets a response body built from the specified text.
     * @param text the plain/text string value.
     * @return this
     */
    public Builder body(final String text) {
      return body(MediaTypes.TEXT, text);
    }

    /**
     * Sets a response body built from the specified text.
     * @param text the string value.
     * @param contentType the media type.
     * @return this
     * @deprecated
     */
    public Builder body(final String text, final MediaType contentType) {
      return body(contentType, text);
    }

    /**
     * Sets a response body built from the specified text.
     * @param contentType the media type.
     * @param text the string value.
     * @return this
     */
    public Builder body(final MediaType contentType, final String text) {
      if (text == null) return body((ResponseBody)null);
      final Buffer buffer = new Buffer().writeUtf8(text);
      return body(new BufferResponse(contentType, buffer));
    }

    /**
     * Sets the response chunks.
     * @param contentType the media type.
     * @param chunks the response body chunks.
     * @return this
     */
    public Builder chunks(final MediaType contentType, final ResponseBody... chunks) {
      return chunks(true, contentType, chunks);
    }

    private Builder chunks(final boolean checkCommonContentType, final MediaType contentType,
                           final ResponseBody... chunks) {
      if (chunks == null) {
        mChunks = null;
        removeHeader(TRANSFER_ENCODING);
        removeHeader(CONTENT_TYPE);
      }
      else {
        if (checkCommonContentType) {
          for (final ResponseBody chunk: chunks) {
            if (!chunk.contentType().equals(contentType)) {
              throw new IllegalArgumentException(
                "All chunks should have the same Content-Type: " + contentType
              );
            }
          }
        }
        mChunks = chunks;
        addHeader(TRANSFER_ENCODING, "chunked");
        removeHeader(CONTENT_LENGTH);
        if (contentType != null) {
          contentType(contentType);
        }
      }
      return this;
    }

    /**
     * Sets the response chunks built from the specified text chunks.
     * @param chunks the plain/text string values.
     * @return this
     */
    public Builder chunks(final String... chunks) {
      return chunks(MediaTypes.TEXT, chunks);
    }

    /**
     * Sets the response chunks built from the specified text chunks.
     * @param contentType the media type.
     * @param chunks the string values.
     * @return this
     */
    public Builder chunks(final MediaType contentType, final String... chunks) {
      if (chunks == null) return chunks(false, contentType, (ResponseBody)null);
      final int length = chunks.length;
      final ResponseBody[] bodies = new ResponseBody[length];
      for (int i=0; i<length; ++i) {
        final Buffer buffer = new Buffer().writeUtf8(chunks[i]);
        bodies[i] = new BufferResponse(contentType, buffer);
      }
      return chunks(false, contentType, bodies);
    }

    /**
     * Adds an url to send as a push stream on an HTTP 2 connection.
     * @param url the url of the content to push.
     * @return this
     */
    public Builder push(final HttpUrl url) {
      if (this.mPush == null) this.mPush = new ArrayList<HttpUrl>(4);
      this.mPush.add(url);
      return this;
    }

    /**
     * Builds the response.
     * @return the response.
     */
    public Response build() {
      if (mProtocol == null) {
        throw new IllegalStateException("The protocol should be specified.");
      }
      if (mCode < 0) {
        throw new IllegalStateException("The return code should is invalid: " + mCode + ".");
      }
      if (mMessage == null) {
        throw new IllegalStateException("The http message is missing.");
      }
      if (mChunks != null && mBody != null) {
        throw new IllegalStateException("Both body and chunks were specified.");
      }
      if (mChunks != null) {
        return new ChunkedResponse(this);
      }
      else {
        return new SyncResponse(this);
      }
    }

    private static String join(final List<String> list) {
      final StringBuilder str = new StringBuilder();
      boolean first = true;
      for (final String value: list) {
        if (first) {
          first = false;
        }
        else {
          str.append(", ");
        }
        str.append(value);
      }
      return str.toString();
    }
  }

  /**
   * SSE Response.
   */
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
        null,
        null,
        null
      );
      mRetrySecs = retrySecs;
      mEventSource = eventSource;
    }

    @Override
    void writeBody(final BufferedSource in, final BufferedSink out) throws IOException {
      out.writeUtf8("retry: " + mRetrySecs + "\n").flush();
      mEventSource.connect(new Body(in, out));
    }

    public final class Body extends ResponseBody {

      private final BufferedSource in;
      private final BufferedSink out;
      private boolean mClosed = false;

      private Body(final BufferedSource in, final BufferedSink out) {
        this.in = in;
        this.out = out;
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
        mClosed = true;
        try { if (in != null) in.close(); } catch (final IOException ignore) {}
        try { out.close(); } catch (final IOException ignore) {}
        //try { if (socket != null) socket.close(); } catch (final IOException ignore) {}
      }

      /**
       * Returns whether the SSE stream has been stopped and no more events should be sent.
       * @return true if the stream has been stopped, false if it is still running.
       */
      public boolean isStopped() {
        return mClosed; // || (socket != null && socket.isClosed());
      }

    }

  }

  private static class SyncResponse extends Response {
    private SyncResponse(final Builder builder) {
      super(builder);
    }
    @Override
    void writeBody(final BufferedSource in, final BufferedSink out) throws IOException {
      //noinspection EmptyFinallyBlock
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
//        try { in.close(); } catch (final IOException ignore) {}
//        try { out.close(); } catch (final IOException ignore) {}
//        try { socket.close(); } catch (final IOException ignore) {}
      }
    }
  }

  private static class ChunkedResponse extends Response {
    private ChunkedResponse(final Builder builder) {
      super(builder);
    }

    @Override
    void writeBody(final BufferedSource in, final BufferedSink out) throws IOException {
      //noinspection EmptyFinallyBlock
      try {
        final ResponseBody[] chunks = chunks();
        if (chunks != null) {
          for (final ResponseBody chunk: chunks) {
            final long length = chunk.contentLength();
            out.writeUtf8(Long.toHexString(length).toUpperCase(Locale.US));
            //out.writeUtf8("chunk-ext");
            out.writeUtf8("\r\n");
            if (length > 0) {
              out.write(chunk.source(), length);
            }
            out.writeUtf8("\r\n");
          }
          out.writeUtf8("0");
          //out.writeUtf8("chunk-ext");
          out.writeUtf8("\r\n");
          //out.writeUtf8("trailer-part");
          out.writeUtf8("\r\n");
        }
      }
      finally {
//        try { in.close(); } catch (final IOException ignore) {}
//        try { out.close(); } catch (final IOException ignore) {}
//        try { socket.close(); } catch (final IOException ignore) {}
      }
    }
  }

  static final class BufferResponse extends ResponseBody {
    public final MediaType contentType;
    public final Buffer buffer;
    BufferResponse(final MediaType contentType, final Buffer buffer) {
      this.contentType = contentType;
      this.buffer = buffer;
    }
    @Override public MediaType contentType() { return contentType; }
    @Override public long contentLength() { return buffer.size(); }
    @Override public BufferedSource source() { return buffer; }
  }

}
