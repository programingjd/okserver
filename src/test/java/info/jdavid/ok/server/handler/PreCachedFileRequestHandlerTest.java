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

import static info.jdavid.ok.server.handler.FileRequestHandlerTest.testETag;
import static info.jdavid.ok.server.handler.FileRequestHandlerTest.testFiles;
import static info.jdavid.ok.server.handler.FileRequestHandlerTest.testIndex;
import static info.jdavid.ok.server.handler.FileRequestHandlerTest.testRange;
import static info.jdavid.ok.server.handler.FileRequestHandlerTest.testWeb;
import static org.junit.Assert.assertTrue;

public class PreCachedFileRequestHandlerTest {

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
      }.add(new PreCachedFileRequestHandler(root))).
      start();
  }

  @AfterClass
  public static void stopServer() {
    SERVER.shutdown();
  }

  static File getWebRoot() throws IOException {
    final File projectDir = new File(".").getCanonicalFile();
    final File root = new File(new File(new File(projectDir, "src"), "test"), "resources");
    assertTrue(root.isDirectory());
    return root;
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
