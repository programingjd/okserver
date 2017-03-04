package info.jdavid.ok.server;

import okhttp3.internal.http.StatusLine;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingTimeout;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.List;


//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ResponseBuilderTest {

  private Response.Builder response(final int code) {
    return new Response.Builder().statusLine(StatusLines.get(code));
  }

  @Test
  public void testMalformed() throws IOException {
    try {
      new Response.Builder().build();
      fail("Should have failed because no status line has been set.");
    }
    catch (final IllegalStateException ignore) {}
    try {
      new Response.Builder().statusLine(StatusLine.parse("HTTP1.1/200")).build();
      fail("Should have failed because no status message has been set.");
    }
    catch (final ProtocolException ignore) {}
    try {
      new Response.Builder().statusLine(StatusLine.parse("HTTP1.1/-999")).build();
      fail("Should have failed because invalid status code has been set.");
    }
    catch (final ProtocolException ignore) {}
    try {
      new Response.Builder().body("body").chunks("chunk1", "chunk2").build();
      fail("Should have failed because both body and chunks were specified.");
    }
    catch (final IllegalStateException ignore) {}
  }

  @Test
  public void testCode() throws IOException {
    assertNull(StatusLines.get(999));
    assertNull(StatusLines.get(0));
    assertNull(StatusLines.get(-200));
    int[] codes = new int[] { 200, 404, 500 };
    for (int code: codes) {
      final Response response = response(code).build();
      assertEquals(code, response.code());
      //noinspection ConstantConditions
      assertEquals(StatusLines.get(code).message, response.message());
    }
  }

  @Test
  public void testCacheControl() throws IOException {
    final String etag = "e-tag";
    assertEquals(etag, response(200).noCache("e-tag").build().header("ETag"));
    assertEquals("no-cache", response(200).noCache(null).build().header("Cache-Control"));
    assertEquals(etag, response(200).etag(etag).noCache(null).build().header("ETag"));
    assertNull(response(200).noCache(etag).etag(null).build().header("ETag"));
    assertEquals("no-store", response(200).noStore().build().header("Cache-Control"));
    assertNull(response(200).etag(etag).noStore().build().header("ETag"));
    assertTrue(response(200).maxAge(30).priv().build().headers("Cache-Control").contains("private"));
    assertEquals("max-age=60", response(200).maxAge(60).build().header("Cache-Control"));
    assertTrue(response(200).maxAge(60).priv().build().headers("Cache-Control").contains("max-age=60"));
  }

  @Test
  public void testHeaders() throws IOException {
    assertEquals("v1", response(200).header("h1", "v1").build().header("h1"));
    assertEquals("v1", response(200).addHeader("h1", "v1").build().header("h1"));
    assertEquals("v2", response(200).header("h1", "v1").header("h1", "v2").build().header("h1"));
    final List<String> values = response(200).header("h1", "v1").addHeader("h1", "v2").build().headers("h1");
    assertEquals(2, values.size());
    assertTrue(values.contains("v1"));
    assertTrue(values.contains("v2"));
    assertNull(response(200).header("h1","v1").addHeader("h1","v2").removeHeader("h1").build().header("h1"));
  }

  @Test
  public void testBody() throws IOException {
    assertNull(response(404).noBody().build().body());
    assertEquals("test", response(200).body("test").build().body().string());
    assertEquals("test",
                 response(200).body("test".getBytes("UTF-8")).build().body().string());
    assertEquals(4, response(200).body("1234").build().body().bytes().length);
    final Buffer buffer = new Buffer();
    response(200).body("test").build().writeBody(null, buffer);
    assertEquals("test", buffer.readUtf8());
    final Buffer source = new Buffer();
    source.writeUtf8("test");
    assertEquals("test",
                 response(200).body(source, (int)source.size()).build().body().string());
  }

  @Test
  public void testChunks() throws IOException {
    final Buffer buffer = new Buffer();
    response(200).chunks("").build().writeBody(null, buffer);
    assertEquals("", Okio.buffer(new ChunkedSource(buffer)).readUtf8());
    buffer.readByteArray();
    response(200).chunks("test").build().writeBody(null, buffer);
    assertEquals("test",  Okio.buffer(new ChunkedSource(buffer)).readUtf8());
    buffer.readByteArray();
    response(200).chunks("1", "23", "4").build().writeBody(null, buffer);
    assertEquals("1234",  Okio.buffer(new ChunkedSource(buffer)).readUtf8());
    buffer.readByteArray();
  }

  private static class ChunkedSource implements Source {
    private static final long NO_CHUNK_YET = -1L;
    private long bytesRemainingInChunk = NO_CHUNK_YET;
    private boolean hasMoreChunks = true;
    private boolean closed;

    private final BufferedSource source;

    ChunkedSource(final BufferedSource source) {
      this.source = source;
    }

    @Override public Timeout timeout() {
      return new ForwardingTimeout(source.timeout());
    }

    @Override public long read(final Buffer sink, final long byteCount) throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (closed) throw new IllegalStateException("closed");
      if (!hasMoreChunks) return -1;
      if (bytesRemainingInChunk == 0 || bytesRemainingInChunk == NO_CHUNK_YET) {
        readChunkSize();
        if (!hasMoreChunks) return -1;
      }
      final long read = source.read(sink, Math.min(byteCount, bytesRemainingInChunk));
      if (read == -1) {
        throw new ProtocolException("unexpected end of stream");
      }
      bytesRemainingInChunk -= read;
      return read;
    }

    private void readChunkSize() throws IOException {
      // Read the suffix of the previous chunk.
      if (bytesRemainingInChunk != NO_CHUNK_YET) {
        source.readUtf8LineStrict();
      }
      try {
        bytesRemainingInChunk = source.readHexadecimalUnsignedLong();
        String extensions = source.readUtf8LineStrict().trim();
        if (bytesRemainingInChunk < 0 || (!extensions.isEmpty() && !extensions.startsWith(";"))) {
          throw new ProtocolException("expected chunk size and optional extensions but was \""
                                      + bytesRemainingInChunk + extensions + "\"");
        }
      } catch (NumberFormatException e) {
        throw new ProtocolException(e.getMessage());
      }
      if (bytesRemainingInChunk == 0L) {
        hasMoreChunks = false;
      }
    }

    @Override public void close() throws IOException {
      if (closed) return;
      closed = true;
    }

  }

}
