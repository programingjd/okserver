package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Https;
import info.jdavid.ok.server.HttpsTest;
import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


public class BasicAuthHandlerTest {

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
    final Handler handler = new Handler() {
      @Override public Handler setup() { return this; }
      @Override public String[] matches(final String method, final HttpUrl url) {
        return new String[0];
      }
      @Override public Response.Builder handle(final Request request, final String[] params) {
        return new Response.Builder().statusLine(StatusLines.OK).noBody();
      }
    };
    final Map<String, String> credentials = new HashMap<String, String>();
    credentials.put("user1", "password1");
    credentials.put("user 2", "password 2");
    SERVER.
      ports(8080, 8181).
      https(new Https.Builder().certificate(cert).build()).
      maxRequestSize(512).
      requestHandler(new RequestHandlerChain() {
        @Override
        protected boolean allowInsecure(final String method, final HttpUrl url, final Headers requestHeaders,
                                        final boolean insecureOnly) {
          return true;
        }
      }.add(new BasicAuthHandler(credentials, handler))).
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
      protocols(Arrays.asList(Protocol.HTTP_1_1, Protocol.HTTP_2)).
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
  public void testHttp() throws IOException {
    test("http://localhost:8080/");
  }

  @Test
  public void testHttps() throws IOException {
    test("https://localhost:8181/");
  }

  private void test(final String baseUrl) throws IOException {
    final OkHttpClient client = client();

    final okhttp3.Response response1 = client.newCall(new okhttp3.Request.Builder().
      url(baseUrl).
      build()
    ).execute();
    assertBadAuth(response1);
    response1.body().close();

    final okhttp3.Response response2 = client.newCall(new okhttp3.Request.Builder().
      url(baseUrl).
      header("Authorization", Credentials.basic("anonymous", "anonymous")).
      build()
    ).execute();
    assertBadAuth(response2);
    response2.body().close();

    final okhttp3.Response response3 = client.newCall(new okhttp3.Request.Builder().
      url(baseUrl).
      header("Authorization", Credentials.basic("user1", null)).
      build()
    ).execute();
    assertBadAuth(response3);
    response3.body().close();

    final okhttp3.Response response4 = client.newCall(new okhttp3.Request.Builder().
      url(baseUrl).
      header("Authorization", Credentials.basic("user1", "password1")).
      build()
    ).execute();
    assertEquals(200, response4.code());
    response4.body().close();

    final okhttp3.Response response5 = client.newCall(new okhttp3.Request.Builder().
      url(baseUrl).
      header("Authorization", Credentials.basic("user1", "password 2")).
      build()
    ).execute();
    assertBadAuth(response5);
    response5.body().close();

    final okhttp3.Response response6 = client.newCall(new okhttp3.Request.Builder().
      url(baseUrl).
      header("Authorization", Credentials.basic("user 2", "password 2")).
      build()
    ).execute();
    assertEquals(200, response6.code());
    response6.body().close();
  }

  private void assertBadAuth(final okhttp3.Response response) {
    assertEquals(401, response.code());
    assertEquals("Basic realm=\"User Visible Realm\"",
                 response.header("WWW-Authenticate"));
  }

}
