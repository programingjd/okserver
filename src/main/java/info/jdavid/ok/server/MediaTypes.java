package info.jdavid.ok.server;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.MediaType;


/**
 * List of predefined media types.
 */
@SuppressWarnings({ "unused", "WeakerAccess" })
public class MediaTypes {
  public static final MediaType CSS = MediaType.parse("text/css");
  public static final MediaType CSV = MediaType.parse("text/csv");
  public static final MediaType HTML = MediaType.parse("text/html");
  public static final MediaType TEXT = MediaType.parse("text/plain");
  public static final MediaType URI_LIST = MediaType.parse("text/uri-list");
  public static final MediaType XML = MediaType.parse("text/xml");

  public static final MediaType ATOM = MediaType.parse("application/atom+xml");
  public static final MediaType GZIP = MediaType.parse("application/gzip");
  public static final MediaType TAR = MediaType.parse("application/x-gtar");
  public static final MediaType XZ = MediaType.parse("application/x-xz");
  public static final MediaType SEVENZ = MediaType.parse("application/x-7z-compressed");
  public static final MediaType ZIP = MediaType.parse("application/zip");
  public static final MediaType RAR = MediaType.parse("application/vnd.rar");
  public static final MediaType JAR = MediaType.parse("application/java-archive");
  public static final MediaType APK = MediaType.parse("application/vnd.android.package-archive");
  public static final MediaType DMG = MediaType.parse("application/x-apple-diskimage");
  public static final MediaType ISO = MediaType.parse("application/x-iso9660-image");
  public static final MediaType JAVASCRIPT = MediaType.parse("application/javascript");
  public static final MediaType JSON = MediaType.parse("application/json");
  public static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
  public static final MediaType PDF = MediaType.parse("application/pdf");
  public static final MediaType WOFF = MediaType.parse("application/font-woff");
  public static final MediaType WOFF2 = MediaType.parse("font/woff2");
  public static final MediaType XHTML = MediaType.parse("application/xhtml+xml");
  public static final MediaType WEB_MANIFEST = MediaType.parse("application/manifest+json");

  public static final MediaType OTF = MediaType.parse("application/opentype");
  public static final MediaType TTF = MediaType.parse("application/truetype");
  public static final MediaType EOT = MediaType.parse("application/vnd.ms-fontobject");

  public static final MediaType WAV = MediaType.parse("audio/x-wav");
  public static final MediaType MP3 = MediaType.parse("audio/mpeg");
  public static final MediaType OGG = MediaType.parse("audio/ogg");

  public static final MediaType MP4 = MediaType.parse("video/mp4");
  public static final MediaType OGV = MediaType.parse("video/ogg");
  public static final MediaType WEBM = MediaType.parse("video/webm");

  public static final MediaType EMAIL = MediaType.parse("message/rfc822");

  public static final MediaType BYTE_RANGES = MediaType.parse("multipart/byteranges");
  public static final MediaType DIGEST = MediaType.parse("multipart/digest");
  public static final MediaType FORM_DATA = MediaType.parse("multipart/form-data");
  public static final MediaType MIXED = MediaType.parse("multipart/mixed");
  public static final MediaType SIGNED = MediaType.parse("multipart/signed");
  public static final MediaType ENCRYPTED = MediaType.parse("multipart/encrypted");

  public static final MediaType JPG = MediaType.parse("image/jpeg");
  public static final MediaType PNG = MediaType.parse("image/png");
  public static final MediaType GIF = MediaType.parse("image/gif");
  public static final MediaType BMP = MediaType.parse("image/bmp");
  public static final MediaType SVG = MediaType.parse("image/svg+xml");
  public static final MediaType ICO = MediaType.parse("image/x-icon");
  public static final MediaType WEBP = MediaType.parse("image/webp");

  public static final MediaType SSE = MediaType.parse("text/event-stream");

  public static final MediaType DIRECTORY = MediaType.parse("text/directory");

  private static final Map<String, MediaType> EXTENSIONS = initMap();
  private static Map<String, MediaType> initMap() {
    final Map<String, MediaType> map = new HashMap<String, MediaType>(64);
    map.put("html", HTML);
    map.put("css", CSS);
    map.put("js", JAVASCRIPT);
    map.put("jpg", JPG);
    map.put("png", PNG);
    map.put("htm", HTML);
    map.put("xhtml", XHTML);
    map.put("woff", WOFF);
    map.put("woff2", WOFF2);
    map.put("webmanifest", WEB_MANIFEST);
    map.put("otf", OTF);
    map.put("ttf", TTF);
    map.put("eot", EOT);
    map.put("gif", GIF);
    map.put("bmp", BMP);
    map.put("svg", SVG);
    map.put("ico", ICO);
    map.put("webp", WEBP);

    map.put("csv", CSV);
    map.put("txt", TEXT);
    map.put("howto", TEXT);
    map.put("readme", TEXT);
    map.put("xml", XML);
    map.put("xsl", XML);
    map.put("gz", GZIP);
    map.put("tar", TAR);
    map.put("xz", XZ);
    map.put("7z", SEVENZ);
    map.put("zip", ZIP);
    map.put("rar", RAR);
    map.put("jar", JAR);
    map.put("apk", APK);
    map.put("dmg", DMG);
    map.put("iso", ISO);
    map.put("json", JSON);
    map.put("bin", OCTET_STREAM);
    map.put("pdf", PDF);

    map.put("wav", WAV);
    map.put("mp3", MP3);
    map.put("ogg", OGG);
    map.put("mp4", MP4);
    map.put("ogv", OGV);
    map.put("webm", WEBM);
    return map;
  }

  public static MediaType fromUrl(final HttpUrl url) {
    if (url == null) return null;
    final List<String> segments = url.pathSegments();
    final int last = segments.size() - 1;
    if (last < 0) return DIRECTORY;
    final String path = segments.get(last);
    int i = path.length();
    if (i == 0) return DIRECTORY;
    int n = 0;
    while (i-- > 0 && ++n < 16) {
      switch (path.charAt(i)) {
        case '.':
          final String ext = path.substring(i+1).toLowerCase();
          return EXTENSIONS.get(ext);
      }
    }
    if (url.encodedPath().startsWith("/.well-known/acme-challenge/")) {
      return MediaTypes.TEXT;
    }
    return null;
  }

  public static MediaType fromFile(final File file) {
    if (file == null) return null;
    if (file.isDirectory()) return DIRECTORY;
    final String filename = file.getName();
    if (".".equals(filename) || "..".equals(filename)) return DIRECTORY;
    final int i = filename.lastIndexOf('.');
    if (i == -1) {
      if (!file.isFile()) {
        return DIRECTORY; // files with no extension that don't exist (yet) are treated as directories.
      }
      final File parent = file.getParentFile();
      if (parent != null && "acme-challenge".equals(parent.getName())) {
        return MediaTypes.TEXT; // return text/plain for certificate acme-challenge files.
      }
      return null;
    }
    final String ext = filename.substring(i+1).toLowerCase();
    return EXTENSIONS.get(ext);
  }

}
