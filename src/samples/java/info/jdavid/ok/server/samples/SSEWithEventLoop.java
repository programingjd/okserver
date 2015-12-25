package info.jdavid.ok.server.samples;

import com.squareup.okhttp.Headers;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okio.Buffer;


public class SSEWithEventLoop {

  protected final HttpServer mServer;
  private final int mRetry;
  private final int mPeriod;
  // Sends 5 OK messages and then closes.
  private final Response.SSE.EventLoop mEventLoop = new Response.SSE.EventLoop() {
    private int mCounter = 0;
    @Override public int loop(final Response.SSE.Body body) {
      body.writeEventData("OK");
      if (++mCounter == 5) return -1;
      return mPeriod;
    }
  };

  public SSEWithEventLoop(final int port, final int retrySecs, final int periodSecs) {
    mRetry = retrySecs;
    mPeriod = periodSecs;
    //noinspection Duplicates
    mServer = new HttpServer() {
      @Override protected Response handle(final String method, final String path,
                                          final Headers requestHeaders, final Buffer requestBody) {
        if (!"GET".equals(method)) return unsupported();
        if (!"/sse".equals(path)) return notFound();
        return sse();
      }
    }.port(port);
  }

  private Response notFound() {
    return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build();
  }

  private Response unsupported() {
    return new Response.Builder().statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody().build();
  }

  private Response sse() {
    return new Response.SSE(mRetry, mEventLoop, 0);
  }

  public void start() {
    mServer.start();
  }

  @SuppressWarnings("unused")
  public void stop() {
    mServer.shutdown();
  }

  public static void main(final String[] args) {
    new SSEWithEventLoop(8080, 5, 10).start();
  }

}