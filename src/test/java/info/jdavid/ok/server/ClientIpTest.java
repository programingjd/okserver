package info.jdavid.ok.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


@SuppressWarnings("ConstantConditions")
public class ClientIpTest {

  private static final OkHttpClient client = new OkHttpClient();

  private static OkHttpClient client(@Nullable final Proxy proxy) {
    final OkHttpClient.Builder builder = client.newBuilder().readTimeout(0, TimeUnit.SECONDS);
    if (proxy != null) builder.proxy(proxy);
    return builder.build();
  }

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  private static Proxy getProxy() throws IOException {
    final String url = "http://gimmeproxy.com/api/getProxy?get=true&get=true&maxCheckPeriod=3600";
    final String json = client.newCall(new Request.Builder().url(url).build()).execute().body().string();
    assertTrue(json.startsWith("{"));
    final int ipKeyIndex = json.indexOf("\"ip\":");
    assertNotEquals(-1, ipKeyIndex);
    final int ipValueStartIndex = ipKeyIndex + 7;
    final int ipValueEndIndex = json.indexOf("\"", ipValueStartIndex + 1);
    assertNotEquals(-1, ipValueEndIndex);
    final String ip = json.substring(ipValueStartIndex, ipValueEndIndex);
    final int portKeyIndex = json.indexOf("\"port\":");
    assertNotEquals(-1, portKeyIndex);
    final int portValueStartIndex = portKeyIndex + 9;
    final int portValueEndIndex = json.indexOf("\"", portValueStartIndex + 1);
    assertNotEquals(-1, portValueEndIndex);
    final int port = Integer.parseInt(json.substring(portValueStartIndex, portValueEndIndex));
    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port));
  }

  @BeforeClass
  public static void startServer() {
    SERVER.port(8080).requestHandler(
      (clientIp, secure, insecureOnly, http2, method, url, requestHeaders, requestBody) ->
        new Response.Builder().statusLine(StatusLines.OK).body(clientIp).build()
      ).start();
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

  //@Test
  @SuppressWarnings("unused")
  // disabled because local server needs to be accessible from the outside (the proxy).
  public void testGetWithProxy() throws IOException {
    final Proxy proxy = getProxy();
    final InetSocketAddress address = (InetSocketAddress)proxy.address();
    final String ip = address.getHostName();
    final String serverIp = InetAddress.getLocalHost().getHostAddress();
    final String url = "http://" + serverIp + ":8080";
    final String clientIp =
      client(proxy).newCall(new Request.Builder().url(url).build()).execute().body().string();
    assertEquals(ip, clientIp);
  }

  @Test
  public void testGetLocalhost() throws IOException {
    final String url = "http://localhost:8080";
    final String clientIp =
      client(null).newCall(new Request.Builder().url(url).build()).execute().body().string();
    assertEquals("127.0.0.1", clientIp);
  }

  //@Test
  @SuppressWarnings("unused")
  // disabled because local server needs to be accessible from the outside (the proxy).
  public void testPostWithProxy() throws IOException {
    final Proxy proxy = getProxy();
    final InetSocketAddress address = (InetSocketAddress)proxy.address();
    final String ip = address.getHostName();
    final String serverIp = InetAddress.getLocalHost().getHostAddress();
    final String url = "http://" + serverIp + ":8080";
    final RequestBody body = RequestBody.create(MediaTypes.TEXT, "test");
    final String clientIp =
      client(proxy).newCall(new Request.Builder().url(url).post(body).build()).execute().body().string();
    assertEquals(ip, clientIp);
  }

  @Test
  public void testPostLocalhost() throws IOException {
    final String url = "http://localhost:8080";
    final RequestBody body = RequestBody.create(MediaTypes.TEXT, "test");
    final String clientIp =
      client(null).newCall(new Request.Builder().url(url).post(body).build()).execute().body().string();
    assertEquals("127.0.0.1", clientIp);
  }

}
