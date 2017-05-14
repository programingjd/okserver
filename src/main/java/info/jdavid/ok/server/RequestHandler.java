package info.jdavid.ok.server;

import javax.annotation.Nullable;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;


/**
 * The server request handler.
 */
@SuppressWarnings("WeakerAccess")
public interface RequestHandler {

  /**
   * Creates the server response for a given request.
   * @param clientIp the client ip.
   * @param secure whether the request is secure (over https) or not.
   * @param insecureOnly whether the server accepts only insecure connections or whether https is enabled.
   * @param http2 whether the request protocol is HTTP 2 (h2) rather than an HTTP 1.1.
   * @param method the request method (get, post, ...).
   * @param url the request url.
   * @param requestHeaders the request headers.
   * @param requestBody the request body.
   * @return the response for the request.
   */
  public Response handle(final String clientIp,
                         final boolean secure, final boolean insecureOnly, final boolean http2,
                         final String method, final HttpUrl url,
                         final Headers requestHeaders, @Nullable final Buffer requestBody);

  static class Helper {

    static Response handle(final RequestHandler handler,
                           final String clientIp,
                           final boolean secure, final boolean insecureOnly, final boolean http2,
                           final String method, final String path,
                           final Headers requestHeaders, final Buffer requestBody) {
      final String h = requestHeaders == null ? null : requestHeaders.get("Host");
      if (h == null) {
        return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
      }
      final int i = h.indexOf(':');
      final String host = i == -1 ? h : h.substring(0, i);
      final int port = i == -1 ? 0 : Integer.valueOf(h.substring(i+1));

      final HttpUrl.Builder url = HttpUrl.parse("http://localhost" + path).newBuilder().
        scheme(secure ? "https" : "http").
        host(host);
      if (port > 0) url.port(port);
      return handler.handle(clientIp, secure, insecureOnly, http2,
                            method, url.build(), requestHeaders, requestBody);
    }

  }

}
