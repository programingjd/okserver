package info.jdavid.ok.server.header;


/**
 * Accept-Ranges header.
 */
public final class AcceptRanges {

  private AcceptRanges() {}

  /**
   * Accept-Ranges header field name. Used by the server to specify that range requests are supported.
   */
  public static final String HEADER = "Accept-Ranges";

  /**
   * Content-Range header field name. Used by the server to specify the range returned.
   */
  public static final String CONTENT_RANGE = "Content-Range";

  public static final String BYTES = "bytes";

  public static final String RANGE = "Range";

  public static final String IF_RANGE = "If-Range";

}
