package info.jdavid.ok.server;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import okio.BufferedSink;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.TimeUnit;


//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HttpServerTest {

  private static Request.Builder request(final String... segments) {
    HttpUrl.Builder url = new HttpUrl.Builder().scheme("http").host("localhost").port(8080);
    if (segments != null) {
      for (final String segment: segments) {
        url.addPathSegment(segment);
      }
    }
    return new Request.Builder().url(url.build());
  }

  private static OkHttpClient client() {
    final OkHttpClient client = new OkHttpClient();
    client.setReadTimeout(0, TimeUnit.SECONDS);
    return client;
  }

  private static final HttpServer SERVER = new HttpServer();

  @BeforeClass
  public static void startServer() {
    SERVER.port(8080).maxRequestSize(512).start();
    // Use an http client once to get rid of the static initializer penalty.
    // This is done so that the first test elapsed time doesn't get artificially high.
    try {
      final OkHttpClient client = new OkHttpClient();
      client.setReadTimeout(1, TimeUnit.SECONDS);
      client().newCall(new Request.Builder().url("http://google.com").build()).execute();
    }
    catch (final IOException ignore) {}
  }

  @AfterClass
  public static void stopServer() {
    SERVER.shutdown();
  }

//  @Test
//  public void testContinue() throws IOException {
//    final Response r = client().newCall(
//      request("test").
//        head().
//        header("Expect", "100-continue").
//        build()
//    ).execute();
//    assertEquals(Protocol.HTTP_1_1, r.protocol());
//    assertEquals(100, r.code());
//    assertEquals("Continue", r.message());
//    assertEquals("0", r.header("Content-Length"));
//    assertEquals("", r.body().string());
//  }

  @Test
  public void testBadRequest() throws IOException {
    final Socket socket = new Socket("localhost", 8080);
    final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    final PrintWriter writer = new PrintWriter(socket.getOutputStream());
    writer.write("Test");
    writer.write('\n');
    writer.flush();
    assertEquals("HTTP/1.1 400 Bad Request", reader.readLine());
  }

  @Test
  public void testIllegalState() throws IOException {
    try {
      SERVER.hostname("test");
      fail();
    }
    catch (final IllegalStateException e) {
      assertNull(SERVER.hostname());
    }
    try {
      SERVER.port(8090);
      fail();
    }
    catch (final IllegalStateException e) {
      assertEquals(8080, SERVER.port());
    }
    try {
      SERVER.maxRequestSize(1024);
    }
    catch (final IllegalStateException e) {
      assertEquals(512, SERVER.maxRequestSize());
    }
    try {
      SERVER.start();
    }
    catch (final IllegalStateException e) {
      assertTrue(SERVER.isRunning());
    }
  }

  @Test
  public void testPayloadTooLarge() throws IOException {
    final Response r = client().newCall(
      request("test").
        post(RequestBody.create(MediaTypes.OCTET_STREAM, new byte[700])).
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(413, r.code());
    assertEquals("Payload Too Large", r.message());
  }

  @Test
  public void testNotFound() throws IOException {
    final Response r = client().newCall(
      request("notfound").
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(404, r.code());
    assertEquals("Not Found", r.message());
  }

  @Test
  public void testMethodNotAllowed() throws IOException {
    final Response r = client().newCall(
      request("wrongmethod").
        post(RequestBody.create(MediaTypes.TEXT, "abc")).
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(405, r.code());
    assertEquals("Method Not Allowed", r.message());
  }


  @Test
  public void testGet1() throws IOException {
    final Response r = client().newCall(
      request("test").
        header("key1", "val1").
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("val1", r.header("key1"));
    assertEquals("0", r.header("Content-Length"));
    assertEquals("", r.body().string());
  }

  @Test
  public void testPost1() throws IOException {
    final Response r = client().newCall(
      request("test").
        header("key1", "val1a").addHeader("key1", "val1b").
        post(RequestBody.create(MediaTypes.TEXT, "text")).
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("val1b", r.header("key1"));
    assertArrayEquals(new String[] { "val1a", "val1b" }, r.headers("key1").toArray());
    assertEquals("4", r.header("Content-Length"));
    assertTrue(r.header("Content-Type").startsWith("text/plain"));
    assertEquals("text", r.body().string());
  }

  @Test
  public void testPut1() throws IOException {
    final Response r = client().newCall(
      request("test").
        put(RequestBody.create(MediaTypes.JSON, "{}")).
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertTrue(r.header("Content-Type").startsWith("application/json"));
    assertEquals("2", r.header("Content-Length"));
    assertEquals("{}", r.body().string());
  }

  @Test
  public void testDelete1() throws IOException {
    final byte[] bytes = new byte[] { 1, 0, 5, 0, 100, -5 };
    final Response r = client().newCall(
      request("test").
        delete(RequestBody.create(MediaTypes.OCTET_STREAM, bytes)).
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("application/octet-stream", r.header("Content-Type"));
    assertEquals("6", r.header("Content-Length"));
    assertArrayEquals(bytes, r.body().bytes());
  }

  @Test
  public void testDelete2() throws IOException {
    final Response r = client().newCall(
      request("test").
        delete().
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertNull(r.header("Content-Type"));
    assertEquals("0", r.header("Content-Length"));
    assertEquals("", r.body().string());
  }

  @Test
  public void testExplicitChunked() throws IOException {
    //final RequestBody body = RequestBody.create(MediaTypes.OCTET_STREAM, "some_data");
    final Response r = client().newCall(
      request("test").
        post(new RequestBody() {
          @Override
          public MediaType contentType() {
            return MediaTypes.OCTET_STREAM;
          }

          @Override
          public void writeTo(final BufferedSink sink) throws IOException {
            sink.writeHexadecimalUnsignedLong(4);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("chun");
            sink.writeUtf8("\r\n");
            sink.writeHexadecimalUnsignedLong(11);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("ked_request");
            sink.writeUtf8("\r\n");
            sink.writeHexadecimalUnsignedLong(7);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("_data_1");
            sink.writeUtf8("\r\n");
            sink.writeHexadecimalUnsignedLong(0);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("\r\n");
          }
        }).
        header("Transfer-Encoding", "chunked").
        header("Content-Length", "-1").
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("chunked_request_data_1", r.body().source().readUtf8());
  }

  @Test
  public void testImplicitChunked() throws IOException {
    final Response r = client().newCall(
      request("test").
        post(new RequestBody() {
          @Override public MediaType contentType() {
            return MediaTypes.OCTET_STREAM;
          }
          @Override public void writeTo(final BufferedSink sink) throws IOException {
            sink.writeHexadecimalUnsignedLong(4);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("chun");
            sink.writeUtf8("\r\n");
            sink.writeHexadecimalUnsignedLong(11);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("ked_request");
            sink.writeUtf8("\r\n");
            sink.writeHexadecimalUnsignedLong(7);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("_data_1");
            sink.writeUtf8("\r\n");
            sink.writeHexadecimalUnsignedLong(0);
            sink.writeUtf8("\r\n");
            sink.writeUtf8("\r\n");
          }
        }).
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("chunked_request_data_1", r.body().source().readUtf8());
  }

}
