package info.jdavid.ok.server;

import com.squareup.okhttp.MediaType;


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
  public static final MediaType ZIP = MediaType.parse("application/zip");
  public static final MediaType JAVASCRIPT = MediaType.parse("application/javascript");
  public static final MediaType JSON = MediaType.parse("application/json");
  public static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
  public static final MediaType PDF = MediaType.parse("application/pdf");
  public static final MediaType WOFF = MediaType.parse("application/font-woff");
  public static final MediaType XHTML = MediaType.parse("application/xhtml+xml");

  public static final MediaType OTF = MediaType.parse("application/opentype");
  public static final MediaType TTF = MediaType.parse("application/truetype");

  public static final MediaType WAV = MediaType.parse("audio/x-wav");
  public static final MediaType MP3 = MediaType.parse("audio/mpeg");

  public static final MediaType MP4 = MediaType.parse("video/mp4");

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
}
