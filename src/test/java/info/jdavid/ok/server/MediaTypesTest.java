package info.jdavid.ok.server;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import org.junit.Test;

import static org.junit.Assert.*;


public class MediaTypesTest {

  @Test
  public void testFromUrl() {
    test("css", MediaTypes.CSS);
    test("csv", MediaTypes.CSV);
    test("html", MediaTypes.HTML);
    test("htm", MediaTypes.HTML);
    test("txt", MediaTypes.TEXT);
    test("howto", MediaTypes.TEXT);
    test("readme", MediaTypes.TEXT);
    test("xml", MediaTypes.XML);
    test("xsl", MediaTypes.XML);
    test("gz", MediaTypes.GZIP);
    test("tar", MediaTypes.TAR);
    test("zip", MediaTypes.ZIP);
    test("js", MediaTypes.JAVASCRIPT);
    test("json", MediaTypes.JSON);
    test("bin", MediaTypes.OCTET_STREAM);
    test("pdf", MediaTypes.PDF);
    test("woff", MediaTypes.WOFF);
    test("xhtml", MediaTypes.XHTML);
    test("webmanifest", MediaTypes.WEB_MANIFEST);
    test("otf", MediaTypes.OTF);
    test("ttf", MediaTypes.TTF);
    test("wav", MediaTypes.WAV);
    test("mp3", MediaTypes.MP3);
    test("mp4", MediaTypes.MP4);
    test("jpg", MediaTypes.JPG);
    test("png", MediaTypes.PNG);
    test("gif", MediaTypes.GIF);
    test("bmp", MediaTypes.BMP);
    test("svg", MediaTypes.SVG);
    test("ico", MediaTypes.ICO);
    test("webp", MediaTypes.WEBP);
  }

  private static void test(final String extension, final MediaType expected) {
    final HttpUrl url =
      new HttpUrl.Builder().scheme("https").host("test.com").addPathSegment("example." + extension).build();
    assertEquals(expected, MediaTypes.fromUrl(url));
  }

}
