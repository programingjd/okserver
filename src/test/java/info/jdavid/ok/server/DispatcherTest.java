package info.jdavid.ok.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Buffer;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


@SuppressWarnings("ConstantConditions")
public class DispatcherTest {

  private static HttpServer server(final Dispatcher dispatcher) {
    return new HttpServer().dispatcher(dispatcher).port(8080).requestHandler(new RequestHandler() {
      @Override public Response handle(final String clientIp,
                                       final boolean secure, final boolean insecureOnly, final boolean http2,
                                       final String method, final HttpUrl url,
                                       final Headers requestHeaders,
                                       @Nullable final Buffer requestBody) {
        try { Thread.sleep(1000L); } catch (final InterruptedException ignore) {}
        return new Response.Builder().statusLine(StatusLines.OK).body("Test").build();
      }
    });
  }

  private static final OkHttpClient client = new OkHttpClient();

  private static OkHttpClient client() {
    return  client.newBuilder().
      readTimeout(0, TimeUnit.SECONDS).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      build();
  }

  private static Request request() {
    final String url = "http://localhost:8080";
    return new Request.Builder().url(url).build();
  }

  private static Runnable call(final AtomicInteger counter) {
    return new Runnable() {
      @Override public void run() {
        try {
          assertEquals("Test", client().newCall(request()).execute().body().string());
          counter.incrementAndGet();
        }
        catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }


  @BeforeClass
  public static void prime() {
    // Use an http client once to get rid of the static initializer penalty.
    // This is done so that the first test elapsed time doesn't get artificially high.
    try {
      final OkHttpClient c = client.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
      c.newCall(new Request.Builder().url("http://google.com").build()).execute();
    }
    catch (final IOException ignore) {}
  }

  @Test
  public void testSameThread() {
    final HttpServer server = server(new Dispatcher.SameThreadDispatcher());
    testOneThread(server);
  }

  @Test
  public void testSingleThread() {
    final HttpServer server = server(new Dispatcher.SingleThreadDispatcher());
    testOneThread(server);
  }

  private void testOneThread(final HttpServer server) {
    try {
      server.start();
      final AtomicInteger counter = new AtomicInteger();
      final Thread thread1 = new Thread(call(counter));
      final Thread thread2 = new Thread(call(counter));
      thread1.start();
      thread2.start();
      try { Thread.sleep(500L); } catch (final InterruptedException ignore) {}
      assertEquals(0, counter.get());
      try { Thread.sleep(1000L); } catch (final InterruptedException ignore) {}
      assertEquals(1, counter.get());
      try { Thread.sleep(1000L); } catch (final InterruptedException ignore) {}
      assertEquals(2, counter.get());
    }
    finally {
      server.shutdown();
    }
  }

  @Test
  public void testTwoThreads() {
    final HttpServer server = server(new Dispatcher.MultiThreadsDispatcher(2));
    try {
      server.start();
      final AtomicInteger counter = new AtomicInteger();
      final Thread thread1 = new Thread(call(counter));
      final Thread thread2 = new Thread(call(counter));
      thread1.start();
      thread2.start();
      try { Thread.sleep(500L); } catch (final InterruptedException ignore) {}
      assertEquals(0, counter.get());
      try { Thread.sleep(1000L); } catch (final InterruptedException ignore) {}
      assertEquals(2, counter.get());
    }
    finally {
      server.shutdown();
    }
  }

  @Test
  public void testThreeThreads() {
    final HttpServer server = server(new Dispatcher.Default());
    try {
      server.start();
      final AtomicInteger counter = new AtomicInteger();
      final Thread thread1 = new Thread(call(counter));
      final Thread thread2 = new Thread(call(counter));
      final Thread thread3 = new Thread(call(counter));
      thread1.start();
      thread2.start();
      thread3.start();
      try { Thread.sleep(500L); } catch (final InterruptedException ignore) {}
      assertEquals(0, counter.get());
      try { Thread.sleep(1000L); } catch (final InterruptedException ignore) {}
      assertEquals(3, counter.get());
    }
    finally {
      server.shutdown();
    }
  }

}
