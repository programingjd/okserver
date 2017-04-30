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

import info.jdavid.ok.server.header.CacheControls;
import info.jdavid.ok.server.header.Cors;
import info.jdavid.ok.server.header.ETags;
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

  final Protocol protocol;
  final int code;
  final String message;
  final Headers headers;
  final ResponseBody body;
  final ResponseBody[] chunks;
  final List<HttpUrl> push;

  private Response(final Builder builder) {
    this(builder.protocol, builder.code, builder.message, builder.headers.build(), builder.body,
         builder.chunks, builder.push);
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

    Protocol protocol = null;
    int code = -1;
    String message = null;
    ResponseBody body = null;
    private ResponseBody[] chunks = null;
    private EventSource eventSource = null;
    private int sseRetrySecs = 5;
    private List<HttpUrl> push = null;
    private Headers.Builder headers = new Headers.Builder();

    /**
     * Creates a new Builder.
     */
    public Builder() {}

    /**
     * Sets the status line.
     * @param statusLine the status line.
     * @return this
     */
    public Builder statusLine(final StatusLine statusLine) {
      protocol = statusLine.protocol;
      code = statusLine.code;
      message = statusLine.message;
      return this;
    }

    /**
     * Gets the http response status code.
     * @return the status code.
     */
    public int code() {
      if (code == -1) throw new IllegalStateException("The status line has not been set.");
      return code;
    }

    /**
     * Sets the etag (optional) and the cache control header to no-cache.
     * @param etag the etag (optional).
     * @return this
     */
    public Builder noCache(final String etag) {
      if (etag != null) headers.set(ETags.HEADER, etag);
      headers.set(CacheControls.HEADER, "no-cache");
      return this;
    }

    /**
     * Sets the cache control header to no-store and removes the etag.
     * @return this
     */
    public Builder noStore() {
      headers.removeAll(ETags.HEADER).set(CacheControls.HEADER, "no-store");
      return this;
    }

    /**
     * Sets the etag.
     * @param etag the etag.
     * @return this
     */
    public Builder etag(final String etag) {
      if (etag == null) {
        headers.removeAll(ETags.HEADER);
      }
      else {
        headers.set(ETags.HEADER, etag);
      }
      return this;
    }

    /**
     * Sets the cache control header to private.
     * @return this
     */
    public Builder priv() {
      headers.add(CacheControls.HEADER, "private");
      return this;
    }

    /**
     * Sets the cache control max-age value.
     * @param secs the max-age value in seconds.
     * @param immutable the immutable attribute.
     * @return this
     */
    public Builder maxAge(final long secs, final boolean immutable) {
      headers.add(CacheControls.HEADER,
                   "max-age=" + secs + (immutable ? ", immutable" : ", must-revalidate"));
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
      this.headers.set(Cors.ALLOW_ORIGIN, origin == null ? "null" : origin);
      if (methods != null) {
        this.headers.set(Cors.ALLOW_METHODS, methods.isEmpty() ? "null" : join(methods));
      }
      if (headers != null) {
        this.headers.set(Cors.ALLOW_HEADERS, headers.isEmpty() ? "null" : join(headers));
      }
      this.headers.set(Cors.ALLOW_HEADERS, join(Arrays.asList(ETags.HEADER, STRICT_TRANSPORT_SECURITY)));
      this.headers.set(Cors.MAX_AGE, String.valueOf(secs));
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
      headers.set(STRICT_TRANSPORT_SECURITY, "max-age=" + secs);
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
      headers.set(LOCATION, url.toString());
      return this;
    }

    /**
     * Sets the location header.
     * @param path the location path.
     * @return this
     */
    public Builder location(final String path) {
      headers.set(LOCATION, path);
      return this;
    }

    /**
     * Gets the last value of the header with the specified name.
     * @param name the header name.
     * @return the last header value, or null if the header is absent.
     */
    public String header(final String name) {
      return headers.get(name);
    }

    /**
     * Gets the list of values for the header with the specified name.
     * @param name the header name.
     * @return the list of values, which can be empty.
     */
    public List<String> headers(final String name) {
      return headers.build().values(name);
    }

    /**
     * Sets the value for the header with the specified name. It replaces any previous value set for the same
     * header name.
     * @param name the header name.
     * @param value the header value.
     * @return this
     */
    public Builder header(final String name, final String value) {
      headers.set(name, value);
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
      headers.add(name, value);
      return this;
    }

    /**
     * Removes all values for the specified header name.
     * @param name the header name.
     * @return this
     */
    public Builder removeHeader(final String name) {
      headers.removeAll(name);
      return this;
    }

    /**
     * Sets the headers from a template. All previous headers set are removed.
     * @param headers the headers template.
     * @return this
     */
    public Builder headers(final Headers headers) {
      this.headers = headers.newBuilder();
      return this;
    }

    /**
     * Sets the content length header to the specified length.
     * @param length the content length.
     * @return this
     */
    public Builder contentLength(final long length) {
      headers.set(CONTENT_LENGTH, String.valueOf(length));
      return this;
    }

    /**
     * Sets the content type header to the specified media type.
     * @param contentType the media type.
     * @return this
     */
    public Builder contentType(final MediaType contentType) {
      headers.set(CONTENT_TYPE, contentType.toString());
      return this;
    }

    /**
     * Sets an empty body.
     * @return this
     */
    public Builder noBody() {
      this.body = null;
      contentLength(0);
      return this;
    }

    /**
     * Sets the response body.
     * @param body the response body.
     * @return this
     */
    public Builder body(final ResponseBody body) {
      this.body = body;
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
        this.chunks = null;
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
        this.chunks = chunks;
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
     * Sets the response event source (SSE response).
     * @param eventSource the event source.
     * @return this.
     */
    public Builder sse(final EventSource eventSource) {
      this.eventSource = eventSource;
      return this;
    }

    /**
     * Sets the response event source (SSE response).
     * @param eventSource the event source.
     * @param clientReconnectDelayInSeconds the reconnect delay in seconds for connected clients.
     * @return this.
     */
    public Builder sse(final EventSource eventSource, final int clientReconnectDelayInSeconds) {
      if (code == -1) statusLine(StatusLines.OK);
      this.eventSource = eventSource;
      sseRetrySecs = clientReconnectDelayInSeconds;
      return this;
    }

    /**
     * Adds an url to send as a push stream on an HTTP 2 connection.
     * @param url the url of the content to push.
     * @return this
     */
    public Builder push(final HttpUrl url) {
      if (this.push == null) this.push = new ArrayList<HttpUrl>(4);
      this.push.add(url);
      return this;
    }

    /**
     * Builds the response.
     * @return the response.
     */
    public Response build() {
      if (protocol == null) {
        throw new IllegalStateException("The protocol should be specified.");
      }
      if (code < 0) {
        throw new IllegalStateException("The return code should is invalid: " + code + ".");
      }
      if (message == null) {
        throw new IllegalStateException("The http message is missing.");
      }
      if (eventSource != null && body != null) {
        throw new IllegalStateException("Both body and event source were specified.");
      }
      if (eventSource != null && chunks != null) {
        throw new IllegalStateException("Both chunks and event source were specified.");
      }
      if (chunks != null && body != null) {
        throw new IllegalStateException("Both body and chunks were specified.");
      }
      if (chunks != null) {
        return new ChunkedResponse(this);
      }
      if (eventSource != null) {
        if (code != 200) {
          throw new IllegalStateException("SSE response should have a status code of 200 (OK).");
        }
        if (headers.get(CONTENT_LENGTH) != null) {
          throw new IllegalStateException("SSE response content length should not be set.");
        }
        final String contentType = headers.get(CONTENT_TYPE);
        if (contentType != null) {
          if (!contentType.startsWith(MediaTypes.SSE.toString())) {
            throw new IllegalStateException(
              "SSE response content type should be " + MediaTypes.SSE.toString()
            );
          }
        }
        else {
          headers.set(CONTENT_TYPE, MediaTypes.SSE.toString());
        }
        final String cache = headers.get(CacheControls.HEADER);
        if (cache != null) {
          if (!cache.equals("no-cache")) {
            throw new IllegalStateException("SSE response cache control should be set to no-cache.");
          }
        }
        else {
          headers.set(CacheControls.HEADER, "no-cache");
        }
        headers.set("Connection", "keep-alive");
        if (headers.get(Cors.ALLOW_ORIGIN) == null) {
          headers.set(Cors.ALLOW_ORIGIN, "*");
        }
        headers.set(Cors.ALLOW_METHODS, "GET");
        if (headers.get(Cors.ALLOW_HEADERS) == null) {
          headers.set(Cors.ALLOW_HEADERS, "Content-Type, Accept");
        }
        return new SSE(this);
      }
      return new SyncResponse(this);
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
   * Event Source for an SSE Response.
   */
  public static final class EventSource {

    private Lock lock = new ReentrantLock();
    List<SSE> responses = new ArrayList<SSE>();

    public EventSource() {}

    EventSource connect(final SSE sse) {
      lock.lock();
      try {
        responses.add(sse);
      }
      finally {
        lock.unlock();
      }
      return this;
    }

    /**
     * Sends a message with the specified data.
     * @param data the message data.
     */
    public void send(final String data) {
      send(data, null);
    }

    /**
     * Sends a message with the specified metadata and the specified data.
     * @param data the message data.
     * @param metadata the message metadata.
     */
    public void send(final String data, final Map<String, String> metadata) {
      if (data == null) throw new NullPointerException("The message data cannot be null.");
      if (metadata != null) {
        for (final Map.Entry<String, String> entry: metadata.entrySet()) {
          final String key = entry.getKey();
          if (key == null) throw new NullPointerException("The metadata key cannot be null.");
          final int n = key.length();
          for (int i=0; i<n; ++i) {
            final char c = key.charAt(i);
            if (c == ':' || c == '\n' || c == '\t') throw new IllegalArgumentException();
          }
        }
      }
      lock.lock();
      try {
        final List<SSE> responses = this.responses;
        for (final SSE sse: responses)  {
          sse.lock.lock();
          try {
            sse.queue.add(new SSE.Message(data, metadata));
            sse.isReady.signal();
          }
          finally {
            sse.lock.unlock();
          }
        }
      }
      finally {
        lock.unlock();
      }
    }

    public void close() {
      lock.lock();
      try {
        final List<SSE> responses = this.responses;
        for (final SSE sse: responses)  {
          sse.lock.lock();
          try {
            sse.queue.add(null);
            sse.isReady.signal();
          }
          finally {
            sse.lock.unlock();
          }
        }
      }
      finally {
        lock.unlock();
      }
    }

    void disconnect(final SSE sse) {
      lock.lock();
      try {
        responses.remove(sse);
      }
      finally {
        lock.unlock();
      }
    }

  }

  private static class SSE extends Response {

    private Lock lock = new ReentrantLock();
    private Condition isReady = lock.newCondition();
    final List<Message> queue = new LinkedList<Message>();

    static class Message {
      final String data;
      final Map<String, String> metadata;
      public Message(final String data, final Map<String, String> metadata) {
        this.data = data;
        this.metadata = metadata;
      }
    }

    final int retry;
    final EventSource eventSource;

    SSE(final Response.Builder builder) {
      super(builder);
      retry = builder.sseRetrySecs;
      eventSource = builder.eventSource.connect(this);
    }

    @Override
    void writeBody(final BufferedSource in, final BufferedSink out) throws IOException {
      out.writeUtf8("retry: " + retry + "\n").flush();
      try {
        while (true) {
          lock.lock();
          try {
            if (queue.size() > 0 || isReady.await(10000L, TimeUnit.MILLISECONDS)) {
              if (queue.size() == 0) continue;
              final Message message = queue.remove(0);
              if (message == null) break;
              final Map<String, String> metadata = message.metadata;
              if (metadata != null) {
                for (final Map.Entry<String, String> entry : metadata.entrySet()) {
                  out.writeUtf8(entry.getKey() + ": " + entry.getValue() + "\n\n");
                }
              }
              final String data = message.data;
              out.writeUtf8("data: " + data + "\n\n").flush();
            }
            else {
              out.writeUtf8(":\n\n").flush();
            }
          }
          catch (final InterruptedException ignore) {
            break;
          }
          finally {
            lock.unlock();
          }
        }
      }
      finally {
        eventSource.disconnect(this);
      }
    }

  }

  private static class SyncResponse extends Response {
    SyncResponse(final Builder builder) {
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
    ChunkedResponse(final Builder builder) {
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
    final MediaType contentType;
    final BufferedSource buffer;
    final int size;
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
