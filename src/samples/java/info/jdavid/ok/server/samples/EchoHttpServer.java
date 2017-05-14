package info.jdavid.ok.server.samples;

import javax.annotation.Nullable;

import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.handler.RegexHandler;
import info.jdavid.ok.server.handler.Request;
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
    mServer = new HttpServer().
//      requestHandler(new RequestHandler() {
//        @Override
//        public Response handle(final String clientIp,
//                               final boolean secure, final boolean insecureOnly, final boolean http2,
//                               final String method, final HttpUrl url,
//                               final Headers requestHeaders, final Buffer requestBody) {
//          if (!"POST".equals(method)) return unsupported();
//          if (!"/echo".equals(url.encodedPath())) return notFound();
//          final MediaType mime = MediaType.parse(requestHeaders.get("Content-Type"));
//          return echo(requestBody, mime).build();
//        }
//      }).
      requestHandler(new RequestHandlerChain().add(new EchoHandler())).
      port(port);
  }

//  private Response notFound() {
//    return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build();
//  }

//  private Response unsupported() {
//    return new Response.Builder().statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody().build();
//  }

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

  private static Response.Builder echo(@Nullable final Buffer requestBody, @Nullable final MediaType mime) {
    if (requestBody == null) return new Response.Builder().statusLine(StatusLines.OK).noBody();
    if (mime == null) return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
    return new Response.Builder().statusLine(StatusLines.OK).body(new EchoBody(requestBody, mime));
  }

  private static class EchoHandler extends RegexHandler {

    EchoHandler() {
      super("POST", "/echo");
    }

    @Override public Response.Builder handle(final Request request, final String[] params) {
      final String contentType = request.headers.get("Content-Type");
      final MediaType mime = contentType == null ? null : MediaType.parse(contentType);
      return echo(request.body, mime);
    }
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
