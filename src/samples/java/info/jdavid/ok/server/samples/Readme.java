package info.jdavid.ok.server.samples;

import info.jdavid.ok.server.Dispatcher;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Https;
import info.jdavid.ok.server.RequestHandler;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;

public class Readme {

  public static void main(final String[] args) {

    final byte[] p12 = {}; // bytes from the p12 certificate.
    final boolean useHttp2 = true;

    new HttpServer().
      requestHandler(
        new RequestHandler() {
          @Override
          public Response handle(final String clientIp, final boolean secure,
                                 final boolean insecureOnly, final boolean http2,
                                 final String method, final HttpUrl url,
                                 final Headers requestHeaders,
                                 final Buffer requestBody) {
            final String path = url.encodedPath();
            final Response.Builder builder = new Response.Builder();
            if ("GET".equals(method) && "/ok".equals(path)) {
              builder.statusLine(StatusLines.OK).body("ok");
            }
          else {
              builder.statusLine(StatusLines.NOT_FOUND).noBody();
            }
            return builder.build();
          }
        }
      ).
      dispatcher(new Dispatcher.MultiThreadsDispatcher(4)).
      hostname("www.example.com").
      ports(8080, 8181).
      https(new Https.Builder().certificate(p12, useHttp2).build()).
      start();

  }

}
