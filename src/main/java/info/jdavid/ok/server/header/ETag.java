package info.jdavid.ok.server.header;


/**
 * ETag header.
 */
@SuppressWarnings({ "WeakerAccess" })
public final class ETag {

  private ETag() {}

  public static final String HEADER = "ETag";

  public static final String IF_MATCH = "If-Match";
  public static final String IF_NONE_MATCH = "If-None-Match";

}
