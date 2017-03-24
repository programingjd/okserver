package info.jdavid.ok.server;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
import java.util.Collections;
import java.util.concurrent.TimeUnit;


//@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
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

  private static Request.Builder secureRequest(final String... segments) {
    HttpUrl.Builder url = new HttpUrl.Builder().scheme("https").host("localhost").port(8181);
    if (segments != null) {
      for (final String segment: segments) {
        url.addPathSegment(segment);
      }
    }
    return new Request.Builder().url(url.build());
  }

  private static final OkHttpClient client = new OkHttpClient();

  private static OkHttpClient client() {
    return HttpsTest.client.
      newBuilder().
      protocols(Collections.singletonList(Protocol.HTTP_1_1)).
      build();
  }

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  @BeforeClass
  public static void startServer() {
    SERVER.
      ports(8080, 8181).
      https(new Https.Builder().certificate(HttpsTest.cert).build()).
      maxRequestSize(512).
      requestHandler(new TestRequestHandler()).
      start();
    // Use an http client once to get rid of the static initializer penalty.
    // This is done so that the first test elapsed time doesn't get artificially high.
    try {
      final OkHttpClient c = client.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
      c.newCall(new Request.Builder().url("http://google.com").build()).execute();
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
      fail("A new hostname should not be accepted once the server has been started.");
    }
    catch (final IllegalStateException e) {
      assertNull(SERVER.hostname());
    }
    try {
      SERVER.port(8090);
      fail("A new port should not be accepted once the server has been started.");
    }
    catch (final IllegalStateException e) {
      assertEquals(8080, SERVER.port());
    }
    try {
      SERVER.securePort(8191);
      fail("A new secure port should not be accepted once the server has been started.");
    }
    catch (final IllegalStateException e) {
      assertEquals(8181, SERVER.securePort());
    }
    try {
      SERVER.ports(8090, 8191);
      fail("A new port should not be accepted once the server has been started.");
    }
    catch (final IllegalStateException e) {
      assertEquals(8080, SERVER.port());
      assertEquals(8181, SERVER.securePort());
    }
    try {
      SERVER.maxRequestSize(1024);
      fail("A new maximum request size should not be accepted once the server has been started.");
    }
    catch (final IllegalStateException e) {
      assertEquals(512, SERVER.maxRequestSize());
    }
    try {
      SERVER.start();
      fail("Starting the server again while it's still running should have failed.");
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
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(413, r.code());
    assertEquals("Payload Too Large", r.message());
    r.close();
  }

  @Test
  public void testPayloadTooLargeSecure() throws IOException {
    final Response r = client().newCall(
      secureRequest("test").
        post(RequestBody.create(MediaTypes.OCTET_STREAM, new byte[700])).
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(413, r.code());
    assertEquals("Payload Too Large", r.message());
    r.close();
  }

  @Test
  public void testNotFound() throws IOException {
    final Response r = client().newCall(
      request("notfound").
        build()
    ).execute();
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(404, r.code());
    assertEquals("Not Found", r.message());
    r.close();
  }

  @Test
  public void testNotFoundSecure() throws IOException {
    final Response r = client().newCall(secureRequest("notfound").
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(404, r.code());
    assertEquals("Not Found", r.message());
    r.close();
  }

  @Test
  public void testMethodNotAllowed() throws IOException {
    final Response r = client().newCall(
      request("wrongmethod").
        post(RequestBody.create(MediaTypes.TEXT, "abc")).
        build()
    ).execute();
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(405, r.code());
    assertEquals("Method Not Allowed", r.message());
    r.close();
  }

  @Test
  public void testMethodNotAllowedSecure() throws IOException {
    final Response r = client().newCall(
      secureRequest("wrongmethod").
        post(RequestBody.create(MediaTypes.TEXT, "abc")).
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(405, r.code());
    assertEquals("Method Not Allowed", r.message());
    r.close();
  }

  @Test
  public void testGet1() throws IOException {
    final Response r = client().newCall(
      request("test").
        header("key1", "val1").
        build()
    ).execute();
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("val1", r.header("key1"));
    assertEquals("0", r.header("Content-Length"));
    assertEquals("", r.body().string());
  }

  @Test
  public void testGet1Secure() throws IOException {
    final Response r = client().newCall(
      secureRequest("test").
        header("key1", "val1").
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
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
    assertNull(r.handshake());
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
  public void testPost1Secure() throws IOException {
    final Response r = client().newCall(
      secureRequest("test").
        header("key1", "val1a").addHeader("key1", "val1b").
        post(RequestBody.create(MediaTypes.TEXT, "text")).
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
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
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertTrue(r.header("Content-Type").startsWith("application/json"));
    assertEquals("2", r.header("Content-Length"));
    assertEquals("{}", r.body().string());
  }

  @Test
  public void testPut1Secure() throws IOException {
    final Response r = client().newCall(
      secureRequest("test").
        put(RequestBody.create(MediaTypes.JSON, "{}")).
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
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
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("application/octet-stream", r.header("Content-Type"));
    assertEquals("6", r.header("Content-Length"));
    assertArrayEquals(bytes, r.body().bytes());
  }

  @Test
  public void testDelete1Secure() throws IOException {
    final byte[] bytes = new byte[] { 1, 0, 5, 0, 100, -5 };
    final Response r = client().newCall(
      secureRequest("test").
        delete(RequestBody.create(MediaTypes.OCTET_STREAM, bytes)).
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
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
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertNull(r.header("Content-Type"));
    assertEquals("0", r.header("Content-Length"));
    assertEquals("", r.body().string());
  }

  @Test
  public void testDelete2Secure() throws IOException {
    final Response r = client().newCall(
      secureRequest("test").
        delete().
        build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertNull(r.header("Content-Type"));
    assertEquals("0", r.header("Content-Length"));
    assertEquals("", r.body().string());
  }

  private RequestBody chunkedRequestBody() {
    return new RequestBody() {
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
    };
  }

  private void testChunked(final boolean explicit) throws IOException {
    final Request.Builder requestBuilder = request("test");
    if (explicit) {
      requestBuilder.header("Transfer-Encoding", "chunked").header("Content-Length", "-1");
    }
    final Response r = client().newCall(
      requestBuilder.
        post(chunkedRequestBody()).build()
    ).execute();
    assertNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("chunked_request_data_1", r.body().source().readUtf8());
    r.close();
  }

  private void testChunkedSecure(final boolean explicit) throws IOException {
    final Request.Builder requestBuilder = secureRequest("test");
    if (explicit) {
      requestBuilder.header("Transfer-Encoding", "chunked").header("Content-Length", "-1");
    }
    final Response r = client().newCall(
      requestBuilder.
        post(chunkedRequestBody()).build()
    ).execute();
    assertNotNull(r.handshake().tlsVersion());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("chunked_request_data_1", r.body().source().readUtf8());
    r.close();
  }

  @Test
  public void testExplicitChunked() throws IOException {
    testChunked(true);
  }

  @Test
  public void testImplicitChunked() throws IOException {
    testChunked(false);
  }

  @Test
  public void testExplicitChunkedSecure() throws IOException {
    testChunkedSecure(true);
  }

  @Test
  public void testImplicitChunkedSecure() throws IOException {
    testChunkedSecure(false);
  }

}
