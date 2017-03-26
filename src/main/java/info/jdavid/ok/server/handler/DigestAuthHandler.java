package info.jdavid.ok.server.handler;

import java.util.Collections;
import java.util.Map;

import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.HttpUrl;

/**
 * Handler that adds digest auth to another handler.
 */
public class DigestAuthHandler implements Handler {

  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

  private final Handler mDelegate;
  private final String mName;
  private Map<String, String> mCredentials;

  /**
   * Creates a new handler with digest auth for the specified list of username/password that delegates to the
   * specified handler.
   * @param credentials a map of username to password.
   * @param delegate the delegate handler.
   */
  public DigestAuthHandler(final Map<String, String> credentials, final Handler delegate) {
    this(credentials, "digest", delegate);
  }

  /**
   * Creates a new handler with digest auth for the specified list of username/password that delegates to the
   * specified handler.
   * @param credentials a map of username to password.
   * @param digestName the name of the realm.
   * @param delegate the delegate handler.
   */
  public DigestAuthHandler(final Map<String, String> credentials, final String digestName,
                           final Handler delegate) {
    if (delegate == null) throw new NullPointerException("The delegate handler cannot be null.");
    mName = digestName;
    mDelegate = delegate;
    mCredentials = credentials == null ? Collections.<String, String>emptyMap() : credentials;
  }

  @Override
  public String[] matches(final String method, final HttpUrl url) {
    return mDelegate.matches(method, url);
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final String auth = request.headers.get("Authorization");
    if (auth == null || !auth.startsWith("Digest ") || !validCredentials(request)) {
      final String nonce = nonce();
      final String opaque = opaque();
      final String realm = "Digest realm=\"" + mName +"@" + request.url.host() + ", qop=\"auth\", nonce=\"" +
                           nonce + "\", opaque=\"" + opaque + "\"";
      return new Response.Builder().statusLine(StatusLines.UNAUTHORIZED).
        addHeader(WWW_AUTHENTICATE, realm).noBody();
    }
    else {
      return mDelegate.handle(request, params);
    }
  }

  private static String nonce() {
    throw new UnsupportedOperationException();
  }

  private static String opaque() {
    throw new UnsupportedOperationException();
  }

  private boolean validCredentials(final Request request) {
    return false;
  }

}
