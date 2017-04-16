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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CacheTest {

  @BeforeClass
  public static void startServer() throws IOException {
    FileRequestHandlerTest.startServer();
  }

  @AfterClass
  public static void stopServer() {
    FileRequestHandlerTest.stopServer();
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
    final Response response1 = client.newCall(new Request.Builder().url(url).build()).execute();
    assertEquals(200, response1.code());
    response1.body().close();
    final Response response2 = getResponse(cache, url);
    assertNotNull(response2);
    assertEquals(new File(root, "index.html").length(),
                 Integer.parseInt(response2.header("Content-Length")));

  }

}
