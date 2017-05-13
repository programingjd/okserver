package info.jdavid.ok.server;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;


/**
 * Abstract implementation of a request handler with sensible (and overridable) defaults.
 */
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
        if ("GET".equals(method) && isAcmeChallenge(url)) {
          return handleAcmeChallenge(url, requestHeaders, requestBody);
        }
        else if (allowInsecure(method, url, insecureOnly)) {
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
   * @param url the challenge request url.
   * @param requestHeaders the challenge request headers.
   * @param requestBody the challenge request body.
   * @return the challenge response.
   */
  protected abstract Response handleAcmeChallenge(final HttpUrl url,
                                                  final Headers requestHeaders,
                                                  final Buffer requestBody);

  /**
   *
   * @param clientIp
   * @param http2
   * @param method
   * @param url
   * @param requestHeaders
   * @param requestBody
   * @return
   */
  protected abstract Response handle(final String clientIp, final boolean http2,
                                     final String method, final HttpUrl url,
                                     final Headers requestHeaders, final Buffer requestBody);


  private static final Response FORBIDDEN =
    new Response.Builder().statusLine(StatusLines.FORBIDDEN).noBody().build();

  protected void init() {}

  /**
   * Returns whether the client ip is allowed (not blacklisted) or not.
   * @param clientIp the client ip to validate.
   * @return true if the client ip is allowed, false if it is blacklisted.
   */
  protected boolean acceptClientIp(final String clientIp) {
    return true;
  }

  /**
   * Creates the response for blacklisted client ips. (403 FORBIDDEN by default).
   * @param method the request method (get, post, ...).
   * @param url the request url.
   * @return the response.
   */
  protected Response handleBlockedClientIp(final String method, final HttpUrl url) {
    return FORBIDDEN;
  }


  /**
   * Returns whether the request is allowed to be insecure (http rather than https) or not.
   * @param method the request method (get, post, ...).
   * @param url the request url.
   * @param insecureOnly whether the server accepts only insecure connections or whether https is enabled.
   * @return whether the insecure request is allowed or not.
   */
  protected boolean allowInsecure(final String method, final HttpUrl url, final boolean insecureOnly) {
    return insecureOnly || isAcmeChallenge(url);
  }

  /**
   * Returns whether the request is an acme challenge (domain owner verification for certificates like
   * let's encrypt).
   * @param url the request url.
   * @return true if the url is for an acme challenge, false if it isn't.
   */
  protected boolean isAcmeChallenge(final HttpUrl url) {
    return url.encodedPath().startsWith("/.well-known/acme-challenge/");
  }

  /**
   * Creates the response for disallowed insecure requests.
   * @param method the request method (get, post, ...).
   * @param url the request url.
   * @param insecureOnly whether the server accepts only insecure connections or whether https is enabled.
   * @return the response.
   */
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
