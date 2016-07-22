package info.jdavid.ok.server;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import info.jdavid.ok.server.samples.SSEWithEventSource;
import okio.BufferedSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SSEWithEventSourceTest {

  private static Request.Builder request(final String... segments) {
    HttpUrl.Builder url = new HttpUrl.Builder().scheme("http").host("localhost").port(8082);
    if (segments != null) {
      for (final String segment: segments) {
        url.addPathSegment(segment);
      }
    }
    return new Request.Builder().url(url.build());
  }

  private static final OkHttpClient client = new OkHttpClient();

  private static OkHttpClient client() {
    return client.newBuilder().readTimeout(10, TimeUnit.SECONDS).build();
  }

  private static final SSEWithEventSource SERVER = new SSEWithEventSource(8082, 5, 0);

  @BeforeClass
  public static void startServer() {
    SERVER.start();
    // Use an http client once to get rid of the static initializer penalty.
    // This is done so that the first test elapsed time doesn't get artificially high.
    try {
      final OkHttpClient c = client.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
      c.newCall(new Request.Builder().url("http://google.com").build()).execute();
    }
    catch (final IOException ignore) {}
  }

  @AfterClass
  public static void stopServer() {
    SERVER.stop();
  }

  @Test
  public void testStream() throws IOException {
    final Response r = client().newCall(
      request("sse").
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("keep-alive", r.header("Connection"));
    final String contentLengthHeader = r.header("Content-Length");
    assertTrue(contentLengthHeader == null || "-1".equals(contentLengthHeader));
    final BufferedSource source = r.body().source();
    assertEquals("retry: 5", source.readUtf8Line());
    assertTrue(source.buffer().exhausted());
    SERVER.startLoop();
    for (int i=0; i<5; ++i) {
      assertEquals("data: OK", source.readUtf8Line());
      assertEquals("", source.readUtf8Line());
    }
    assertTrue(source.exhausted());
  }

}
