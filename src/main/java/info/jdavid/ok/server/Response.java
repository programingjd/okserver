package info.jdavid.ok.server;

import java.io.IOException;
import java.util.List;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.http.StatusLine;
import okio.Buffer;
import okio.BufferedSource;


@SuppressWarnings("unused")
public final class Response {

  private final Protocol protocol;
  private final int code;
  private final String message;
  private final Headers headers;
  private final ResponseBody body;

  private Response(final Builder builder) {
    this.protocol = builder.protocol;
    this.code = builder.code;
    this.message = builder.message;
    this.headers = builder.headers.build();
    this.body = builder.body;
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

  public List<String> headers(String name) {
    return headers.values(name);
  }

  public String header(String name) {
    return header(name, null);
  }

  public String header(String name, String defaultValue) {
    String result = headers.get(name);
    return result != null ? result : defaultValue;
  }

  public Headers headers() {
    return headers;
  }

  public ResponseBody body() {
    return body;
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  @Override public String toString() {
    return "Response{protocol="
           + protocol
           + ", code="
           + code
           + ", message="
           + message
           + '}';
  }

  public static class Builder {

    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String CONTENT_TYPE = "Content-Type";
    private Protocol protocol;
    private int code = -1;
    private String message;
    private Headers.Builder headers;
    private ResponseBody body;

    public Builder() {
      headers = new Headers.Builder();
    }

    private Builder(final Response response) {
      this.protocol = response.protocol;
      this.code = response.code;
      this.message = response.message;
      this.headers = response.headers.newBuilder();
      this.body = response.body;
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
        long contentLength = -1;
        try {
          contentLength = body.contentLength();
        }
        catch (final IOException ignore) {}
        contentLength(contentLength);
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
      return new Response(this);
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
