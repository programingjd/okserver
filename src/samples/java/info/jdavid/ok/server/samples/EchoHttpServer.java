package info.jdavid.ok.server.samples;

import info.jdavid.ok.server.RequestHandler;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okio.Buffer;
import okio.BufferedSource;


@SuppressWarnings("WeakerAccess")
public class EchoHttpServer {

  private final HttpServer mServer;

  public EchoHttpServer() {
    this(8080);
  }

  public EchoHttpServer(final int port) {
    mServer = new HttpServer().requestHandler(new RequestHandler() {
      @Override public Response handle(final boolean secure, final String method, final HttpUrl url,
                                       final Headers requestHeaders, final Buffer requestBody) {
        if (!"POST".equals(method)) return unsupported();
        if (!"/echo".equals(url.encodedPath())) return notFound();
        final MediaType mime = MediaType.parse(requestHeaders.get("Content-Type"));
        return echo(requestBody, mime);
      }
    }).port(port);
  }

  private Response notFound() {
    return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build();
  }

  private Response unsupported() {
    return new Response.Builder().statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody().build();
  }

  private Response echo(final Buffer requestBody, final MediaType mime) {
    return new Response.Builder().statusLine(StatusLines.OK).body(new EchoBody(requestBody, mime)).build();
  }

  public void start() {
    mServer.start();
  }

  @SuppressWarnings("unused")
  public void stop() {
    mServer.shutdown();
  }

  public static void main(final String[] args) {
    new EchoHttpServer().start();
  }

  private static class EchoBody extends ResponseBody {
    private final Buffer mBuffer;
    private final long mLength;
    private final MediaType mMediaType;
    public EchoBody(final Buffer buffer, final MediaType mediaType) {
      super();
      mBuffer = buffer;
      mLength = buffer.size();
      mMediaType = mediaType;
    }
    @Override public MediaType contentType() { return mMediaType; }
    @Override public long contentLength() { return mLength; }
    @Override public BufferedSource source() { return mBuffer; }
  }

}
