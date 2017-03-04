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
    test("xz", MediaTypes.XZ);
    test("7z", MediaTypes.SEVENZ);
    test("zip", MediaTypes.ZIP);
    test("rar", MediaTypes.RAR);
    test("jar", MediaTypes.JAR);
    test("apk", MediaTypes.APK);
    test("dmg", MediaTypes.DMG);
    test("iso", MediaTypes.ISO);
    test("js", MediaTypes.JAVASCRIPT);
    test("json", MediaTypes.JSON);
    test("bin", MediaTypes.OCTET_STREAM);
    test("pdf", MediaTypes.PDF);
    test("woff", MediaTypes.WOFF);
    test("woff2", MediaTypes.WOFF2);
    test("xhtml", MediaTypes.XHTML);
    test("webmanifest", MediaTypes.WEB_MANIFEST);
    test("otf", MediaTypes.OTF);
    test("ttf", MediaTypes.TTF);
    test("eot", MediaTypes.EOT);
    test("wav", MediaTypes.WAV);
    test("mp3", MediaTypes.MP3);
    test("ogg", MediaTypes.OGG);
    test("mp4", MediaTypes.MP4);
    test("ogv", MediaTypes.OGV);
    test("webm", MediaTypes.WEBM);
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
