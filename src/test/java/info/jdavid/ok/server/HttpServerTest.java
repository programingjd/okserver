package info.jdavid.ok.server;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


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
    SERVER.port(8080).start();
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

}
