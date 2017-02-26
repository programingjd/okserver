package info.jdavid.ok.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;


public class RequestHandlerTest {

  private static HttpServer SERVER = new HttpServer().requestHandler(new RequestHandler() {
    @Override public Response handle(final String clientIp, final boolean secure,
                                     final String method, final HttpUrl url,
                                     final Headers requestHeaders, final Buffer requestBody) {
        final Buffer buffer = new Buffer();
        buffer.writeByte(byteLength(clientIp));
        buffer.writeUtf8(clientIp);
        buffer.writeByte(secure ? 0x01 : 0x00);
        buffer.writeByte(byteLength(method));
        buffer.writeUtf8(method);
        buffer.writeByte(byteLength(url.toString()));
        buffer.writeUtf8(url.toString());
        buffer.writeByte(requestHeaders.size());
        for (int i=0; i<requestHeaders.size(); ++i) {
          final String name = requestHeaders.name(i);
          buffer.writeByte(byteLength(name));
          buffer.writeUtf8(name);
          final String value = requestHeaders.value(i);
          buffer.writeByte(byteLength(value));
          buffer.writeUtf8(value);
        }
        buffer.writeByte((int)requestBody.size());
        buffer.write(requestBody, requestBody.size());
        return new Response.Builder().statusLine(StatusLines.OK).body(buffer, (int)buffer.size()).build();
      }
  });

  private static int byteLength(final String s) {
    try {
      return s.getBytes("UTF-8").length;
    }
    catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeClass
  public static void startServer() {
    SERVER.port(8080).start();
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
    SERVER.shutdown();
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

  @Test
  public void testParams() throws IOException {
    final BufferedSource source = Okio.buffer(client().newCall(new Request.Builder().
      url("http://localhost:8080").
      addHeader("test_name", "test_value").
      post(RequestBody.create(MediaTypes.TEXT, "test content")).
      build()
    ).execute().body().source());
    final int ipLength = source.readByte();
    final String ip = source.readUtf8(ipLength);
    assertEquals("127.0.0.1", ip);
    final byte secure = source.readByte();
    assertEquals(0x00, secure);
    final int methodLength = source.readByte();
    final String method = source.readUtf8(methodLength);
    assertEquals("POST", method);
    final int urlLength = source.readByte();
    final String url = source.readUtf8(urlLength);
    assertEquals("http://localhost:8080/", url);
    final byte count = source.readByte();
    assertTrue(count > 3);
    final Map<String, String> headers = new HashMap<String, String>(count);
    for (int i=0; i<count; ++i) {
      final int nameLength = source.readByte();
      final String name = source.readUtf8(nameLength);
      final int valueLength = source.readByte();
      final String value = source.readUtf8(valueLength);
      headers.put(name, value);
    }
    assertEquals("test_value", headers.get("test_name"));
    assertEquals("text/plain; charset=utf-8", headers.get("Content-Type"));
    assertEquals("12", headers.get("Content-Length"));
    assertEquals("localhost:8080", headers.get("Host"));
    final int bodyLength = source.readByte();
    final String body = source.readUtf8(bodyLength);
    assertEquals("test content", body);
    assertTrue(source.exhausted());
  }

}
