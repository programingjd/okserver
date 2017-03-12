package info.jdavid.ok.server.samples;

import info.jdavid.ok.server.RequestHandler;
import okhttp3.Headers;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.HttpUrl;
import okio.Buffer;


@SuppressWarnings("WeakerAccess")
public class SSEServer {

  protected final HttpServer mServer;

  public SSEServer(final int port, final int retrySecs, final int periodSecs, final int initialDelaySecs) {
    //noinspection Duplicates
    mServer = new HttpServer().
      requestHandler(new RequestHandler() {
        @Override
        public Response handle(final String clientIp,
                               final boolean secure, final boolean insecureOnly, final boolean http2,
                               final String method, final HttpUrl url,
                               final Headers requestHeaders, final Buffer requestBody) {
          if (!"GET".equals(method)) return unsupported();
          if (!"/sse".equals(url.encodedPath())) return notFound();
          final Response.SSE sse = sse();
          final Response.SSE.EventSource eventSource = sse.getEventSource();
          new Thread() {
            // Sends 5 "OK" message.
            public void run() {
              if (initialDelaySecs > 0) {
                try { Thread.sleep(initialDelaySecs * 1000L); }
                catch (final InterruptedException ignore) {}
              }
              for (int i=0; i<4; ++i) {
                eventSource.send("OK");
                try { Thread.sleep(periodSecs * 1000L); }
                catch (final InterruptedException ignore) {}
              }
              eventSource.send("OK");
              eventSource.close();
            }
          }.start();
          return sse;
        }
        private Response notFound() {
          return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build();
        }
        private Response unsupported() {
          return new Response.Builder().statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody().build();
        }
        private Response.SSE sse() {
          return new Response.SSE(retrySecs);
        }
      }).port(port);
  }

  public void start() {
    mServer.start();
  }

  @SuppressWarnings("unused")
  public void stop() {
    mServer.shutdown();
  }


  public static void main(final String[] args) {
    final HttpServer server = new HttpServer().port(8083);
    server.start();
    server.shutdown();
  }

}
