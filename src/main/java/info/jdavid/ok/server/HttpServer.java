package info.jdavid.ok.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import static info.jdavid.ok.server.Logger.log;


/**
 * Http Server class.
 */
@SuppressWarnings({ "unused", "WeakerAccess", "Convert2Lambda" })
public class HttpServer {

  private final AtomicBoolean mStarted = new AtomicBoolean();
  private final AtomicBoolean mSetup = new AtomicBoolean();
  private int mPort = 8080; // 80
  private int mSecurePort = 8181; // 443
  private String mHostname = null;
  private long mMaxRequestSize = 65536;
  private ServerSocket mServerSocket = null;
  private SecureServerSocket mSecureServerSocket = null;
  private Dispatcher mDispatcher = null;
  private KeepAliveStrategy mKeepAliveStrategy = KeepAliveStrategy.DEFAULT;
  private RequestHandler mRequestHandler = RequestHandler.DEFAULT;
  private Https mHttps = Https.DISABLED;

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
   * Sets the Keep-Alive strategy.
   * @param strategy the strategy.
   * @return this
   */
  public final HttpServer keepAliveStrategy(final KeepAliveStrategy strategy) {
    if (mStarted.get()) {
      throw new IllegalStateException(
        "The keep-alive strategy cannot be changed while the server is running.");
    }
    this.mKeepAliveStrategy = strategy == null ? KeepAliveStrategy.DEFAULT : strategy;
    return this;
  }

  protected void validateHandler() {}

  /**
   * Sets the request handler.
   * @param handler the request handler.
   * @return this
   */
  public final HttpServer requestHandler(final RequestHandler handler) {
    if (mStarted.get()) {
      throw new IllegalStateException("The request handler cannot be changed while the server is running.");
    }
    validateHandler();
    this.mRequestHandler = handler == null ? RequestHandler.DEFAULT : handler;
    return this;
  }

  protected void validateDispatcher() {}

  /**
   * Sets a custom dispatcher.
   * @param dispatcher the dispatcher responsible for distributing the connection requests.
   * @return this
   */
  public final HttpServer dispatcher(final Dispatcher dispatcher) {
    if (mStarted.get()) {
      throw new IllegalStateException("The dispatcher cannot be changed while the server is running.");
    }
    validateDispatcher();
    this.mDispatcher = dispatcher;
    return this;
  }

  protected void validateHttps() {}

  /**
   * Sets the Https provider.
   * @param https the Https provider.
   * @return this
   */
  public final HttpServer https(final Https https) {
    if (mStarted.get()) {
      throw new IllegalStateException("The certificates cannot be changed while the server is running.");
    }
    validateHttps();
    this.mHttps = https == null ? Https.DISABLED : https;
    return this;
  }

  private Dispatcher dispatcher() {
    Dispatcher dispatcher = mDispatcher;
    if (dispatcher == null) {
      dispatcher = mDispatcher = new Dispatcher.Default();
    }
    return dispatcher;
  }

  /**
   * Override to perform tasks right after the server is started. By default, it does nothing.
   */
  protected void setup() {}

  /**
   * Shuts down the server.
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

      final Https https = mHttps;
      final SecureServerSocket secureServerSocket;
      if (https == Https.DISABLED) {
        secureServerSocket = mSecureServerSocket = null;
      }
      else {
        secureServerSocket = mSecureServerSocket = new SecureServerSocket(mSecurePort, address);
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
                if (serverSocket.isClosed()) {
                  break;
                }
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
                  if (secureServerSocket.isClosed()) {
                    break;
                  }
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
      final Socket socket = mSocket;
      if (mSecure) {
        boolean http2 = false;
        String hostname = null;
        SSLSocket sslSocket = null;
        try {
          final Handshake handshake = Handshake.read(socket);
          if (handshake != null) {
            final Https https = mHttps;
            hostname = handshake.hostname;
            http2 = handshake.http2 && https.http2;
            try {
              sslSocket = https.createSSLSocket(socket, hostname, http2);
            }
            catch (final SSLHandshakeException e) {
              log(handshake.getCipherSuites());
              throw new IOException(e);
            }
          }
        }
        catch (final SocketTimeoutException ignore) {}
        catch (final Exception e) {
          log(e);
        }
        if (sslSocket == null) {
          try {
            socket.close();
          }
          catch (final IOException ignore) {}
        }
        else {
          if (http2) {
            if (hostname == null) {
              if (mHostname == null) {
                hostname = "localhost";
              }
              else {
                hostname = mHostname;
              }
            }
            HttpServer.this.serveHttp2(sslSocket, hostname);
          }
          else {
            HttpServer.this.serveHttp1(sslSocket, true);
          }
        }
      }
      else {
        HttpServer.this.serveHttp1(socket, false);
      }
    }
  }

  private void dispatch(final Dispatcher dispatcher, final Socket socket, final boolean secure) {
    if (!Thread.currentThread().isInterrupted()) {
      dispatcher.dispatch(new Request(socket, secure));
    }
  }

  private void serveHttp1(final Socket socket, final boolean secure) {
    try {
      Http11.serve(socket, secure, mMaxRequestSize, mKeepAliveStrategy, mRequestHandler);
    }
    catch (final SocketTimeoutException ignore) {}
    catch (final Exception e) {
      log(e);
    }
  }

  private void serveHttp2(final SSLSocket socket, final String hostname) {
    try {
      Http2.serve(socket, hostname, mMaxRequestSize, mKeepAliveStrategy, mRequestHandler);
    }
    catch (final SocketTimeoutException ignore) {}
    catch (final Exception e) {
      log(e);
    }
  }

  private static class SecureServerSocket extends ServerSocket {
    SecureServerSocket(final int port, final InetAddress address) throws IOException {
      super(port, -1, address);
    }
    @Override public Socket accept() throws IOException {
      if (isClosed()) throw new SocketException("Socket is closed");
      if (!isBound()) throw new SocketException("Socket is not bound yet");
      final Handshake.HandshakeSocket s = new Handshake.HandshakeSocket();
      implAccept(s);
      return s;
    }
  }

}
