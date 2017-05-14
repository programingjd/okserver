package info.jdavid.ok.server.header;

import okhttp3.Headers;


/**
 * Accept-Encoding header.
 */
@SuppressWarnings({ "WeakerAccess" })
public final class AcceptEncoding {

  private AcceptEncoding() {}

  /**
   * Accept-Ranges header field name. Used by the server to specify that range requests are supported.
   */
  public static final String HEADER = "Accept-Encoding";

  public static final String CONTENT_ENCODING = "Content-Encoding";

  public static final String GZIP = "gzip";

  public static boolean supportsGZipEncoding(final Headers headers) {
    final String value = headers.get(HEADER);
    return value != null && value.contains(GZIP);
  }

}
