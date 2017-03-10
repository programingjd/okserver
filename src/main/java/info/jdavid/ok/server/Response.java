package info.jdavid.ok.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
   * You should not reuse builder instances when a body (or chunks) have been set, because those
   * can only be read once.
   */
  @SuppressWarnings("UnusedReturnValue")
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
     * Sets a response body built from the specified bytes.
     * @param bytes the octet stream.
     * @return this
     */
    public Builder body(final byte[] bytes) {
      return body(MediaTypes.OCTET_STREAM, bytes);
    }

    /**
     * Sets a response body built from the specified bytes.
     * @param contentType the media type.
     * @param bytes the bytes.
     * @return this
     */
    public Builder body(final MediaType contentType, final byte[] bytes) {
      if (bytes == null) return body((ResponseBody)null);
      final Buffer buffer = new Buffer().write(bytes);
      return body(new BufferResponse(contentType, buffer));
    }

    /**
     * Sets a response body built from the specified bytes.
     * @param source the bytes.
     * @param size the byte size of the source.
     * @return this
     */
    public Builder body(final BufferedSource source, final int size) {
      if (source == null) return body((ResponseBody)null);
      if (size < 0) throw new IllegalArgumentException();
      return body(new BufferResponse(MediaTypes.OCTET_STREAM, source, size));
    }

    /**
     * Sets a response body built from the specified bytes.
     * @param contentType the media type.
     * @param source the bytes.
     * @param size the byte size of the source.
     * @return this
     */
    public Builder body(final MediaType contentType, final BufferedSource source, final int size) {
      if (source == null) return body((ResponseBody)null);
      if (size < 0) throw new IllegalArgumentException();
      return body(new BufferResponse(contentType, source, size));
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
     * Sets the response chunks built from the specified byte chunks.
     * @param contentType the media type.
     * @param chunks the bytes chunks.
     * @return this
     */
    public Builder chunks(final MediaType contentType, final byte[]... chunks) {
      if (chunks == null) return chunks(false, contentType, (ResponseBody)null);
      final int length = chunks.length;
      final ResponseBody[] bodies = new ResponseBody[length];
      for (int i=0; i<length; ++i) {
        final Buffer buffer = new Buffer().write(chunks[i]);
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

    private final List<Message> mQueue = new LinkedList<Message>();
    private Lock mLock = new ReentrantLock();
    private Condition isReady = mLock.newCondition();

    private static class Message {
      final String data;
      final Map<String, String> metadata;
      public Message(final String data, final Map<String, String> metadata) {
        this.data = data;
        this.metadata = metadata;
      }
    }

    public static final class EventSource {

      private SSE sse;

      private EventSource(final SSE sse) {
        this.sse = sse;
      }

      public void send(final String data) {
        send(data, null);
      }

      public void send(final String data, final Map<String, String> metadata) {
        if (sse == null) throw new IllegalStateException();
        if (data == null) throw new NullPointerException();
        if (metadata != null) {
          for (final Map.Entry<String, String> entry: metadata.entrySet()) {
            final String key = entry.getKey();
            if (key == null) throw new NullPointerException();
            final int n = key.length();
            for (int i=0; i<n; ++i) {
              final char c = key.charAt(i);
              if (c == ':' || c == '\n' || c == '\t') throw new IllegalArgumentException();
            }
          }
        }
        sse.mLock.lock();
        try {
          sse.mQueue.add(new Message(data, metadata));
          sse.isReady.signal();
        }
        finally {
          sse.mLock.unlock();
        }
      }

      public void close() {
        if (sse == null) return;
        sse.mLock.lock();
        try {
          sse.mQueue.add(null);
          sse.isReady.signal();
        }
        finally {
          sse.mLock.unlock();
        }
        sse = null;
      }

    }

    private final int mRetrySecs;
    private final EventSource mEventSource = new EventSource(this);

    /**
     * Creates an SSE response with the default retry delay.
     */
    public SSE() {
      this(5);
    }

    /**
     * Creates an SSE response with the specified retry delay.
     * @param retrySecs the retry delay in seconds.
     */
    public SSE(final int retrySecs) {
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
    }

    public EventSource getEventSource() {
      return mEventSource;
    }

    @Override
    void writeBody(final BufferedSource in, final BufferedSink out) throws IOException {
      out.writeUtf8("retry: " + mRetrySecs + "\n").flush();
      while (true) {
        mLock.lock();
        try {
          if (mQueue.size() > 0 || isReady.await(10000L, TimeUnit.MILLISECONDS)) {
            System.out.println("ok");
            if (mQueue.size() == 0) continue;
            final Message message = mQueue.remove(0);
            if (message == null) break;
            final Map<String, String> metadata = message.metadata;
            if (metadata != null) {
              for (final Map.Entry<String, String> entry: metadata.entrySet()) {
                out.writeUtf8(entry.getKey() + ": " + entry.getValue() + "\n\n");
              }
            }
            final String data = message.data;
            out.writeUtf8("data: " + data + "\n\n").flush();
          }
          else {
            System.out.println("next");
            out.writeUtf8(":\n\n").flush();
          }
        }
        catch (final InterruptedException ignore) {
          mEventSource.close();
          break;
        }
        finally {
          mLock.unlock();
        }
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
            out.flush();
          }
          out.writeUtf8("0");
          //out.writeUtf8("chunk-ext");
          out.writeUtf8("\r\n");
          out.flush();
          //out.writeUtf8("trailer-part");
          out.writeUtf8("\r\n");
          out.flush();
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
    public final BufferedSource buffer;
    public final int size;
    BufferResponse(final MediaType contentType, final Buffer buffer) {
      this(contentType, buffer, (int)buffer.size());
    }
    BufferResponse(final MediaType contentType, final BufferedSource buffer, final int size) {
      this.contentType = contentType;
      this.buffer = buffer;
      this.size = size;
    }
    @Override public MediaType contentType() { return contentType; }
    @Override public long contentLength() { return size; }
    @Override public BufferedSource source() { return buffer; }
  }

}
