package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Https;
import info.jdavid.ok.server.RequestHandlerChain;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static info.jdavid.ok.server.handler.FileHandlerTest.testETag;
import static info.jdavid.ok.server.handler.FileHandlerTest.testFiles;
import static info.jdavid.ok.server.handler.FileHandlerTest.testIndex;
import static info.jdavid.ok.server.handler.FileHandlerTest.testRange;
import static info.jdavid.ok.server.handler.FileHandlerTest.testWeb;

import static org.junit.Assert.*;


public class PreCachedFileHandlerTest {

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  private static PreCachedFileHandler handler;

  @BeforeClass
  public static void startServer() throws IOException {
    final File root = getWebRoot();
    final File certFile = new File(root, "test.p12");
    assertTrue(certFile.isFile());
    final byte[] cert = new byte[(int)certFile.length()];
    try (final RandomAccessFile raf = new RandomAccessFile(certFile, "r")) {
      raf.readFully(cert);
    }
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
      }.add(handler = new PreCachedFileHandler(root))).
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
  public void testCache() {
    assertEquals(9, handler.cache.size());
    final PreCachedFileHandler.Data videoData = handler.cache.get("/video.mp4");
    assertNotNull(videoData);
    assertTrue(videoData.bytes.length > 0);
    final PreCachedFileHandler.Data styleData = handler.cache.get("/style.css");
    assertNotNull(styleData);
    assertTrue(styleData.bytes.length > 0);
    final PreCachedFileHandler.Data textData = handler.cache.get("/noindex/file.txt");
    assertNotNull(textData);
    assertTrue(textData.bytes.length > 0);
  }

  @Test
  public void testWebHttp() throws Exception {
    testWeb("http://localhost:8080/");
  }

  @Test
  public void testWebHttps() throws Exception {
    testWeb("https://localhost:8181/");
  }

  @Test
  public void testIndexHttp() throws IOException {
    testIndex("http://localhost:8080/");
  }

  @Test
  public void testIndexHttps() throws IOException {
    testIndex("https://localhost:8181/");
  }

  @Test
  public void testFilesHttp() throws IOException {
    testFiles("http://localhost:8080/");
  }

  @Test
  public void testFilesHttps() throws IOException {
    testFiles("https://localhost:8181/");
  }

  @Test
  public void testETagHttp() throws IOException {
    testETag("http://localhost:8080/");
  }

  @Test
  public void testETagHttps() throws IOException {
    testETag("https://localhost:8181/");
  }

  @Test
  public void testRangeHttp() throws IOException {
    testRange("http://localhost:8080/");
  }

  @Test
  public void testRangeHttps() throws IOException {
    testRange("https://localhost:8181/");
  }

}
