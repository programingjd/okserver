package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.Cache;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Https;
import info.jdavid.ok.server.HttpsTest;
import info.jdavid.ok.server.MediaTypes;
import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.header.AcceptRanges;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


@SuppressWarnings("ConstantConditions")
public class FileHandlerTest {

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
      maxRequestSize(512).
      requestHandler(new RequestHandlerChain() {
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

  private static File getWebRoot() throws IOException {
    final File projectDir = new File(".").getCanonicalFile();
    final File root = new File(new File(new File(projectDir, "src"), "test"), "resources");
    assertTrue(root.isDirectory());
    return root;
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

  @Test
  public void testWebHttp() throws Exception {
    testWeb("http://localhost:8080/");
  }

  @Test
  public void testWebHttps() throws Exception {
    testWeb("https://localhost:8181/");
  }

  static void testWeb(final String baseUrl) throws Exception {
    final File root = getWebRoot();
    //try { Thread.sleep(3000L); } catch (final InterruptedException ignore) {}
    final WebClient web = new WebClient(BrowserVersion.BEST_SUPPORTED);
    try {
      web.setCache(new Cache() {
        @Override
        protected boolean isCacheableContent(WebResponse response) {
          return true;
        }
      });
      final WebClientOptions options = web.getOptions();
      options.setThrowExceptionOnFailingStatusCode(false);
      options.setThrowExceptionOnScriptError(false);
      options.setJavaScriptEnabled(true);
      options.setCssEnabled(true);
      options.setDownloadImages(true);
      options.setRedirectEnabled(false);
      options.setUseInsecureSSL(true);

      final HtmlPage page = web.getPage(baseUrl);
      assertEquals(200, page.getWebResponse().getStatusCode());
      final Cache cache = web.getCache();
      assertEquals(3, cache.getSize());
      final WebResponse imgResponse = cache.getCachedResponse(req(baseUrl + "img.png"));
      assertEquals(200, imgResponse.getStatusCode());
      assertEquals(new File(root, "img.png").length(), imgResponse.getContentLength());
      final WebResponse jsResponse = cache.getCachedResponse(req(baseUrl + "script.js"));
      assertEquals(200, jsResponse.getStatusCode());
      assertEquals(text(new File(root, "script.js")), jsResponse.getContentAsString().trim());
      final WebResponse htmlResponse = cache.getCachedResponse(req(baseUrl));
      assertEquals(200, htmlResponse.getStatusCode());
      assertEquals(text(new File(root, "index.html")), htmlResponse.getContentAsString().trim());

      cache.clear();
      web.getPage(baseUrl + "index.html");
      assertEquals(1, cache.getSize());
      final WebResponse indexResponse = cache.getCachedResponse(req(baseUrl + "index.html"));
      assertEquals(301, indexResponse.getStatusCode());
      assertEquals(baseUrl, indexResponse.getResponseHeaderValue("Location"));
    }
    finally {
      web.close();
    }
  }

  @Test
  public void testIndexHttp() throws IOException {
    testIndex("http://localhost:8080/");
  }

  @Test
  public void testIndexHttps() throws IOException {
    testIndex("https://localhost:8181/");
  }

  static void testIndex(final String baseUrl) throws IOException {
    final File root = getWebRoot();
    final HttpUrl url = HttpUrl.parse(baseUrl);
    final OkHttpClient client = client();
    final okhttp3.Response response1 =
      client.newCall(new Request.Builder().url(url).build()).execute();
    assertEquals(200, response1.code());
    assertEquals(text(new File(root, "index.html")), response1.body().string().trim());
    final okhttp3.Response response2 =
      client.newCall(new Request.Builder().url(url.newBuilder("index.html").build()).build()).execute();
    assertEquals(301, response2.code());
    assertEquals(baseUrl, response2.header("Location"));
    assertEquals("", response2.body().string());
    final okhttp3.Response response3 =
      client.newCall(new Request.Builder().url(url.newBuilder("index.htm").build()).build()).execute();
    assertEquals(404, response3.code());
    assertEquals("", response3.body().string());

    final File dir = new File(root, "dir");
    final okhttp3.Response response4 =
      client.newCall(new Request.Builder().url(url.newBuilder("/dir/").build()).build()).execute();
    assertEquals(200, response4.code());
    assertEquals(text(new File(dir, "index.htm")), response4.body().string().trim());
    final okhttp3.Response response5 =
      client.newCall(new Request.Builder().url(url.newBuilder("/dir/index.htm").build()).build()).execute();
    assertEquals(301, response5.code());
    assertEquals(url.newBuilder("/dir/").build().toString(), response5.header("Location"));
    assertEquals("", response5.body().string());
    final okhttp3.Response response6 =
      client.newCall(new Request.Builder().url(url.newBuilder("/dir/index.html").build()).build()).execute();
    assertEquals(404, response6.code());
    assertEquals("", response6.body().string());
    final okhttp3.Response response7 =
      client.newCall(new Request.Builder().url(url.newBuilder("/dir").build()).build()).execute();
    assertEquals(301, response7.code());
    assertEquals(url.newBuilder("/dir/").build().toString(), response7.header("Location"));
    assertEquals("", response7.body().string().trim());
    final okhttp3.Response response8 =
      client.newCall(new Request.Builder().url(url.newBuilder("/noindex").build()).build()).execute();
    assertEquals(403, response8.code());
    assertEquals("", response8.body().string().trim());
  }

  @Test
  public void testFilesHttp() throws IOException {
    testFiles("http://localhost:8080/");
  }

  @Test
  public void testFilesHttps() throws IOException {
    testFiles("https://localhost:8181/");
  }

  static void testFiles(final String baseUrl) throws IOException {
    final File root = getWebRoot();
    final HttpUrl url = HttpUrl.parse(baseUrl);
    final OkHttpClient client = client();
    final okhttp3.Response response1 =
      client.newCall(new Request.Builder().url(url.newBuilder("/script.js").build()).build()).execute();
    assertEquals(200, response1.code());
    assertTrue(response1.header("Content-Type").startsWith(MediaTypes.JAVASCRIPT.type()));
    assertEquals(text(new File(root, "script.js")), response1.body().string().trim());
    final okhttp3.Response response2 =
      client.newCall(new Request.Builder().url(url.newBuilder("/style.css").build()).build()).execute();
    assertEquals(200, response2.code());
    assertTrue(response2.header("Content-Type").startsWith(MediaTypes.CSS.type()));
    assertEquals(text(new File(root, "style.css")), response2.body().string().trim());
    final okhttp3.Response response3 =
      client.newCall(new Request.Builder().url(url.newBuilder("/img.png").build()).build()).execute();
    assertEquals(200, response3.code());
    assertTrue(response3.header("Content-Type").startsWith(MediaTypes.PNG.type()));
    assertEquals(String.valueOf(new File(root, "img.png").length()),
                 response3.header("Content-Length"));
    response3.body().close();
    final okhttp3.Response response4 =
      client.newCall(new Request.Builder().url(url.newBuilder().build()).build()).execute();
    assertEquals(200, response4.code());
    assertTrue(response4.header("Content-Type").startsWith(MediaTypes.HTML.type()));
    assertEquals(text(new File(root, "index.html")), response4.body().string().trim());
    final okhttp3.Response response5 =
      client.newCall(new Request.Builder().url(url.newBuilder("/missing").build()).build()).execute();
    assertEquals(404, response5.code());
    assertEquals("", response5.body().string());
    final okhttp3.Response response6 =
      client.newCall(new Request.Builder().url(url.newBuilder("/test.p12").build()).build()).execute();
    assertEquals(404, response6.code());
    assertEquals("", response6.body().string());
    final okhttp3.Response response7 =
      client.newCall(new Request.Builder().url(url.newBuilder("/dir/").build()).build()).execute();
    assertEquals(200, response7.code());
    assertTrue(response7.header("Content-Type").startsWith(MediaTypes.HTML.type()));
    assertEquals(text(new File(new File(root, "dir"), "index.htm")),
                 response7.body().string().trim());
    final okhttp3.Response response8 =
      client.newCall(new Request.Builder().url(url.newBuilder("/noindex/file.txt").build()).
        build()).execute();
    assertEquals(200, response8.code());
    assertTrue(response8.header("Content-Type").startsWith(MediaTypes.TEXT.type()));
    assertEquals(text(new File(new File(root, "noindex"), "file.txt")),
                 response8.body().string().trim());

    final okhttp3.Response response9 =
      client.newCall(new Request.Builder().url(url.newBuilder("/tests.html").build()).
        build()).execute();
    assertEquals(200, response9.code());
    assertTrue(response9.header("Content-Type").startsWith(MediaTypes.HTML.type()));
    assertEquals(text(new File(root, "tests.html")),
                 response9.body().string().trim());
  }

  @Test
  public void testETagHttp() throws IOException {
    testETag("http://localhost:8080/");
  }

  @Test
  public void testETagHttps() throws IOException {
    testETag("https://localhost:8181/");
  }

  static void testETag(final String baseUrl) throws IOException {
    final File root = getWebRoot();
    final HttpUrl url = HttpUrl.parse(baseUrl);
    final OkHttpClient client = client();
    final okhttp3.Response response1 =
      client.newCall(new Request.Builder().url(url.newBuilder("/script.js").build()).build()).execute();
    assertEquals(200, response1.code());
    final String etag = response1.header("ETag");
    assertNotNull(etag);
    assertEquals(text(new File(root, "script.js")), response1.body().string().trim());
    final okhttp3.Response response2 =
      client.newCall(new Request.Builder().
        url(url.newBuilder("/script.js").build()).
        addHeader("If-None-Match", etag).
        build()).execute();
    assertEquals(304, response2.code());
    assertEquals("", response2.body().string());
    final okhttp3.Response response3 =
      client.newCall(new Request.Builder().
        url(url.newBuilder("/script.js").build()).
        addHeader("If-None-Match", "123456").
        build()).execute();
    assertEquals(200, response3.code());
    assertEquals(text(new File(root, "script.js")), response3.body().string().trim());
    assertTrue(new File(root, "script.js").setLastModified(System.currentTimeMillis()));
    final okhttp3.Response response4 =
      client.newCall(new Request.Builder().
        url(url.newBuilder("/script.js").build()).
        addHeader("If-None-Match", etag).
        build()).execute();
    assertEquals(200, response4.code());
    assertEquals(text(new File(root, "script.js")), response4.body().string().trim());
  }

  @Test
  public void testRangeHttp() throws IOException {
    testRange("http://localhost:8080/");
  }

  @Test
  public void testRangeHttps() throws IOException {
    testRange("https://localhost:8181/");
  }

  static void testRange(final String baseUrl) throws IOException {
    final File root = getWebRoot();
    final File file = new File(root, "video.mp4");
    final byte[] bytes = Okio.buffer(Okio.source(file)).readByteArray();
    final HttpUrl url = HttpUrl.parse(baseUrl);
    final OkHttpClient client = client();

    final okhttp3.Response response1 =
      client.newCall(new Request.Builder().url(url.newBuilder("/video.mp4").build()).
        head().build()).execute();
    assertEquals(200, response1.code());
    assertTrue(response1.header("Content-Type").startsWith(MediaTypes.MP4.type()));
    assertEquals("bytes", response1.header("Accept-Ranges"));
    assertEquals(String.valueOf(bytes.length), response1.header("Content-Length"));
    response1.close();

    final okhttp3.Response response2 =
      client.newCall(new Request.Builder().url(url.newBuilder("/script.js").build()).
        head().build()).execute();
    assertEquals(200, response2.code());
    assertTrue(response2.header("Content-Type").startsWith(MediaTypes.JAVASCRIPT.type()));
    assertNull(response2.header("Accept-Ranges"));
    response2.close();

    final okhttp3.Response response3 =
      client.newCall(new Request.Builder().url(url.newBuilder("/video.mp4").build()).
        header("Range", "100-200").
        get().build()).execute();
    assertEquals(400, response3.code());
    response3.close();

    final okhttp3.Response response4 =
      client.newCall(new Request.Builder().url(url.newBuilder("/video.mp4").build()).
        header("Range", "bytes=100-200").
        get().build()).execute();
    assertEquals(206, response4.code());
    assertTrue(response4.header("Content-Type").startsWith(MediaTypes.MP4.type()));
    assertEquals("100", response4.header("Content-Length"));
    assertEquals("bytes 100-200/" + bytes.length, response4.header("Content-Range"));
    assertEquals(ByteString.of(bytes, 100, 100), response4.body().source().readByteString());
    response4.close();

    final okhttp3.Response response5 =
      client.newCall(new Request.Builder().url(url.newBuilder("/video.mp4").build()).
        header("Range", "bytes=-200").
        get().build()).execute();
    assertEquals(206, response5.code());
    assertTrue(response5.header("Content-Type").startsWith(MediaTypes.MP4.type()));
    assertEquals("200", response5.header("Content-Length"));
    assertEquals("bytes 0-200/" + bytes.length, response5.header("Content-Range"));
    assertEquals(ByteString.of(bytes, 0, 200), response5.body().source().readByteString());
    response5.close();

    final okhttp3.Response response6 =
      client.newCall(new Request.Builder().url(url.newBuilder("/video.mp4").build()).
        header("Range", "bytes=" + (bytes.length - 100) + "-").
        get().build()).execute();
    assertEquals(206, response6.code());
    assertTrue(response6.header("Content-Type").startsWith(MediaTypes.MP4.type()));
    assertEquals("100", response6.header("Content-Length"));
    assertEquals("bytes " + (bytes.length - 100) + "-" + bytes.length + "/" + bytes.length,
                 response6.header("Content-Range"));
    assertEquals(ByteString.of(bytes, bytes.length - 100, 100),
                 response6.body().source().readByteString());
    response6.close();

    final okhttp3.Response response7 =
      client.newCall(new Request.Builder().url(url.newBuilder("/video.mp4").build()).
        header("Range", "bytes=200-300, 500-700").
        get().build()).execute();
    assertEquals(206, response7.code());
    final String contentType = response7.header("Content-Type");
    assertTrue(contentType.startsWith(AcceptRanges.MULTIPART_TYPE.toString()));
    final int index = contentType.indexOf("boundary=");
    assertTrue(index != -1);
    final String boundary = contentType.substring(index + 9);
    final BufferedSource source = response7.body().source();
    int i=0;
    while (!source.readUtf8LineStrict().equals("--" + boundary)) {
      if (++i == 3) fail();
    }
    assertEquals("Content-Type: video/mp4", source.readUtf8LineStrict());
    assertEquals("Content-Range: bytes 200-300/" + bytes.length, source.readUtf8LineStrict());
    assertEquals("", source.readUtf8LineStrict());
    assertEquals(ByteString.of(bytes, 200, 100), source.readByteString(100));
    assertEquals("", source.readUtf8LineStrict());
    i=0;
    while (!source.readUtf8LineStrict().equals("--" + boundary)) {
      if (++i == 3) fail();
    }
    assertEquals("Content-Type: video/mp4", source.readUtf8LineStrict());
    assertEquals("Content-Range: bytes 500-700/" + bytes.length, source.readUtf8LineStrict());
    assertEquals("", source.readUtf8LineStrict());
    assertEquals(ByteString.of(bytes, 500, 200), source.readByteString(200));
    assertEquals("", source.readUtf8LineStrict());
    assertEquals("--" + boundary + "--", source.readUtf8LineStrict());
    response7.close();

    final okhttp3.Response response8 =
      client.newCall(new Request.Builder().url(url.newBuilder("/video.mp4").build()).
        header("Range", "bytes=16000-36000, -15000").
        get().build()).execute();
    assertEquals(206, response8.code());
    final String contentType2 = response8.header("Content-Type");
    assertTrue(contentType2.startsWith(AcceptRanges.MULTIPART_TYPE.toString()));
    final int index2 = contentType2.indexOf("boundary=");
    assertTrue(index2 != -1);
    final String boundary2 = contentType2.substring(index2 + 9);
    final BufferedSource source2 = response8.body().source();
    i=0;
    while (!source2.readUtf8LineStrict().equals("--" + boundary2)) {
      if (++i == 3) fail();
    }
    assertEquals("Content-Type: video/mp4", source2.readUtf8LineStrict());
    assertEquals("Content-Range: bytes 16000-36000/" + bytes.length, source2.readUtf8LineStrict());
    assertEquals("", source2.readUtf8LineStrict());
    assertEquals(ByteString.of(bytes, 16000, 20000), source2.readByteString(20000));
    assertEquals("", source2.readUtf8LineStrict());
    i=0;
    while (!source2.readUtf8LineStrict().equals("--" + boundary2)) {
      if (++i == 3) fail();
    }
    assertEquals("Content-Type: video/mp4", source2.readUtf8LineStrict());
    assertEquals("Content-Range: bytes 0-15000/" + bytes.length, source2.readUtf8LineStrict());
    assertEquals("", source2.readUtf8LineStrict());
    assertEquals(ByteString.of(bytes, 0, 15000), source2.readByteString(15000));
    assertEquals("", source2.readUtf8LineStrict());
    assertEquals("--" + boundary2 + "--", source2.readUtf8LineStrict());
    response8.close();
  }

  private static WebRequest req(final String url) {
    try {
      return new WebRequest(new URL(url));
    }
    catch (final MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static String text(final File file) {
    try {
      return new Scanner(file).useDelimiter("\\Z").next();
    }
    catch (final FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

}
