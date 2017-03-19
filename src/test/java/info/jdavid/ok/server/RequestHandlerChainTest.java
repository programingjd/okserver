package info.jdavid.ok.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.Cache;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Test;

import static org.junit.Assert.*;


public class RequestHandlerChainTest {

  @Test
  public void testFiles() throws Exception {
    final File projectDir = new File(".").getCanonicalFile();
    final File root = new File(new File(new File(projectDir, "src"), "test"), "resources");
    assertTrue(root.isDirectory());
    final File certFile = new File(root, "test.p12");
    assertTrue(certFile.isFile());

    RequestHandlerChain.main(new String[] { "--root", root.getPath() });
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

      final HtmlPage page = web.getPage("http://localhost:8080/");
      assertEquals(200, page.getWebResponse().getStatusCode());
      final Cache cache = web.getCache();
      assertEquals(3, cache.getSize());
      final WebResponse imgResponse = cache.getCachedResponse(req("http://localhost:8080/img.png"));
      assertEquals(200, imgResponse.getStatusCode());
      assertEquals(new File(root, "img.png").length(), imgResponse.getContentLength());
      final WebResponse jsResponse = cache.getCachedResponse(req("http://localhost:8080/script.js"));
      assertEquals(200, jsResponse.getStatusCode());
      assertEquals(text(new File(root, "script.js")), jsResponse.getContentAsString().trim());
      final WebResponse htmlResponse = cache.getCachedResponse(req("http://localhost:8080/"));
      assertEquals(200, htmlResponse.getStatusCode());
      assertEquals(text(new File(root, "index.html")), htmlResponse.getContentAsString().trim());

      cache.clear();
      web.getPage("http://localhost:8080/index.html");
      assertEquals(1, cache.getSize());
      final WebResponse indexResponse = cache.getCachedResponse(req("http://localhost:8080/index.html"));
      assertEquals(308, indexResponse.getStatusCode());
      assertEquals("http://localhost:8080/", indexResponse.getResponseHeaderValue("Location"));
    }
    finally {
      web.close();
    }
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
