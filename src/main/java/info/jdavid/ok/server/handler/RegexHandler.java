package info.jdavid.ok.server.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.jdavid.ok.server.RequestHandler;
import info.jdavid.ok.server.Response;
import okhttp3.HttpUrl;


/**
 * Handler that accepts request based on the request method and a regular expression for the request url path.
 * The regex captured groups are passed as parameters to the handle method.
 */
public abstract class RegexHandler implements Handler {

  private final Pattern mPattern;
  private final String mMethod;

  /**
   * Creates an handler that will accept a request with the specified method, whose path matches the specified
   * regular expression.
   * @param method the request method.
   * @param regex the regular expression.
   */
  RegexHandler(final String method, final String regex) {
    if (method == null) throw new NullPointerException("The accepted request method cannot be null.");
    if (regex == null) throw new NullPointerException("The regular expression cannot be null.");
    mMethod = method.toUpperCase();
    mPattern = Pattern.compile(regex);
  }

  @Override
  public String[] matches(final String method, final HttpUrl url) {
    if (mMethod.equals(method)) {
      final String encodedPath = url.encodedPath();
      final Matcher matcher = mPattern.matcher(url.encodedPath());
      if (matcher.find()) {
        if (matcher.start() > 0) return null;
        if (matcher.end() < encodedPath.length()) return null;
        final int n = matcher.groupCount();
        final String[] params = new String[n];
        for (int i=0; i<n; ++i) {
          params[i] = matcher.group(i + 1);
        }
        return params;
      }
    }
    return null;
  }

  /**
   * Adds a check on the request method and path to the specified handler.
   * @param method the accepted method.
   * @param regex the regular expression that the the url path should match.
   * @param delegate the delegate handler.
   * @return the handler with the additional requirements.
   */
  public static Handler create(final String method, final String regex, final Handler delegate) {
    if (delegate == null) throw new NullPointerException("The delegate handler cannot be null.");
    return new RegexHandler(method, regex) {
      @Override public String[] matches(final String method, final HttpUrl url) {
        return super.matches(method, url) == null ? null : delegate.matches(method, url);
      }
      @Override public Response.Builder handle(final Request request, final String[] params) {
        return delegate.handle(request, params);
      }
    };
  }

}
