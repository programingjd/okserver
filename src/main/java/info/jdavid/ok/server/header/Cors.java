package info.jdavid.ok.server.header;


/**
 * Cross origin headers.
 */
@SuppressWarnings({ "WeakerAccess" })
public final class Cors {

  private Cors() {}

  public static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
  public static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
  public static final String MAX_AGE = "Access-Control-Max-Age";
  public static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";

}
