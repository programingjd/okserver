package info.jdavid.ok.server;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okio.Buffer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class Http2Test {

  private static OkHttpClient client() {
    return  HttpsTest.client.newBuilder().
      readTimeout(0, TimeUnit.SECONDS).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      protocols(Collections.singletonList(Protocol.HTTP_2)).
      build();
  }

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  @BeforeClass
  public static void startServer() throws IOException {
    SERVER.
      ports(8080, 8181).
      https(new Https.Builder().certificate(HttpsTest.cert, false).build()).
      requestHandler(new RequestHandler() {
        @Override public Response handle(final String clientIp, final boolean secure,
                                         final String method, final HttpUrl url,
                                         final Headers requestHeaders, final Buffer requestBody) {
          final String s = url + "\n" + secure;
          return new Response.Builder().statusLine(StatusLines.OK).body(s).build();
        }
      }).
      start();
    // Use an http client once to get rid of the static initializer penalty.
    // This is done so that the first test elapsed time doesn't get artificially high.
    try {
      final OkHttpClient c = HttpsTest.client.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
      c.newCall(new Request.Builder().url("http://google.com").build()).execute();
    }
    catch (final IOException ignore) {}
  }

  @AfterClass
  public static void stopServer() {
    SERVER.shutdown();
  }

  @Test
  public void testAlpnAvailable() throws Exception {
    try {
      Class.forName("sun.security.ssl.ALPNExtension");
    }
    catch (final IllegalAccessError ignore) {}
    assertTrue(Platform.findPlatform().supportsHttp2());
  }


}
