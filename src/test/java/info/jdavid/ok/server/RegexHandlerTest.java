package info.jdavid.ok.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import info.jdavid.ok.server.handler.RegexHandler;
import info.jdavid.ok.server.handler.Request;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


@SuppressWarnings("ConstantConditions")
public class RegexHandlerTest {

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  private static final OkHttpClient client = new OkHttpClient();

  private static OkHttpClient client() {
    return  client.newBuilder().
      readTimeout(0, TimeUnit.SECONDS).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      build();
  }

  @BeforeClass
  public static void startServer() throws IOException {
    SERVER.
      port(8080).
      maxRequestSize(512).
      requestHandler(new RequestHandlerChain().
        add(new RegexHandler("GET", "/r1") {
          @Override public Response.Builder handle(final Request request, final String[] params) {
            return new Response.Builder().statusLine(StatusLines.OK).header("handler", "r1").
              body(String.valueOf(params.length));
          }
        }).
        add(new RegexHandler("GET", "/r2/([^/]+)/([^/]+)/?") {
          @Override public Response.Builder handle(final Request request, final String[] params) {
            return new Response.Builder().statusLine(StatusLines.OK).header("handler", "r2").
              body(params[0] + "\n" + params[1]);
          }
        }).
        add(new RegexHandler("POST", "/r3") {
          @Override public Response.Builder handle(final Request request, final String[] params) {
            return new Response.Builder().statusLine(StatusLines.OK).header("handler", "r3").
              body(MediaType.parse(request.headers.get("Content-Type")), request.body.readByteArray());
          }
        })
      ).
      start();
  }

  @AfterClass
  public static void stopServer() {
    SERVER.shutdown();
  }

  @Test
  public void testNonMatching() throws IOException {
    final OkHttpClient client = client();
    final okhttp3.Response response1 = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/").
      build()
    ).execute();
    assertEquals(404, response1.code());
    response1.body().close();
    final okhttp3.Response response2 = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/a/r1").
      build()
    ).execute();
    assertEquals(404, response2.code());
    response2.body().close();
    final okhttp3.Response response3 = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/r12").
      build()
    ).execute();
    assertEquals(404, response3.code());
    response3.body().close();
    final okhttp3.Response response4 = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/r2/a").
      build()
    ).execute();
    assertEquals(404, response4.code());
    response4.body().close();
    final okhttp3.Response response5 = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/r2/a/b/c").
      build()
    ).execute();
    assertEquals(404, response5.code());
    response5.body().close();
  }

  @Test
  public void testSimple() throws IOException {
    final OkHttpClient client = client();
    final okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/r1").
      build()
    ).execute();
    assertEquals(200, response.code());
    assertEquals("r1", response.header("handler"));
    assertEquals("0", response.body().string());
  }

  @Test
  public void testParams() throws IOException {
    final OkHttpClient client = client();
    final okhttp3.Response response1 = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/r2/seg1/seg2").
      build()
    ).execute();
    assertEquals(200, response1.code());
    assertEquals("r2", response1.header("handler"));
    assertEquals("seg1\nseg2", response1.body().string());
    final okhttp3.Response response2 = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/r2/seg1/seg2/").
      build()
    ).execute();
    assertEquals(200, response2.code());
    assertEquals("r2", response2.header("handler"));
    assertEquals("seg1\nseg2", response2.body().string());
  }

  @Test
  public void testPost() throws IOException {
    final String json = "{\"test\":true}";
    final OkHttpClient client = client();
    final okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().
      url("http://localhost:8080/r3").
      post(RequestBody.create(MediaTypes.JSON, json)).
      build()
    ).execute();
    assertEquals(200, response.code());
    assertTrue(response.header("Content-Type").startsWith(MediaTypes.JSON.type()));
    assertEquals("r3", response.header("handler"));
    assertEquals(json, response.body().string());
  }

}
