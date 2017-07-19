package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Https;
import info.jdavid.ok.server.HttpsTest;
import info.jdavid.ok.server.RequestHandlerChain;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


@SuppressWarnings("ConstantConditions")
public class AcmeChallengeHandlerTest {
  private static final String FILENAME = "8sJDar9qlMMswuDxAa2rnfOid82dnSw_fQapdlxfjJl";
  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  @BeforeClass
  public static void startServer() throws IOException {
    final File root = getWebRoot();
    final File certFile = new File(root, "test.p12");
    assertTrue(certFile.isFile());
    final byte[] cert = new byte[(int)certFile.length()];
    final RandomAccessFile raf = new RandomAccessFile(certFile, "r");
    try {
      raf.readFully(cert);
    }
    finally {
      raf.close();
    }
//    try (final RandomAccessFile raf = new RandomAccessFile(certFile, "r")) {
//      raf.readFully(cert);
//    }
    SERVER.
      ports(8080, 8181).
      https(new Https.Builder().certificate(cert).build()).
      maxRequestSize(4096).
      requestHandler(new RequestHandlerChain(new AcmeChallengeHandler(root)) {
        @Override
        protected boolean allowInsecure(final String method, final HttpUrl url, final Headers requestHeaders,
                                        final boolean insecureOnly) {
          return true;
        }
      }.add(new FileHandler(root))).
      start();
  }

  @AfterClass
  public static void stopServer() {
    SERVER.shutdown();
  }

  private static OkHttpClient client() {
    return HttpsTest.client.newBuilder().
      readTimeout(0, TimeUnit.SECONDS).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      protocols(Collections.singletonList(Protocol.HTTP_1_1)).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      followRedirects(false).
      followSslRedirects(false).
      build();
  }

  private static File getWebRoot() throws IOException {
    final File projectDir = new File(".").getCanonicalFile();
    final File root = new File(new File(new File(projectDir, "src"), "test"), "resources");
    assertTrue(root.isDirectory());
    return root;
  }

  @Test
  public void testHttp() throws Exception {
    test("http://localhost:8080/");
    FileHandlerTest.testWeb("http://localhost:8080/");
  }

  @Test
  public void testHttps() throws Exception {
    test("https://localhost:8181/");
    FileHandlerTest.testWeb("https://localhost:8181/");
  }

  private void test(final String baseUrl) throws IOException {
    final OkHttpClient client = client();

    final HttpUrl url = HttpUrl.parse(baseUrl);

    final okhttp3.Response response1 = client.newCall(new okhttp3.Request.Builder().
      url(url.newBuilder("/.well-known").build()).
      build()
    ).execute();
    assertFalse(response1.isSuccessful());
    response1.body().close();

    final okhttp3.Response response2 = client.newCall(new okhttp3.Request.Builder().
      url(url.newBuilder("/.well-known/test").build()).
      build()
    ).execute();
    assertFalse(response2.isSuccessful());
    response2.body().close();

    final okhttp3.Response response3 = client.newCall(new okhttp3.Request.Builder().
      url(url.newBuilder("/.well-known/acme-challenge/" + FILENAME).build()).
      build()
    ).execute();
    assertTrue(response3.isSuccessful());
    assertEquals(FILENAME, response3.body().string().trim());
  }


}
