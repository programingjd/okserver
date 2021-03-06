package info.jdavid.ok.server.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import info.jdavid.ok.server.Response;
import okhttp3.HttpUrl;


/**
 * Handler that accepts request based on the request method and a regular expression for the request url path.
 * The regex captured groups are passed as parameters to the handle method.
 */
@SuppressWarnings({ "WeakerAccess", "unused" })
public abstract class RegexHandler implements Handler {

  final Pattern pattern;
  final List<String> methods;

  /**
   * Creates an handler that will accept a request with the specified methods,
   * whose path matches the specified regular expression.
   * @param methods the request methods.
   * @param regex the regular expression.
   */
  protected RegexHandler(final Collection<String> methods, final String regex) {
    if (methods.isEmpty()) {
      throw new NullPointerException("The accepted request method cannot be null.");
    }
    final List<String> list = this.methods = new ArrayList<>(methods.size());
    for (final String method: methods) {
      list.add(method.toUpperCase());
    }
    pattern = Pattern.compile(regex);
  }

  /**
   * Creates an handler that will accept a request with the specified method, whose path matches the specified
   * regular expression.
   * @param method the request method.
   * @param regex the regular expression.
   */
  protected RegexHandler(final String method, final String regex) {
    methods = Collections.singletonList(method.toUpperCase());
    pattern = Pattern.compile(regex);
  }

  @Override public Handler setup() { return this; }

  @Override
  public @Nullable String[] matches(final String method, final HttpUrl url) {
    if (methods.contains(method)) {
      final String encodedPath = url.encodedPath();
      final Matcher matcher = pattern.matcher(url.encodedPath());
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
   * Adds a check on the request methods and path to the specified handler.
   * @param methods the accepted methods.
   * @param regex the regular expression that the the url path should match.
   * @param delegate the delegate handler.
   * @return the handler with the additional requirements.
   */
  public static Handler create(final Collection<String> methods, final String regex, final Handler delegate) {
    return new RegexHandlerWrapper(methods, regex, delegate);
  }

  /**
   * Adds a check on the request method and path to the specified handler.
   * @param method the accepted method.
   * @param regex the regular expression that the the url path should match.
   * @param delegate the delegate handler.
   * @return the handler with the additional requirements.
   */
  public static Handler create(final String method, final String regex, final Handler delegate) {
    //noinspection Duplicates
    return new RegexHandlerWrapper(method, regex, delegate);
  }

  static class RegexHandlerWrapper extends RegexHandler {

    final Handler delegate;

    protected RegexHandlerWrapper(final Collection<String> methods, final String regex, final Handler delegate) {
      super(methods, regex);
      this.delegate = delegate;
    }

    protected RegexHandlerWrapper(final String method, final String regex, final Handler delegate) {
      super(method, regex);
      this.delegate = delegate;
    }

    @Override public Handler setup() {
      delegate.setup();
      return this;
    }

    @Override public String[] matches(final String method, final HttpUrl url) {
      return super.matches(method, url) == null ? null : delegate.matches(method, url);
    }

    @Override public Response.Builder handle(final Request request, final String[] params) {
      return delegate.handle(request, params);
    }

  }

}
