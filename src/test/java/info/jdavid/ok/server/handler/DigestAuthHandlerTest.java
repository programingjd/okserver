package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Https;
import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.apache.http.auth.AuthScope;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


@SuppressWarnings("ConstantConditions")
public class DigestAuthHandlerTest {

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  @BeforeClass
  public static void startServer() throws IOException {
    final File root = getWebRoot();
    final File certFile = new File(root, "test.p12");
    assertTrue(certFile.isFile());
    final byte[] cert = new byte[(int)certFile.length()];
    try (final RandomAccessFile raf = new RandomAccessFile(certFile, "r")) {
      raf.readFully(cert);
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
    final Map<String, String> credentials = new HashMap<>();
    credentials.put("user1", "password1");
    credentials.put("user 2", "password 2");
    SERVER.
      ports(8080, 8181).
      https(new Https.Builder().certificate(cert).build()).
      maxRequestSize(4096).
      requestHandler(new RequestHandlerChain() {
        @Override
        protected boolean allowInsecure(final String method, final HttpUrl url, final Headers requestHeaders,
                                        final boolean insecureOnly) {
          return true;
        }
      }.add(new DigestAuthHandler(credentials, "Test", "DigestAuthTest".getBytes(), handler))).
      start();
  }

  @AfterClass
  public static void stopServer() {
    SERVER.shutdown();
  }

  private static File getWebRoot() throws IOException {
    final File projectDir = new File(".").getCanonicalFile();
    final File root = new File(new File(new File(projectDir, "src"), "test"), "resources");
    assertTrue(root.isDirectory());
    return root;
  }

  @Test
  public void testHttp() throws Exception {
    test(HttpUrl.parse("http://localhost:8080/"));
  }

  @Test
  public void testHttps() throws Exception {
    test(HttpUrl.parse("https://localhost:8181/"));
  }

  private void test(final HttpUrl baseUrl) throws Exception {
    final DefaultCredentialsProvider credentials = new DefaultCredentialsProvider();
    final WebClient web = new WebClient(BrowserVersion.BEST_SUPPORTED);
    web.setCredentialsProvider(credentials);
    try {
      final WebClientOptions options = web.getOptions();
      options.setThrowExceptionOnFailingStatusCode(false);
      options.setThrowExceptionOnScriptError(false);
      options.setJavaScriptEnabled(true);
      options.setCssEnabled(true);
      options.setDownloadImages(true);
      options.setRedirectEnabled(true);
      options.setUseInsecureSSL(true);

      final Page page1 = web.getPage(baseUrl.url());
      assertEquals(401, page1.getWebResponse().getStatusCode());
      assertWWWAuthHeaderIsCorrect(
        page1.getWebResponse().getResponseHeaderValue("WWW-AUTHENTICATE"),
        baseUrl.host()
      );

      credentials.addCredentials("anonymous", "anonymous",
                                 baseUrl.host(), baseUrl.port(), "Test@" + baseUrl.host());

      final Page page2 = web.getPage(baseUrl.url());
      assertEquals(401, page2.getWebResponse().getStatusCode());
      assertWWWAuthHeaderIsCorrect(
        page2.getWebResponse().getResponseHeaderValue("WWW-AUTHENTICATE"),
        baseUrl.host()
      );

      credentials.removeCredentials(new AuthScope(baseUrl.host(), baseUrl.port()));
      credentials.addCredentials("user1", "password1",
                                 baseUrl.host(), baseUrl.port(), "Test@" + baseUrl.host());

      final Page page3 = web.getPage(baseUrl.url());
      assertEquals(200, page3.getWebResponse().getStatusCode());

      credentials.removeCredentials(new AuthScope(baseUrl.host(), baseUrl.port()));
      credentials.addCredentials("user 2", "password 2",
                                 baseUrl.host(), baseUrl.port(), "Test@" + baseUrl.host());

      final Page page4 = web.getPage(baseUrl.url());
      assertEquals(200, page4.getWebResponse().getStatusCode());
    }
    finally {
      web.close();
    }
  }

  private Map<String, String> assertWWWAuthHeaderIsCorrect(final String headerValue, final String host) {
    assertNotNull(headerValue);
    assertTrue(headerValue.startsWith("Digest "));
    final Map<String, String> map1 = DigestAuthHandler.parseHeaderValue(headerValue);
    assertEquals("Test@" + host, map1.get("realm"));
    assertEquals("MD5", map1.get("algorithm"));
    assertEquals("auth", map1.get("qop"));
    assertNotNull(map1.get("nonce"));
    assertNotNull(map1.get("opaque"));
    return map1;
  }

  static OkHttpClient client() {
    return new OkHttpClient.Builder().
      followRedirects(false).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      protocols(Collections.singletonList(Protocol.HTTP_1_1)).
      build();
  }

  @Test
  public void testSeed() throws Exception {
    final OkHttpClient client = client();
    final okhttp3.Response r1 =
      client.newCall(new okhttp3.Request.Builder().url("http://localhost:8080/").build()).execute();
    assertEquals("", r1.body().string());
    assertEquals(401, r1.code());
    final String header1 = r1.header("WWW-Authenticate");
    final String opaque1 = assertWWWAuthHeaderIsCorrect(header1, "localhost").get("opaque");
    stopServer();
    startServer();
    final okhttp3.Response r2 =
      client.newCall(new okhttp3.Request.Builder().url("http://localhost:8080/").build()).execute();
    assertEquals("", r2.body().string());
    assertEquals(401, r2.code());
    final String header2 = r2.header("WWW-Authenticate");
    final String opaque2 = assertWWWAuthHeaderIsCorrect(header2, "localhost").get("opaque");
    assertEquals(opaque1, opaque2);
  }

}
