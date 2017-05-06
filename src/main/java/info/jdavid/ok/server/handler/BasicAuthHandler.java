package info.jdavid.ok.server.handler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;


/**
 * Handler that adds basic auth to another handler.
 */
public class BasicAuthHandler extends AuthHandler {

  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
  private static final String DIGEST = "Basic realm=\"User Visible Realm\"";

  Set<String> credentials;

  /**
   * Creates a new handler with basic auth for the specified list of username/password that delegates to the
   * specified handler.
   * @param credentials a map of username to password.
   * @param delegate the delegate handler.
   */
  public BasicAuthHandler(final Map<String, String> credentials, final Handler delegate) {
    super(delegate);
    this.credentials = credentials == null ? Collections.<String>emptySet() : credentials(credentials);
  }

  /**
   * Checks if the credentials are valid.
   * @param auth the authorization value ("Basic base64(user:password)").
   * @return true if the credentials are valid.
   */
  protected boolean areCredentialsValid(final String auth) {
    return credentials.contains(auth);
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final String auth = request.headers.get("Authorization");
    if (auth != null && auth.startsWith("Basic ") && areCredentialsValid(auth)) {
      return handleAuthenticated(request, params);
    }
    else {
      return new Response.Builder().statusLine(StatusLines.UNAUTHORIZED).
        addHeader(WWW_AUTHENTICATE, DIGEST).noBody();
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

}
