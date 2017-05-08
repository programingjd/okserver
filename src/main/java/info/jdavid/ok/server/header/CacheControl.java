package info.jdavid.ok.server.header;


/**
 * Cache-Control header.
 */
public final class CacheControl {

  private CacheControl() {}

  /**
   * Cache-Control header field name.
   */
  public static final String HEADER = "Cache-Control";
  /**
   * Expires header field name.
   */
  public static final String EXPIRES = "Expires";


  /**
   * Cache-Control directive.
   */
  public static class Directive {

    private Directive() {}

    /**
     * The response should not be stored in the cache.
     */
    public static final String NO_STORE = "no-store";
    /**
     * The response should be stored as-is in the cache. No transformation (usually by a proxy) is allowed.
     */
    public static final String NO_TRANSFORM = "no-transform";
    /**
     * The response can be cached publicly, including in shared caches (at the proxy level).
     */
    public static final String PUBLIC = "public";
    /**
     * The response can only be cached for the current user, but not in shared caches (at the proxy level).
     */
    public static final String PRIVATE = "private";

    /**
     * A client should revalidate the response with the server before returning the cached response.
     */
    public static final String NO_CACHE = "no-cache";
    /**
     * A client should revalidate a stale cached response (max-age expired) with the server
     * before returning it.
     */
    public static final String MUST_REVALIDATE = "must-revalidate";
    /**
     * Same as must-revalidate, but only for public (shared) caches. It does not apply to private cache.
     */
    public static final String PROXY_REVALIDATE = "proxy-revalidate";
    /**
     * A client should response with a cached response, or with a 504 (gateway timeout).
     */
    public static final String ONLY_IF_CACHED = "only-if-cached";
    /**
     * A client should consider the response stale once the max-age is expired.
     */
    public static final String MAX_AGE_EQUALS = "max-age=";
    /**
     * s-maxage overrides max-age and the Expires header value for public (shared) caches.
     */
    public static final String S_MAX_AGE_EQUALS = "s-maxage=";
    /**
     * A clients should revalidate a stale cached response (max-age expired) with the server, but it can
     * return the cached response if its max-age is lower than the specified limit.
     */
    public static final String STALE_WHILE_REVALIDATE_EQUALS = "stale-while-revalidate=";
    /**
     * A client should revalidate a stale cached response (max-age expired) with the server, but it can
     * return the cached response if thevalidation call fails and the cached response max-age is lower than
     * the specified limit.
     */
    public static final String STALE_IF_ERROR_EQUALS = "stale-if-error=";
    /**
     * A client should consider that a cached response that is not stale has not changed and should not
     * revalidate it with the server.
     */
    public static final String IMMUTABLE = "immutable";

  }

}
