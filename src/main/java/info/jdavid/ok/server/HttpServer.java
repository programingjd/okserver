package info.jdavid.ok.server;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.internal.http.HttpMethod;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;


@SuppressWarnings({ "unused", "WeakerAccess" })
public class HttpServer {

  @SuppressWarnings("unused")
  static void log(final String...values) {
    switch (values.length) {
      case 0:
        return;
      case 1:
        System.out.println(values[0]);
        return;
      case 2:
        System.out.println(values[0] + ": " + values[1]);
        return;
      default:
        boolean addSeparator = false;
        for (final String value: values) {
          if (addSeparator) {
            System.out.print(' ');
          }
          else {
            addSeparator = true;
          }
          System.out.print(value);
        }
        System.out.println();
    }
  }

  static void log(final Throwable t) {
    t.printStackTrace();
  }

  private final AtomicBoolean mStarted = new AtomicBoolean();
  private final AtomicBoolean mSetup = new AtomicBoolean();
  private int mPort = 8080; // 80
  private int mSecurePort = 8181; // 443
  private String mHostname = null;
  private long mMaxRequestSize = 65536;
  private ServerSocket mServerSocket = null;
  private SSLServerSocket mSecureServerSocket = null;
  private Dispatcher mDispatcher = null;

  /**
   * Sets the port number for the server.
   * @param port the port number.
   * @param securePort the secure port number.
   * @return this.
   */
  public final HttpServer ports(final int port, final int securePort) {
    if (mStarted.get()) {
      throw new IllegalStateException("The port number cannot be changed while the server is running.");
    }
    this.mPort = port;
    this.mSecurePort = securePort;
    return this;
  }

  /**
   * Sets the port number for the server.
   * @param port the port number.
   * @return this.
   */
  public final HttpServer port(final int port) {
    if (mStarted.get()) {
      throw new IllegalStateException("The port number cannot be changed while the server is running.");
    }
    this.mPort = port;
    return this;
  }

  /**
   * Gets the port number for the server.
   * @return the server port number.
   */
  public final int port() {
    return mPort;
  }

  /**
   * Sets the port number for the server (secure connections).
   * @param port the port number.
   * @return this.
   */
  public final HttpServer securePort(final int port) {
    if (mStarted.get()) {
      throw new IllegalStateException("The port number cannot be changed while the server is running.");
    }
    this.mSecurePort = port;
    return this;
  }

  /**
   * Gets the port number for the server (secure connections).
   * @return the server secure port number.
   */
  public final int securePort() {
    return mSecurePort;
  }

  /**
   * Sets the host name for the server.
   * @param hostname the host name.
   * @return this.
   */
  public final HttpServer hostname(final String hostname) {
    if (mStarted.get()) {
      throw new IllegalStateException("The host name cannot be changed while the server is running.");
    }
    this.mHostname = hostname;
    return this;
  }

  /**
   * Gets the host name for the server.
   * @return the server host name.
   */
  public final String hostname() {
    return mHostname;
  }

  /**
   * Sets the maximum request size allowed by the server.
   * @param size the maximum request size in bytes.
   * @return this.
   */
  public final HttpServer maxRequestSize(final long size) {
    if (mStarted.get()) {
      throw new IllegalStateException("The max request size cannot be changed while the server is running.");
    }
    this.mMaxRequestSize = size;
    return this;
  }

  /**
   * Gets the maximum request size allowed by the server.
   * @return the maximum allowed request size.
   */
  public final long maxRequestSize() {
    return mMaxRequestSize;
  }

  /**
   * Sets a custom dispatcher.
   * @param dispatcher the dispatcher responsible for distributing the connection requests.
   * @return this
   */
  public final HttpServer dispatcher(final Dispatcher dispatcher) {
    if (mStarted.get()) {
      throw new IllegalStateException("The dispatcher cannot be changed while the server is running.");
    }
    this.mDispatcher = dispatcher;
    return this;
  }

  private Dispatcher dispatcher() {
    Dispatcher dispatcher = mDispatcher;
    if (dispatcher == null) {
      dispatcher = mDispatcher = createDefaultDispatcher();
    }
    return dispatcher;
  }

  protected void setup() {}

  /**
   * Shuts the server down.
   */
  public void shutdown() {
    if (!mStarted.get()) return;
    try {
      mServerSocket.close();
    }
    catch (final IOException ignore) {}
    try {
      if (mSecureServerSocket != null) mSecureServerSocket.close();
    }
    catch (final IOException ignore) {}
    try {
      mDispatcher.shutdown();
    }
    catch (final Exception ignore) {}
    finally {
      mStarted.set(false);
    }
  }

  /**
   * Returns whether the server is running or not.
   * @return true if the server has been started, false if it hasn't, or has been stopped since.
   */
  public final boolean isRunning() {
    return mStarted.get();
  }

  protected InputStream getSSLCertificate() {
    return null;
//    try {
//      return new java.io.FileInputStream(...);
//    }
//    catch (final Exception ignore) {
//      return null;
//    }
  }

  /**
   * Returns the SSL Context for https connections.
   * @return the ssl context, null if https is not supported by the server.
   */
  protected SSLContext getSSLContext() {
    final InputStream cert = getSSLCertificate();
    if (cert == null) return null;
    try {
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(cert, new char[0]);
      cert.close();
      final KeyManagerFactory kmf =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, new char[0]);
      final KeyStore trustStore = KeyStore.getInstance("JKS");
      trustStore.load(null, null);
      final TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trustStore);
      final SSLContext context = SSLContext.getInstance("TLS");
      context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
      return context;
    }
    catch (final GeneralSecurityException e) {
      log(e);
      return null;
    }
    catch (final IOException e) {
      log(e);
      return null;
    }
  }

  /**
   * Starts the server.
   */
  public final void start() {
    if (mStarted.getAndSet(true)) {
      throw new IllegalStateException("The server has already been started.");
    }
    try {
      if (!mSetup.getAndSet(true)) setup();
      final Dispatcher dispatcher = dispatcher();
      dispatcher.start();
      final InetAddress address;
      if (mHostname == null) {
        address = null; //new InetSocketAddress(0).getAddress();
      }
      else {
        address = InetAddress.getByName(mHostname);
      }

      final ServerSocket serverSocket = mServerSocket = new ServerSocket(mPort, -1, address);
      serverSocket.setReuseAddress(true);

      final SSLContext ssl = getSSLContext();
      final SSLServerSocket secureServerSocket = mSecureServerSocket =
        ssl == null ?
          null :
          (SSLServerSocket)ssl.getServerSocketFactory().createServerSocket(mSecurePort, -1, address);
      if (secureServerSocket != null) {
        secureServerSocket.setReuseAddress(true);
        try {
          final SSLParameters parameters = new SSLParameters();
          parameters.setProtocols( new String[] {
            "TLSv1.2"
          });
          parameters.setCipherSuites(new String[] {
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
          });
          secureServerSocket.setSSLParameters(new SSLParameters());
        }
        catch (final Exception e) {
          log(e);
        }
      }

      new Thread(new Runnable() {
        @Override public void run() {
          try {
            //noinspection InfiniteLoopStatement
            while (true) {
              try {
                dispatch(dispatcher, serverSocket.accept(), false);
              }
              catch (final IOException e) {
                if (serverSocket.isClosed()) break;
                log(e);
              }
            }
          }
          finally {
            try { serverSocket.close(); } catch (final IOException ignore) {}
            try { if (secureServerSocket != null) serverSocket.close(); } catch (final IOException ignore) {}
            try { dispatcher.shutdown(); } catch (final Exception e) { log(e); }
            mServerSocket = null;
            mSecureServerSocket = null;
            mStarted.set(false);
          }
        }
      }).start();

      if (secureServerSocket != null) {
        new Thread(new Runnable() {
          @Override public void run() {
            try {
              //noinspection InfiniteLoopStatement
              while (true) {
                try {
                  dispatch(dispatcher, secureServerSocket.accept(), true);
                }
                catch (final IOException e) {
                  if (secureServerSocket.isClosed()) break;
                  log(e);
                }
              }
            }
            finally {
              try { serverSocket.close(); } catch (final IOException ignore) {}
              try { secureServerSocket.close(); } catch (final IOException ignore) {}
              try { dispatcher.shutdown(); } catch (final Exception e) { log(e); }
              mServerSocket = null;
              mSecureServerSocket = null;
              mStarted.set(false);
            }
          }
        }).start();
      }

    }
    catch (final IOException e) {
      log(e);
    }
  }

  public final class Request {
    private final Socket mSocket;
    private final boolean mSecure;
    private Request(final Socket socket, final boolean secure) { mSocket = socket; mSecure = secure; }
    public void serve() {
      HttpServer.this.serve(mSocket, mSecure);
    }
  }

  private void dispatch(final Dispatcher dispatcher, final Socket socket, final boolean secure) {
    if (!Thread.currentThread().isInterrupted()) {
      dispatcher.dispatch(new Request(socket, secure));
    }
  }

  protected Dispatcher createDefaultDispatcher() {
    return new Dispatcher.Default();
  }

  private void serve(final Socket socket, final boolean secure) {
    try {
      final BufferedSource in = Okio.buffer(Okio.source(socket));
      final BufferedSink out = Okio.buffer(Okio.sink(socket));
      try {
        while (true) {
          in.timeout().timeout(5, TimeUnit.SECONDS);
          final String request = in.readUtf8LineStrict().trim();
          if (request == null || request.length() == 0) return;
          final int index1 = request.indexOf(' ');
          final String method = index1 == -1 ? null : request.substring(0, index1);
          final int index2 = method == null ? -1 : request.indexOf(' ', index1 + 1);
          final String path = index2 == -1 ? null : request.substring(index1 + 1, index2);
          final Response response;
          if (method == null || path == null) {
            response = new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
          }
          else {
            final boolean useBody = HttpMethod.permitsRequestBody(method);
            final Headers.Builder headersBuilder = new Headers.Builder();
            String header;
            while ((header = in.readUtf8LineStrict()).length() != 0) {
              headersBuilder.add(header);
            }
            if ("100-continue".equals(headersBuilder.get("Expect"))) {
              response = new Response.Builder().
                statusLine(StatusLines.CONTINUE).noBody().build();
            }
            else {
              final String contentLength = headersBuilder.get("Content-Length");
              final long length = contentLength == null ? -1 : Long.parseLong(contentLength);
              if (length > mMaxRequestSize) {
                response = new Response.Builder().
                  statusLine(StatusLines.PAYLOAD_TOO_LARGE).noBody().build();
              }
              else {
                if (length == 0) {
                  response = handle(secure, method, path, headersBuilder.build(), null);
                }
                else if (length < 0 || "chunked".equals(headersBuilder.get("Transfer-Encoding"))) {
                  if (useBody) {
                    final Buffer body = new Buffer();
                    long total = 0L;
                    boolean invalid = false;
                    while (true) {
                      final long chunkSize = Long.parseLong(in.readUtf8LineStrict().trim(), 16);
                      total += chunkSize;
                      if (chunkSize == 0) {
                        if (in.readUtf8LineStrict().length() != 0) invalid = true;
                        break;
                      }
                      if (total > mMaxRequestSize) {
                        break;
                      }
                      if (!socket.isClosed()) in.read(body, chunkSize);
                      body.flush();
                      if (in.readUtf8LineStrict().length() != 0) {
                        invalid = true;
                        break;
                      }
                    }
                    if (invalid) {
                      response = new Response.Builder().
                        statusLine(StatusLines.BAD_REQUEST).noBody().build();
                    }
                    else if (total > mMaxRequestSize) {
                      response = new Response.Builder().
                        statusLine(StatusLines.PAYLOAD_TOO_LARGE).noBody().build();
                    }
                    else {
                      response = handle(secure, method, path, headersBuilder.build(), body);
                    }
                  }
                  else {
                    response = handle(secure, method, path, headersBuilder.build(), null);
                  }
                }
                else { // length > 0
                  if (useBody) {
                    final Buffer body = new Buffer();
                    if (!socket.isClosed()) in.readFully(body, length);
                    body.flush();
                    response = handle(secure, method, path, headersBuilder.build(), body);
                  }
                  else {
                    response = handle(secure, method, path, headersBuilder.build(), null);
                  }
                }
              }
            }
          }
          final Response r =
            response == null ?
            new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build() :
            response;

          out.writeUtf8(r.protocol().toString().toUpperCase(Locale.US));
          out.writeUtf8(" ");
          out.writeUtf8(String.valueOf(r.code()));
          out.writeUtf8(" ");
          out.writeUtf8(r.message());
          out.writeUtf8("\r\n");
          final Headers headers = r.headers();
          final int headersSize = headers.size();
          for (int i=0; i<headersSize; ++i) {
            out.writeUtf8(headers.name(i));
            out.writeUtf8(": ");
            out.writeUtf8(headers.value(i));
            out.writeUtf8("\r\n");
          }
          out.writeUtf8("\r\n");
          out.flush();

          r.writeBody(in, out, socket);
        }
      }
      catch (final Exception e) {
        throw new RuntimeException(e);
      }
      finally {
        try { in.close(); } catch (final IOException ignore) {}
        try { out.close(); } catch (final IOException ignore) {}
        try { socket.close(); } catch (final IOException ignore) {}
      }
    }
    catch (final Exception e) {
      log(e);
    }
  }

  private Response handle(final boolean secure, final String method, final String path,
                            final Headers requestHeaders, final Buffer requestBody) {
    final String h = requestHeaders.get("Host");
    final int i = h.indexOf(':');
    final String host = i == -1 ? h : h.substring(0, i);
    final int port = i == -1 ? 0 : Integer.valueOf(h.substring(i+1));

    final HttpUrl.Builder url = new HttpUrl.Builder().
      scheme(secure ? "https" : "http").
      host(host).
      addEncodedPathSegments(path.indexOf('/') == 0 ? path.substring(1) : path);
    if (port > 0) url.port(port);
    return handle(secure, method, url.build(), requestHeaders, requestBody);
  }

  protected Response handle(final boolean secure, final String method, final HttpUrl url,
                            final Headers requestHeaders, final Buffer requestBody) {
    final Response.Builder builder = new Response.Builder();
    if ("/test".equals(url.encodedPath())) {
      builder.statusLine(StatusLines.OK);
      builder.headers(requestHeaders);
      if (requestBody != null) {
        final MediaType mediaType = MediaType.parse(requestHeaders.get("Content-Type"));
        builder.body(new Response.BufferResponse(mediaType, requestBody));
      }
      else {
        builder.noBody();
      }
    }
    else if ("GET".equalsIgnoreCase(method)) {
      builder.statusLine(StatusLines.NOT_FOUND).noBody();
    }
    else {
      builder.statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody();
    }
    return builder.build();
  }

}
