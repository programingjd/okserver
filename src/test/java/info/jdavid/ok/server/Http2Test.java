package info.jdavid.ok.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

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

import static org.junit.Assert.*;


@SuppressWarnings("ConstantConditions")
public class Http2Test {

  private static OkHttpClient client(final List<Protocol> protocols) {
    return HttpsTest.client.newBuilder().
      readTimeout(0, TimeUnit.SECONDS).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      protocols(protocols).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      build();
  }

  private static OkHttpClient http11Client() {
    return client(Collections.singletonList(Protocol.HTTP_1_1));
  }

  private static OkHttpClient http2Client() {
    return client(Arrays.asList(Protocol.HTTP_1_1, Protocol.HTTP_2));
  }

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  private static AtomicInteger pushCounter = new AtomicInteger();

  private static boolean needsAlpnBoot() {
    final String v = Runtime.class.getPackage().getImplementationVersion();
    return (v != null && (v.startsWith("1.6") || v.startsWith("1.7") || v.startsWith("1.8")));
  }

  @BeforeClass
  public static void startServer() throws IOException {
    if (needsAlpnBoot() && !"true".equals(System.getProperty("started-with-gradle"))) {
      throw new RuntimeException(
        "This test needs to run within an environment that has an implementation of ALPN.\n" +
        "ALPN is an important part of the http2 protocol negociation.\n" +
        "You can either run it with java 9, or run it from gradle, as the gradle test task adds an ALPN " +
        "implementation to the boot class path.\n" +
        "In IntelliJ IDEA, you can go to the settings under:\n" +
        "Build, Execution, Deployment > Build Tools > Gradle > Runner\n" +
        "and check the option:\n" +
        "Delegate IDE build/run actions to gradle\n" +
        "This will make the IDE use gradle when running tests from within the IDE itself."
      );
    }

    SERVER.
      ports(0, 8181).
      https(new Https.Builder().certificate(HttpsTest.cert, true).build()).
      requestHandler(new RequestHandler() {
        @Override public Response handle(final String clientIp, final boolean secure,
                                         final boolean insecureOnly, final boolean http2,
                                         final String method, final HttpUrl url,
                                         final Headers requestHeaders,
                                         @Nullable final Buffer requestBody) {
          final List<String> path = url.pathSegments();
          if (path.isEmpty() || !path.get(path.size() - 1).equals("push")) {
            final String s = url + "\n" + secure + "\n" + insecureOnly + "\n" + http2;
            final HttpUrl pushUrl = url.newBuilder("push").build();
            return new Response.Builder().statusLine(StatusLines.OK).body(s).push(pushUrl).build();
          }
          else {
            pushCounter.incrementAndGet();
            return new Response.Builder().statusLine(StatusLines.OK).body("push").build();
          }
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

  private Request request() {
    return new Request.Builder().url("https://localhost:8181").build();
  }

  @Test
  public void testHttp11Fallback() throws IOException {
    final okhttp3.Response r = http11Client().newCall(request()).execute();
    assertNotNull(r.handshake());
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    final String[] split1 = r.body().string().split("\n");
    assertEquals(4, split1.length);
    assertEquals("https://localhost:8181/", split1[0]);
    assertEquals("true", split1[1]);
    assertEquals("false", split1[2]);
    assertEquals("false", split1[3]);
  }

  @Test
  public void testHttp2() throws IOException {
    if (Platform.findPlatform() instanceof Platform.Jdk9Platform) {
      // other platforms are not supported by the okhttp client.
      pushCounter.set(0);
      final okhttp3.Response r = http2Client().newCall(request()).execute();
      assertNotNull(r.handshake());
      assertEquals(Protocol.HTTP_2, r.protocol());
      assertEquals(200, r.code());
      final String[] split1 = r.body().string().split("\n");
      assertEquals(4, split1.length);
      assertEquals("https://localhost:8181/", split1[0]);
      assertEquals("true", split1[1]);
      assertEquals("false", split1[2]);
      assertEquals("true", split1[3]);
      try {
        Thread.sleep(1000L);
      }
      catch (final InterruptedException ignore) {
      }
      assertEquals(1, pushCounter.get());
    }
  }

}
