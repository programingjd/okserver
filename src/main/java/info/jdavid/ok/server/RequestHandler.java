package info.jdavid.ok.server;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okio.Buffer;

/**
 * The server request handler.
 */
@SuppressWarnings("WeakerAccess")
public interface RequestHandler {

  public Response handle(final boolean secure, final String method, final HttpUrl url,
                         final Headers requestHeaders, final Buffer requestBody);
  /**
   * The Default request handler, used for testing.
   */
  public static final RequestHandler DEFAULT = new DefaultRequestHandler();

  static class Helper {

    static Response handle(final RequestHandler handler,
                           final boolean secure, final String method, final String path,
                           final Headers requestHeaders, final Buffer requestBody) {
      final String h = requestHeaders.get("Host");
      if (h == null) {
        return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
      }
      final int i = h.indexOf(':');
      final String host = i == -1 ? h : h.substring(0, i);
      final int port = i == -1 ? 0 : Integer.valueOf(h.substring(i+1));

      final HttpUrl.Builder url = new HttpUrl.Builder().
        scheme(secure ? "https" : "http").
        host(host).
        addEncodedPathSegments(path.indexOf('/') == 0 ? path.substring(1) : path);
      if (port > 0) url.port(port);
      return handler.handle(secure, method, url.build(), requestHeaders, requestBody);
    }

  }

  static class DefaultRequestHandler implements RequestHandler {

    @Override public Response handle(final boolean secure, final String method, final HttpUrl url,
                                     final Headers requestHeaders, final Buffer requestBody) {
      final Response.Builder builder = new Response.Builder();
      if ("/test".equals(url.encodedPath())) {
        builder.statusLine(StatusLines.OK);
        builder.headers(requestHeaders);
        if (requestBody != null) {
          final MediaType mediaType = MediaType.parse(requestHeaders.get("Content-Type"));
          builder.body(new Response.BufferResponse(mediaType, requestBody));
        }
        else {
          builder.noBody();
        }
      }
      else if ("GET".equalsIgnoreCase(method)) {
        builder.statusLine(StatusLines.NOT_FOUND).noBody();
      }
      else {
        builder.statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody();
      }
      return builder.build();
    }

  }

}
