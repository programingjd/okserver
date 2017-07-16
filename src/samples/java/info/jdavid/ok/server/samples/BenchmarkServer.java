package info.jdavid.ok.server.samples;

import javax.annotation.Nullable;

import info.jdavid.ok.server.Dispatcher;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.MediaTypes;
import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import info.jdavid.ok.server.handler.Handler;
import info.jdavid.ok.server.handler.Request;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;


public class BenchmarkServer {

  public static void main(final String[] args) {
    new HttpServer()/*.dispatcher(new Dispatcher.MultiThreadsDispatcher(16))*/.requestHandler(
      new RequestHandlerChain().add(new BenchmarkHandler())
    ).start();
  }

  private static class BenchmarkHandler implements Handler {

    private final String CRLF = "\r\n";

    @Override
    public Handler setup() {
      return this;
    }

    @Nullable
    @Override
    public String[] matches(final String method, final HttpUrl url) {
      return "GET".equals(method) ? new String[] { url.encodedPath() } : null;
    }

    @Override
    public Response.Builder handle(final Request request, final String[] params) {
      final Headers headers = request.headers;

      final Buffer buffer = new Buffer();
      buffer.writeUtf8("GET " + params[0] + CRLF + CRLF);
      final int n = headers.size();
      for (int i=0; i<n; ++i) {
        buffer.writeUtf8(headers.name(i) + ": " + headers.value(i) + CRLF);
      }

      return new Response.Builder().
        statusLine(StatusLines.OK).
        body(MediaTypes.TEXT, buffer);
    }
  }

}
