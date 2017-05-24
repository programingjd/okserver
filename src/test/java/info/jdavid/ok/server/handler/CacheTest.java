package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import info.jdavid.ok.server.HttpsTest;
import info.jdavid.ok.server.InMemoryFileSystem;
import okhttp3.*;
import okhttp3.Request;
import okhttp3.internal.io.FileSystem;
import okio.Buffer;
import okio.Okio;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


public class CacheTest {

  @BeforeClass
  public static void startServer() throws IOException {
    FileHandlerTest.startServer();
  }

  @AfterClass
  public static void stopServer() {
    FileHandlerTest.stopServer();
  }

  private static File getWebRoot() throws IOException {
    final File projectDir = new File(".").getCanonicalFile();
    final File root = new File(new File(new File(projectDir, "src"), "test"), "resources");
    assertTrue(root.isDirectory());
    return root;
  }

  private static Cache createInMemoryCache() {
    try {
      final Constructor<Cache> constructor =
        Cache.class.getDeclaredConstructor(File.class, Long.TYPE, FileSystem.class);
      constructor.setAccessible(true);
      return constructor.newInstance(
        new File(".virtual"), 128*1024*1024, new InMemoryFileSystem()
      );
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Response getResponse(final Cache cache, final HttpUrl url) {
    try {
      return (Response)mGetFromCache.invoke(cache, new okhttp3.Request.Builder().url(url).build());
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final Method mGetFromCache;
  static {
    try {
      mGetFromCache = Cache.class.getDeclaredMethod("get", okhttp3.Request.class);
      mGetFromCache.setAccessible(true);
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static OkHttpClient client() {
    return HttpsTest.client.newBuilder().
      readTimeout(0, TimeUnit.SECONDS).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      protocols(Arrays.asList(Protocol.HTTP_1_1, Protocol.HTTP_2)).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      followRedirects(false).
      followSslRedirects(false).
      cache(createInMemoryCache()).
      build();
  }

  @Test
  public void testHtmlHttp() throws Exception {
    testHtml("http://localhost:8080/");
  }

  @Test
  public void testHtmlHttps() throws Exception {
    testHtml("https://localhost:8181/");
  }

  private void testHtml(final String baseUrl) throws Exception {
    final File root = getWebRoot();
    final OkHttpClient client = client();
    final Cache cache = client.cache();
    final HttpUrl url = HttpUrl.parse(baseUrl);

    final Response response1 = client.newCall(
      new Request.Builder().url(url).header("Accept-Encoding", "identity").build()
    ).execute();
    assertEquals(200, response1.code());
    response1.body().close();
    final Response response2 = getResponse(cache, url);
    assertNotNull(response2);
    assertEquals(content(new File(root, "index.html")), response2.body().string());
    assertEquals("no-cache", response2.header("Cache-Control"));
    cache.evictAll();
    assertNull(getResponse(cache, url));

    final Response response3 = client.newCall(
      new Request.Builder().
        url(url).
        header("Cache-Control", "no-cache, no-store, must-revalidate").
        build()
    ).execute();
    assertEquals(200, response3.code());
    response3.close();
    assertNull(getResponse(cache, url));

    final String etag = response3.header("ETag");
    assertNotNull(etag);
    final Response response4 = client.newCall(
      new Request.Builder().
        url(url).
        header("If-None-Match", etag).
        build()
    ).execute();
    assertEquals(304, response4.code());
    response4.close();
    assertNull(getResponse(cache, url));
  }

  @Test
  public void testImgHttp() throws Exception {
    testImg("http://localhost:8080/");
  }

  @Test
  public void testImgHttps() throws Exception {
    testImg("https://localhost:8181/");
  }

  private void testImg(final String baseUrl) throws Exception {
    final File root = getWebRoot();
    final OkHttpClient client = client();
    final Cache cache = client.cache();
    final HttpUrl url = HttpUrl.parse(baseUrl).newBuilder("img.png").build();

    final Response response1 = client.newCall(
      new Request.Builder().url(url).header("Accept-Encoding", "identity").build()
    ).execute();
    assertEquals(200, response1.code());
    response1.body().close();
    final Response response2 = getResponse(cache, url);
    assertNotNull(response2);
    assertEquals(bytes(new File(root, "img.png")).length,
                 response2.body().bytes().length);
    final String cacheControl = response2.header("Cache-Control");
    assertTrue(cacheControl.contains("max-age="));
    assertTrue(cacheControl.contains("immutable"));
    cache.evictAll();
    assertNull(getResponse(cache, url));

    final Response response3 = client.newCall(
      new Request.Builder().
        url(url).
        header("Cache-Control", "no-cache, no-store, must-revalidate").
        build()
    ).execute();
    assertEquals(200, response3.code());
    response3.close();
    assertNull(getResponse(cache, url));

    final String etag = response3.header("ETag");
    assertNotNull(etag);
    final Response response4 = client.newCall(
      new Request.Builder().
        url(url).
        header("If-None-Match", etag).
        build()
    ).execute();
    assertEquals(304, response4.code());
    response4.close();
    assertNull(getResponse(cache, url));
  }

  @Test
  public void testJsonHttp() throws Exception {
    testJson("http://localhost:8080/");
  }

  @Test
  public void testJsonHttps() throws Exception {
    testJson("https://localhost:8181/");
  }

  private void testJson(final String baseUrl) throws Exception {
    final File root = getWebRoot();
    final OkHttpClient client = client();
    final Cache cache = client.cache();
    final HttpUrl url = HttpUrl.parse(baseUrl).newBuilder("data.json").build();

    final Response response1 = client.newCall(new Request.Builder().url(url).build()).execute();
    assertEquals(200, response1.code());
    assertEquals(content(new File(root, "data.json")), response1.body().string());
    assertEquals("no-store", response1.header("Cache-Control"));
    final String etag = response1.header("ETag");
    assertNull(etag);
    response1.close();
    final Response response2 = getResponse(cache, url);
    assertNull(response2);
  }

  @Test
  public void testTextHttp() throws Exception {
    testText("http://localhost:8080/");
  }

  @Test
  public void testTextHttps() throws Exception {
    testText("https://localhost:8181/");
  }

  private void testText(final String baseUrl) throws Exception {
    final File root = getWebRoot();
    final OkHttpClient client = client();
    final Cache cache = client.cache();
    final HttpUrl url = HttpUrl.parse(baseUrl).newBuilder("noindex/file.txt").build();

    final Response response1 = client.newCall(
      new Request.Builder().url(url).header("Accept-Encoding", "identity").build()
    ).execute();
    assertEquals(200, response1.code());
    response1.body().close();
    final Response response2 = getResponse(cache, url);
    assertNotNull(response2);
    assertEquals(content(new File(new File(root, "noindex"), "file.txt")),
                 response2.body().string());
    assertEquals("no-cache", response2.header("Cache-Control"));
    response2.close();
    cache.evictAll();
    assertNull(getResponse(cache, url));

    final Response response3 = client.newCall(
      new Request.Builder().
        url(url).
        header("Cache-Control", "no-cache, no-store, must-revalidate").
        build()
    ).execute();
    assertEquals(200, response3.code());
    response3.close();
    assertNull(getResponse(cache, url));

    final String etag = response3.header("ETag");
    assertNotNull(etag);
    final Response response4 = client.newCall(
      new Request.Builder().
        url(url).
        header("If-None-Match", etag).
        build()
    ).execute();
    assertEquals(304, response4.code());
    response4.close();
    assertNull(getResponse(cache, url));
  }

  private static String content(final File f) {
    final Buffer buffer = new Buffer();
    try {
      Okio.source(f).read(buffer, f.length());
      return buffer.readUtf8();
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] bytes(final File f) {
    final Buffer buffer = new Buffer();
    try {
      Okio.source(f).read(buffer, f.length());
      return buffer.readByteArray();
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

}
