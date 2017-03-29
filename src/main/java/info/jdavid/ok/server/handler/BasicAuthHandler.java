package info.jdavid.ok.server.handler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.HttpUrl;
import okio.Buffer;

/**
 * Handler that adds basic auth to another handler.
 */
public class BasicAuthHandler implements Handler {

  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
  private static final String DIGEST = "Basic realm=\"User Visible Realm\"";

  private final Handler mDelegate;
  private Set<String> mCredentials;

  /**
   * Creates a new handler with basic auth for the specified list of username/password that delegates to the
   * specified handler.
   * @param credentials a map of username to password.
   * @param delegate the delegate handler.
   */
  public BasicAuthHandler(final Map<String, String> credentials, final Handler delegate) {
    if (delegate == null) throw new NullPointerException("The delegate handler cannot be null.");
    mDelegate = delegate;
    mCredentials = credentials == null ? Collections.<String>emptySet() : credentials(credentials);
  }

  @Override
  public String[] matches(final String method, final HttpUrl url) {
    return mDelegate.matches(method, url);
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final String auth = request.headers.get("Authorization");
    if (auth == null || !auth.startsWith("Basic ") || !mCredentials.contains(auth)) {
      return new Response.Builder().statusLine(StatusLines.UNAUTHORIZED).
        addHeader(WWW_AUTHENTICATE, DIGEST).noBody();
    }
    else {
      return mDelegate.handle(request, params);
    }
  }

  private static Set<String> credentials(final Map<String, String> credentials) {
    final Base64Helper helper = new Base64Helper();
    final Set<String> set = new HashSet<String>(credentials.size());
    for (final Map.Entry<String, String> entry: credentials.entrySet()) {
      final String user = entry.getKey();
      final String password = entry.getValue();
      if (user != null) {
        set.add("Basic " + helper.encode(user + ":" + (password == null ? "" : password)));
      }
    }
    return set;
  }

  private static class Base64Helper extends AbstractPreferences {

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
