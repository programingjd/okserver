package info.jdavid.ok.server.header;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;
import okio.Source;
import okio.Timeout;


/**
 * Accept-Ranges header.
 */
public final class AcceptRanges {

  private AcceptRanges() {}

  /**
   * Accept-Ranges header field name. Used by the server to specify that range requests are supported.
   */
  public static final String HEADER = "Accept-Ranges";

  /**
   * Content-Range header field name. Used by the server to specify the range returned.
   */
  public static final String CONTENT_RANGE = "Content-Range";

  public static final String BYTES = "bytes";

  public static final String RANGE = "Range";

  public static final String IF_RANGE = "If-Range";

  public static final MediaType MULTIPART_TYPE = MediaType.parse("multipart/byteranges");

  public static class ByteRangesBody extends ResponseBody {

    final String boundary;
    final List<Part> parts;
    final long length;

    ByteRangesBody(final ByteString boundary, final List<Part> parts) {
      if (parts.isEmpty()) throw new IllegalArgumentException();
      this.boundary = boundary.utf8();
      this.parts = parts;
      long n = 0;
      for (final Part part: parts) {
        n += part.size;
      }
      length = n;
    }

    @Override public MediaType contentType() {
      return MediaType.parse(MULTIPART_TYPE.toString() + "; boundary=" + boundary);
    }

    @Override public long contentLength() {
      return length;
    }

    @Override public BufferedSource source() {
      return Okio.buffer(new PartsSource(parts));
    }

    public static class Builder {

      private static final ByteString SLASH = ByteString.encodeUtf8("/");
      private static final ByteString DASH = ByteString.encodeUtf8("-");
      private static final ByteString DASHES = ByteString.encodeUtf8("--");
      private static final ByteString CRLF = ByteString.encodeUtf8("\r\n");
      private static final ByteString CONTENT_TYPE_PREFIX = ByteString.encodeUtf8("Content-Type: ");
      private static final ByteString CONTENT_RANGE_PREFIX = ByteString.encodeUtf8("Content-Range: bytes ");

      final MediaType contentType;
      final List<Part> parts = new ArrayList<Part>(8);
      final ByteString boundary;

      public Builder(final MediaType contentType) {
        this(contentType, null);
      }

      public Builder(final MediaType contentType, final String boundary) {
        this.contentType = contentType;
        this.boundary = ByteString.encodeUtf8(boundary == null ? UUID.randomUUID().toString() : boundary);
      }

      public void addRange(final Source source, final long start, final long end, final long total) {
        final Buffer buffer = new Buffer();
        buffer.write(CRLF);
        buffer.write(DASHES);
        buffer.write(boundary);
        buffer.write(CRLF);
        buffer.write(CONTENT_TYPE_PREFIX);
        buffer.writeUtf8(contentType.toString());
        buffer.write(CRLF);
        buffer.write(CONTENT_RANGE_PREFIX);
        buffer.writeUtf8(String.valueOf(start));
        buffer.write(DASH);
        buffer.writeUtf8(String.valueOf(end));
        buffer.write(SLASH);
        buffer.writeUtf8(String.valueOf(total));
        buffer.write(CRLF);
        buffer.write(CRLF);
        parts.add(new Part(buffer, buffer.size()));
        parts.add(new Part(source, end - start));
      }

      public ByteRangesBody build() {
        final Buffer buffer = new Buffer();
        buffer.write(CRLF);
        buffer.write(DASHES);
        buffer.write(boundary);
        buffer.write(DASHES);
        buffer.write(CRLF);
        parts.add(new Part(buffer, buffer.size()));
        return new ByteRangesBody(boundary, parts);
      }

    }

    static class PartsSource implements Source {

      final Timeout timeout = new Timeout();
      final ListIterator<Part> iterator;
      private Part part;
      private long pos = 0L;

      PartsSource(final List<Part> parts) {
        if (parts.isEmpty()) throw new IllegalArgumentException();
        iterator = parts.listIterator();
        part = iterator.next();
      }

      @Override public long read(final Buffer sink, final long byteCount) throws IOException {
        if (part == null) return -1L;
        final long n = Math.min(byteCount, part.size - pos);
        sink.write(part.source, n);
        pos += n;
        if (pos == part.size) {
          part.close();
          pos = 0L;
          part = iterator.hasNext() ? iterator.next() : null;
        }
        return n;
      }

      @Override public Timeout timeout() {
        return timeout;
      }

      @Override public void close() {
        while (part != null) {
          try {
            part.close();
          }
          catch (final IOException ignore) {}
          part = iterator.hasNext() ? iterator.next() : null;
        }
      }

    }

    static class Part {

      final Source source;
      final long size;

      private Part(final Source source, final long size) {
        this.source = source;
        this.size = size;
      }

      public void close() throws IOException {
        source.close();
      }

    }

  }

}
