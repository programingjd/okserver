package info.jdavid.ok.server;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okio.Buffer;
import okio.Okio;
import okio.Source;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


public class HttpsTest {

  private static byte[] getCert() {
    final Source source = Okio.source(HttpsTest.class.getResourceAsStream("/test.p12"));
    final Buffer buffer = new Buffer();
    try {
      buffer.writeAll(source);
      return buffer.readByteArray();
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      try { source.close(); } catch (final IOException ignore) {}
      buffer.close();
    }
  }

  static final byte[] cert = getCert();

  static final OkHttpClient client;
  static {
    try {
      final X509TrustManager trustManager = new X509TrustManager() {
        @Override public void checkClientTrusted(final X509Certificate[] x509Certificates, final String s)
          throws CertificateException {}
        @Override
        public void checkServerTrusted(final X509Certificate[] x509Certificates, final String s)
          throws CertificateException {}
        @Override public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };
      final SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] { trustManager }, new SecureRandom());
      client = new OkHttpClient.Builder().
        sslSocketFactory(context.getSocketFactory(), trustManager).
        hostnameVerifier(new HostnameVerifier() {
          @Override public boolean verify(final String hostname, final SSLSession sslSession) {
            return true;
          }
        }).
        build();
    }
    catch (final GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }


  private static OkHttpClient client() {
    return  client.newBuilder().
      readTimeout(0, TimeUnit.SECONDS).
      retryOnConnectionFailure(false).
      connectTimeout(60, TimeUnit.SECONDS).
      connectionPool(new ConnectionPool(0, 1L, TimeUnit.SECONDS)).
      protocols(Collections.singletonList(Protocol.HTTP_1_1)).
      build();
  }

  private static final HttpServer SERVER = new HttpServer(); //.dispatcher(new Dispatcher.Logged());

  @BeforeClass
  public static void startServer() throws IOException {
    SERVER.
      ports(8080, 8181).
      https(new Https.Builder().certificate(cert, false).build()).
      requestHandler(new RequestHandler() {
        @Override public Response handle(final String clientIp, final boolean secure,
                                         final boolean insecureOnly, final boolean http2,
                                         final String method, final HttpUrl url,
                                         final Headers requestHeaders, final Buffer requestBody) {
          final String s = url + "\n" + secure + "\n" + insecureOnly;
          return new Response.Builder().statusLine(StatusLines.OK).body(s).build();
        }
      }).
      start();
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

  @Test
  public void testHttps() throws IOException {
    final String result =
      client().newCall(new Request.Builder().url("https://localhost:8181").build()).execute().body().string();
    final String[] split = result.split("\n");
    assertEquals(3, split.length);
    assertEquals("https://localhost:8181/", split[0]);
    assertEquals("true", split[1]);
    assertEquals("false", split[2]);
  }

  @Test
  public void testHttp() throws IOException {
    final String result =
      client().newCall(new Request.Builder().url("http://localhost:8080").build()).execute().body().string();
    final String[] split = result.split("\n");
    assertEquals(3, split.length);
    assertEquals("http://localhost:8080/", split[0]);
    assertEquals("false", split[1]);
    assertEquals("false", split[1]);
    try {
      client().newCall(new Request.Builder().url("http://localhost:8181").build()).execute();
      fail("Secure port 8181 should not accept plain HTTP connections.");
    }
    catch (final IOException ignore) {}
  }

}
