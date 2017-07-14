package info.jdavid.ok.server.header;

/**
 * Connection header.
 */
@SuppressWarnings({ "WeakerAccess" })
public final class Connection {

  private Connection() {}

  public static final String HEADER = "Connection";

  public static final String CLOSE = "Close";
  public static final String KEEP_ALIVE = "Keep-Alive";

}
