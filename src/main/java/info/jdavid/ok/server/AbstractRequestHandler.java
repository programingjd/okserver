package info.jdavid.ok.server;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;


/**
 * Abstract implementation of a request handler with sensible (and overridable) defaults.
 */
@SuppressWarnings({ "WeakerAccess" })
public abstract class AbstractRequestHandler implements RequestHandler {

  @Override
  public final Response handle(final String clientIp,
                               final boolean secure, final boolean insecureOnly, final boolean http2,
                               final String method, final HttpUrl url, final Headers requestHeaders,
                               final Buffer requestBody) {
    if (acceptClientIp(clientIp)) {
      if (secure) {
        return handle(clientIp, http2, method, url, requestHeaders, requestBody);
      }
      else {
        if (isAcmeChallenge(method, url, requestHeaders)) {
          return handleAcmeChallenge(clientIp, method, url, requestHeaders, requestBody);
        }
        else if (allowInsecure(method, url, requestHeaders, insecureOnly)) {
          return handle(clientIp, http2, method, url, requestHeaders, requestBody);
        }
        else {
          return handleDisallowedInsecureRequest(method, url, insecureOnly);
        }
      }
    }
    else {
      return handleBlockedClientIp(method, url);
    }
  }

  /**
   * Handles an acme challenge request.
   * @param clientIp the request client ip.
   * @param method the request method.
   * @param url the challenge request url.
   * @param requestHeaders the challenge request headers.
   * @param requestBody the challenge request body.
   * @return the challenge response.
   */
  protected abstract Response handleAcmeChallenge(final String clientIp,
                                                  final String method,
                                                  final HttpUrl url,
                                                  final Headers requestHeaders,
                                                  final Buffer requestBody);

  /**
   * Handles a request once the request validation has been performed.
   * @param clientIp the request client ip.
   * @param http2 true if the request is using http 2 (h2).
   * @param method the request method.
   * @param url the request url.
   * @param requestHeaders the request headers.
   * @param requestBody the request body.
   * @return the response.
   */
  protected abstract Response handle(final String clientIp, final boolean http2,
                                     final String method, final HttpUrl url,
                                     final Headers requestHeaders, final Buffer requestBody);


  private static final Response FORBIDDEN =
    new Response.Builder().statusLine(StatusLines.FORBIDDEN).noBody().build();

  /**
   * Hook for performing initialization tasks.
   */
  protected void init() {}

  /**
   * Returns whether the client ip is allowed (not blacklisted) or not.
   * @param clientIp the client ip to validate.
   * @return true if the client ip is allowed, false if it is blacklisted.
   */
  @SuppressWarnings("unused")
  protected boolean acceptClientIp(final String clientIp) {
    return true;
  }

  /**
   * Creates the response for blacklisted client ips. (403 FORBIDDEN by default).
   * @param method the request method (get, post, ...).
   * @param url the request url.
   * @return the response.
   */
  @SuppressWarnings("unused")
  protected Response handleBlockedClientIp(final String method, final HttpUrl url) {
    return FORBIDDEN;
  }


  /**
   * Returns whether the request is allowed to be insecure (http rather than https) or not.
   * @param method the request method (get, post, ...).
   * @param url the request url.
   * @param requestHeaders the request headers.
   * @param insecureOnly whether the server accepts only insecure connections or whether https is enabled.
   * @return whether the insecure request is allowed or not.
   */
  protected boolean allowInsecure(final String method, final HttpUrl url, final Headers requestHeaders,
                                  final boolean insecureOnly) {
    return insecureOnly || isAcmeChallenge(method, url, requestHeaders);
  }

  /**
   * Returns whether the request is an acme challenge (domain owner verification for certificates like
   * let's encrypt).
   * @param method the request method.
   * @param url the request url.
   * @param requestHeaders the request headers.
   * @return true if the url is for an acme challenge, false if it isn't.
   */
  @SuppressWarnings("unused")
  protected boolean isAcmeChallenge(final String method, final HttpUrl url, final Headers requestHeaders) {
    return "GET".equals(method) && url.encodedPath().startsWith("/.well-known/acme-challenge/");
  }

  /**
   * Creates the response for disallowed insecure requests.
   * @param method the request method (get, post, ...).
   * @param url the request url.
   * @param insecureOnly whether the server accepts only insecure connections or whether https is enabled.
   * @return the response.
   */
  @SuppressWarnings("unused")
  protected Response handleDisallowedInsecureRequest(final String method, final HttpUrl url,
                                                     final boolean insecureOnly) {
    if (insecureOnly) {
      return FORBIDDEN;
    }
    else {
      return new Response.Builder().statusLine(StatusLines.PERMANENT_REDIRECT).
        location(url.newBuilder().scheme("https").build()).hsts().noBody().build();
    }
  }

}
