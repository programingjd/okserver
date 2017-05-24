package info.jdavid.ok.server.samples;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import info.jdavid.ok.server.Dispatcher;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.Https;
import info.jdavid.ok.server.RequestHandler;
import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import info.jdavid.ok.server.handler.BasicAuthHandler;
import info.jdavid.ok.server.handler.FileHandler;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;

public class Readme {

  public static void customRequestHandler() {
    new HttpServer().
      requestHandler(
        new RequestHandler() {
          @Override
          public Response handle(final String clientIp, final boolean secure,
                                 final boolean insecureOnly, final boolean http2,
                                 final String method, final HttpUrl url,
                                 final Headers requestHeaders,
                                 @Nullable final Buffer requestBody) {
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
      hostname("localhost").
      port(8080).
      start();
  }

  public static void https() {
    final byte[] p12 = {}; // bytes from the p12 certificate.
    final boolean useHttp2 = true;
    new HttpServer().
      hostname("example.com").
      ports(8080, 8181).
      https(new Https.Builder().certificate(p12, useHttp2).build()).
      start();
  }

  public static void customDispatcher() {
    new HttpServer().
      dispatcher(
        new Dispatcher() {
          private ExecutorService mExecutors = null;
          @Override
          public void start() {
            mExecutors = Executors.newSingleThreadExecutor();
          }
          @Override
          public void dispatch(final HttpServer.Request request) {
            mExecutors.execute(new Runnable() {
              @Override public void run() { request.serve(); }
            });
          }
          @Override
          public void shutdown() {
            try {
              mExecutors.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch (final InterruptedException ignore) {}
            mExecutors.shutdownNow();
          }
        }
      ).
      requestHandler(new RequestHandlerChain()).
      port(8080).
      start();
  }

  public static void handlerChain() {
    final Map<String, String> credentials = new HashMap<String, String>();
    credentials.put("user1", "password1");
    final File webRoot = new File(new File(System.getProperty("user.home")), "www");

    new HttpServer().
      requestHandler(
        new RequestHandlerChain().
          add(new BasicAuthHandler(credentials, new FileHandler(webRoot)))
      ).
      dispatcher(new Dispatcher.MultiThreadsDispatcher(4)).
      port(8080).
      start();
  }

}
