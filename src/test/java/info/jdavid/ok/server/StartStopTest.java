package info.jdavid.ok.server;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import org.junit.Test;
import static org.junit.Assert.*;


public class StartStopTest {

  private static Request.Builder request(final int port, final String... segments) {
    HttpUrl.Builder url = new HttpUrl.Builder().scheme("http").host("localhost").port(port);
    if (segments != null) {
      for (final String segment: segments) {
        url.addPathSegment(segment);
      }
    }
    return new Request.Builder().url(url.build());
  }

  private static OkHttpClient client() {
    final OkHttpClient client = new OkHttpClient();
    client.setConnectTimeout(250, TimeUnit.MILLISECONDS);
    client.setReadTimeout(250, TimeUnit.MILLISECONDS);
    return client;
  }

  @Test
  public void testStartAndStop() throws IOException {
    final HttpServer server = new HttpServer().port(8085);
    server.start();
    assertEquals(404, client().newCall(request(8085).build()).execute().code());
    server.shutdown();
    try {
      assertEquals(404, client().newCall(request(8085).build()).execute().code());
      fail("Server should have stopped");
    }
    catch (final ConnectException ignore) {}
    catch (final InterruptedIOException ignored) {}
  }

  @Test
  public void testMultipleStartAndStop() throws IOException {
    final HttpServer server1 = new HttpServer().port(8086);
    server1.start();
    assertEquals(404, client().newCall(request(8086).build()).execute().code());
    final HttpServer server2 = new HttpServer().port(8087);
    server2.start();
    assertEquals(404, client().newCall(request(8087).build()).execute().code());
    server1.shutdown();
    try {
      assertEquals(404, client().newCall(request(8086).build()).execute().code());
      fail("Server should have stopped");
    }
    catch (final ConnectException ignore) {}
    catch (final InterruptedIOException ignored) {}
    assertEquals(404, client().newCall(request(8087).build()).execute().code());
    server2.shutdown();
    try {
      assertEquals(404, client().newCall(request(8087).build()).execute().code());
      fail("Server should have stopped");
    }
    catch (final ConnectException ignore) {}
    catch (final InterruptedIOException ignored) {}
  }

}
