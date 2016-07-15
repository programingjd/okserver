package info.jdavid.ok.server;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
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
  private ServerSocket mSecureServerSocket = null;
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

  protected SSLParameters getSSLParameters() {
    final SSLParameters parameters = new SSLParameters();
    parameters.setProtocols( new String[] {
      "TLSv1.2"
    });
    parameters.setCipherSuites(new String[] {
      "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
    });
    return parameters;
  }

  private void assertThat(final boolean assertion) {
    if (!assertion) throw new RuntimeException();
  }

  private static int int24(final byte[] bytes) {
    return ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
  }

  private SSLSocket createSSLSocket(final SSLContext context, final SSLParameters parameters,
                                    final Socket socket) throws IOException {
    if (context == null) return null;
    final SSLSocketFactory sslFactory = context.getSocketFactory();
    final BufferedSource source = Okio.buffer(Okio.source(socket));
    final Buffer buffer = new Buffer();
    final byte handshake = source.readByte();
    buffer.writeByte(handshake);
    assertThat(handshake == 0x16);
    final byte major = source.readByte();
    final byte minor = source.readByte();
    buffer.writeByte(major);
    buffer.writeByte(minor);
    final short recordLength = source.readShort();
    buffer.writeShort(recordLength);
    final byte hello = source.readByte();
    buffer.writeByte(hello);
    assertThat(hello == 0x01);
    final byte[] lengthBytes = source.readByteArray(3);
    buffer.write(lengthBytes);
    final int handshakeLength = int24(lengthBytes);
    assertThat(handshakeLength <= recordLength - 4);
    final byte helloMajor = source.readByte();
    final byte helloMinor = source.readByte();
    buffer.writeByte(helloMajor);
    buffer.writeByte(helloMinor);
    final byte[] random = source.readByteArray(32);
    buffer.write(random);
    final byte sessionIdLength = source.readByte();
    buffer.writeByte(sessionIdLength);
    final byte[] sessionId = source.readByteArray(sessionIdLength);
    buffer.write(sessionId);
    final short cipherSuiteLength = source.readShort();
    buffer.writeShort(cipherSuiteLength);
    final byte[] cipherSuite = source.readByteArray(cipherSuiteLength);
    buffer.write(cipherSuite);
    final byte compressionMethodsLength = source.readByte();
    buffer.writeByte(compressionMethodsLength);
    final byte[] compressionMethods = source.readByteArray(compressionMethodsLength);
    buffer.write(compressionMethods);
    if (buffer.size() < handshakeLength + 9) {
      final short extensionsLength = source.readShort();
      buffer.writeShort(extensionsLength);
      int len = extensionsLength;
      while (len > 0) {
        final short extensionType = source.readShort();
        buffer.writeShort(extensionType);
        final short extensionLength = source.readShort();
        buffer.writeShort(extensionLength);
        final byte[] extension = source.readByteArray(extensionLength);
        buffer.write(extension);
        len -= extensionLength + 4;
      }
    }
    final ByteArrayInputStream consumed = new ByteArrayInputStream(buffer.readByteArray());
    final SSLSocket sslSocket = (SSLSocket)sslFactory.createSocket(socket, consumed, true);
    sslSocket.setSSLParameters(parameters);
    return sslSocket;
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
      final SSLParameters parameters;
      final ServerSocket secureServerSocket;
      if (ssl == null) {
        parameters = null;
        secureServerSocket = mSecureServerSocket = null;
      }
      else {
        parameters = getSSLParameters();
        secureServerSocket = mSecureServerSocket = new ServerSocket(mSecurePort, -1, address);
        secureServerSocket.setReuseAddress(true);
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
                  final Socket socket = secureServerSocket.accept();
                  final SSLSocket sslSocket = createSSLSocket(ssl, parameters, socket);
                  dispatch(dispatcher, sslSocket, true);
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

  protected boolean use(final BufferedSource in, final int reuse) {
    in.timeout().timeout(reuse == 0 ? 30 : 5, TimeUnit.SECONDS);
    return true;
  }

  private void serve(final Socket socket, final boolean secure) {
    try {
      Http11.serve(this, socket, secure, mMaxRequestSize);
    }
    catch (final SocketTimeoutException ignore) {}
    catch (final Exception e) {
      log(e);
    }
  }

  protected final Response handle(final boolean secure, final String method, final String path,
                                  final Headers requestHeaders, final Buffer requestBody) {
    final String h = requestHeaders.get("Host");
    if (h == null) {
      return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
    }
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
