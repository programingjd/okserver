package info.jdavid.ok.server.samples;

import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.handler.RegexHandler;
import info.jdavid.ok.server.handler.Request;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Response;


@SuppressWarnings("WeakerAccess")
public class SSEServer {

  protected final HttpServer mServer;

  public SSEServer(final int port, final int retrySecs, final int periodSecs, final int initialDelaySecs) {
    //noinspection Duplicates
    mServer = new HttpServer().
      requestHandler(
//        new RequestHandler() {
//          @Override
//          public Response handle(final String clientIp,
//                                 final boolean secure, final boolean insecureOnly, final boolean http2,
//                                 final String method, final HttpUrl url,
//                                 final Headers requestHeaders, final Buffer requestBody) {
//            if (!"GET".equals(method)) return unsupported();
//            if (!"/sse".equals(url.encodedPath())) return notFound();
//            final Response.EventSource eventSource = new Response.EventSource();
//            new Thread() {
//              // Sends 5 "OK" message.
//              public void run() {
//                if (initialDelaySecs > 0) {
//                  try { Thread.sleep(initialDelaySecs * 1000L); }
//                  catch (final InterruptedException ignore) {}
//                }
//                for (int i=0; i<4; ++i) {
//                  eventSource.send("OK");
//                  try { Thread.sleep(periodSecs * 1000L); }
//                  catch (final InterruptedException ignore) {}
//                }
//                eventSource.send("OK");
//              }
//            }.start();
//            return new Response.Builder().sse(eventSource, retrySecs).build();
//          }
//          private Response notFound() {
//            return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build();
//          }
//          private Response unsupported() {
//            return new Response.Builder().statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody().build();
//          }
//        }
        new RequestHandlerChain().
          add(new SSEHandler(retrySecs, periodSecs, initialDelaySecs))
      ).
      port(port);
  }

  public void start() {
    mServer.start();
  }

  @SuppressWarnings("unused")
  public void stop() {
    mServer.shutdown();
  }


  public static void main(final String[] args) {
    new SSEServer(8080, 15, 10, 5).start();
  }

  private static class SSEHandler extends RegexHandler {

    private final int mRetrySecs;
    private final int mPeriodSecs;
    private final int mInitialDelaySecs;

    protected SSEHandler(final int retrySecs, final int periodSecs, final int initialDelaySecs) {
      super("GET", "/sse");
      mRetrySecs = retrySecs;
      mPeriodSecs = periodSecs;
      mInitialDelaySecs = initialDelaySecs;
    }

    @Override
    public Response.Builder handle(final Request request, final String[] params) {
      final Response.EventSource eventSource = new Response.EventSource();
      new Thread() {
        // Sends 5 "OK" message.
        public void run() {
          if (mInitialDelaySecs > 0) {
            try { Thread.sleep(mInitialDelaySecs * 1000L); }
            catch (final InterruptedException ignore) {}
          }
          for (int i=0; i<4; ++i) {
            eventSource.send("OK");
            try { Thread.sleep(mPeriodSecs * 1000L); }
            catch (final InterruptedException ignore) {}
          }
          eventSource.send("OK");
          eventSource.close();
        }
      }.start();
      return new Response.Builder().sse(eventSource, mRetrySecs);
    }

  }

}
