package info.jdavid.ok.server.handler;

import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.header.CacheControl;
import okhttp3.HttpUrl;
import okio.Buffer;


/**
 * Abstract class for Handlers handling user authentication.
 */
@SuppressWarnings({ "WeakerAccess" })
public abstract class AuthHandler implements Handler {

  final Handler delegate;

  /**
   * Adds authentication on top of the specified handler.
   * @param delegate the handler responsible for serving authenticated requests.
   */
  public AuthHandler(final Handler delegate) {
    super();
    if (delegate == null) throw new NullPointerException("The delegate handler cannot be null.");
    this.delegate = delegate;
  }

  @Override
  public Handler setup() {
    delegate.setup();
    return this;
  }

  @Override
  public String[] matches(final String method, final HttpUrl url) {
    return delegate.matches(method, url);
  }

  /**
   * Forwards the requests to the delegate after having adjusted the Cache-Control headers if necessary.
   * @param request the request object.
   * @param params the params returned by the accept method.
   * @return the response builder.
   */
  protected Response.Builder handleAuthenticated(final Request request, final String[] params) {
    final Response.Builder response = delegate.handle(request, params);
    final String cacheControl = response.header(CacheControl.HEADER);
    final String expires =  response.header(CacheControl.EXPIRES);
    final String modifiedCacheControl =
      modifiedCacheControlValue(request.method, response.code(), cacheControl, expires);
    if (modifiedCacheControl != null) {
      response.header(CacheControl.HEADER, modifiedCacheControl);
    }
    return response;
  }

  /**
   * Adjusts the Cache-Control header value if needed. Responses that are cacheable, but not explicitly
   * marked as being cacheable publicly are marked as cacheable privately (only for the current user).
   * @param requestMethod the request method.
   * @param responseCode the response status code.
   * @param cacheControlHeaderValue the value of the Cache-Control header (can be null).
   * @param expiresHeaderValue the value of the Expires header (can be null).
   * @return a modified Cache-Control header value, or null if it doesn't need to be changed.
   */
  protected String modifiedCacheControlValue(final String requestMethod, final int responseCode,
                                             final String cacheControlHeaderValue,
                                             final String expiresHeaderValue) {
    boolean cacheable = expiresHeaderValue != null ||
      (cacheableByDefaultRequestMethod(requestMethod) && cacheableByDefaultStatusCode(responseCode));
    if (cacheControlHeaderValue == null) {
      return cacheable ? "private" : null;
    }
    final String[] directives = cacheControlHeaderValue.split(", ");
    for (final String directive: directives) {
      if (CacheControl.Directive.NO_STORE.equals(directive)) return null;
      if (CacheControl.Directive.PRIVATE.equals(directive)) return null;
      if (CacheControl.Directive.PUBLIC.equals(directive)) return null;
      if (!cacheable) {
        if (directive.startsWith(CacheControl.Directive.MAX_AGE_EQUALS) ||
            directive.startsWith(CacheControl.Directive.S_MAX_AGE_EQUALS) ||
            directive.startsWith(CacheControl.Directive.STALE_WHILE_REVALIDATE_EQUALS) ||
            directive.startsWith(CacheControl.Directive.STALE_IF_ERROR_EQUALS)) {
          cacheable = true;
        }
      }
    }
    return cacheable ? "private, " + cacheControlHeaderValue : null;
  }

  /**
   * Returns whether a response with the specified status code is cacheable by default or not.
   * @param code the status code.
   * @return true if the response is cacheable by default.
   */
  protected boolean cacheableByDefaultStatusCode(final int code) {
    return code == 200 || code == 206 || code == 300 || code == 301 || code == 308;
  }

  protected boolean cacheableByDefaultRequestMethod(final String method) {
    return "GET".equals(method);
  }

  static class Base64Helper extends AbstractPreferences {

    Base64Helper() {
      super(null, "");
    }

    private String mValue = null;
    private Buffer buffer = new Buffer();

    String encode(final String str) {
      putByteArray(null, buffer.writeUtf8(str).readByteArray());
      final String value = mValue;
      mValue = null;
      return value;
    }

    @Override public void put(final String key, final String value) {
      mValue = value;
    }

    @Override protected void putSpi(final String key, final String value) {}
    @Override protected String getSpi(final String key) { return null; }
    @Override protected void removeSpi(final String key) {}
    @Override protected void removeNodeSpi() throws BackingStoreException {}
    @Override protected String[] keysSpi() throws BackingStoreException { return new String[0]; }
    @Override protected String[] childrenNamesSpi() throws BackingStoreException { return new String[0]; }
    @Override protected AbstractPreferences childSpi(final String name) { return null; }
    @Override protected void syncSpi() throws BackingStoreException {}
    @Override protected void flushSpi() throws BackingStoreException {}
  }

}
