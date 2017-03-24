package info.jdavid.ok.server;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okio.Buffer;

public class TestRequestHandler implements RequestHandler {

  @Override public Response handle(final String clientIp,
                                   final boolean secure, final boolean insecureOnly, final boolean http2,
                                   final String method, final HttpUrl url,
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
