package info.jdavid.ok.server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import static info.jdavid.ok.server.Logger.logger;


/**
 * Http Server class.
 */
@SuppressWarnings({ "WeakerAccess" })
public class HttpServer {

  final AtomicBoolean started = new AtomicBoolean();
  final AtomicBoolean setup = new AtomicBoolean();
  int port = 8080; // 80
  int securePort = 8181; // 443
  String hostname = null;
  long maxRequestSize = 65536;
  ServerSocket serverSocket = null;
  SecureServerSocket secureServerSocket = null;
  Dispatcher dispatcher = null;
  KeepAliveStrategy keepAliveStrategy = KeepAliveStrategy.DEFAULT;
  RequestHandler requestHandler = null;
  Https https = null;
  private final Lock serverSocketLock = new ReentrantLock();

  /**
   * Sets the port number for the server. You can use 0 for "none" (to disable the port binding).
   * @param port the port number.
   * @param securePort the secure port number.
   * @return this.
   */
  public final HttpServer ports(final int port, final int securePort) {
    if (started.get()) {
      throw new IllegalStateException("The port number cannot be changed while the server is running.");
    }
    this.port = port;
    this.securePort = securePort;
    return this;
  }

  /**
   * Sets the port number for the server. You can use 0 for "none" (to disable the port binding).
   * @param port the port number.
   * @return this.
   */
  public final HttpServer port(final int port) {
    if (started.get()) {
      throw new IllegalStateException("The port number cannot be changed while the server is running.");
    }
    this.port = port;
    return this;
  }

  /**
   * Gets the port number for the server.
   * @return the server port number.
   */
  public final int port() {
    return port;
  }

  /**
   * Sets the port number for the server (secure connections). You can use 0 for "none"
   * (to disable the port binding).
   * @param port the port number.
   * @return this.
   */
  @SuppressWarnings("UnusedReturnValue")
  public final HttpServer securePort(final int port) {
    if (started.get()) {
      throw new IllegalStateException("The port number cannot be changed while the server is running.");
    }
    this.securePort = port;
    return this;
  }

  /**
   * Gets the port number for the server (secure connections).
   * @return the server secure port number.
   */
  public final int securePort() {
    return securePort;
  }

  /**
   * Sets the host name for the server.
   * @param hostname the host name.
   * @return this.
   */
  public final HttpServer hostname(final String hostname) {
    if (started.get()) {
      throw new IllegalStateException("The host name cannot be changed while the server is running.");
    }
    this.hostname = hostname;
    return this;
  }

  /**
   * Gets the host name for the server.
   * @return the server host name.
   */
  public final String hostname() {
    return hostname;
  }

  /**
   * Sets the maximum request size allowed by the server.
   * @param size the maximum request size in bytes.
   * @return this.
   */
  public final HttpServer maxRequestSize(final long size) {
    if (started.get()) {
      throw new IllegalStateException("The max request size cannot be changed while the server is running.");
    }
    this.maxRequestSize = size;
    return this;
  }

  /**
   * Gets the maximum request size allowed by the server.
   * @return the maximum allowed request size.
   */
  public final long maxRequestSize() {
    return maxRequestSize;
  }

  /**
   * Sets the Keep-Alive strategy.
   * @param strategy the strategy.
   * @return this
   */
  public final HttpServer keepAliveStrategy(@Nullable final KeepAliveStrategy strategy) {
    if (started.get()) {
      throw new IllegalStateException(
        "The keep-alive strategy cannot be changed while the server is running.");
    }
    this.keepAliveStrategy = strategy == null ? KeepAliveStrategy.DEFAULT : strategy;
    return this;
  }

  /**
   * Validates the specified handler before it is set as the request handler for the server.
   * @param handler the candidate request handler.
   * @throws IllegalArgumentException if the handler is not suitable.
   */
  protected void validateHandler(@SuppressWarnings("unused") final RequestHandler handler) {}

  /**
   * Sets the request handler.
   * @param handler the request handler.
   * @return this
   */
  public final HttpServer requestHandler(final RequestHandler handler) {
    if (started.get()) {
      throw new IllegalStateException("The request handler cannot be changed while the server is running.");
    }
    validateHandler(handler);
    this.requestHandler = handler;
    return this;
  }

  private RequestHandler requestHandler() {
    RequestHandler handler = requestHandler;
    if (handler == null) {
      handler = requestHandler = RequestHandlerChain.createDefaultChain();
    }
    return handler;
  }

  /**
   * Validates the specified dispatcher before it is set as the request dispatcher for the server.
   * @param dispatcher the candidate request dispatcher.
   * @throws IllegalArgumentException if the dispatcher is not suitable.
   */
  @SuppressWarnings("unused")
  protected void validateDispatcher(final Dispatcher dispatcher) {}

  /**
   * Sets a custom dispatcher.
   * @param dispatcher the dispatcher responsible for distributing the connection requests.
   * @return this
   */
  public final HttpServer dispatcher(final Dispatcher dispatcher) {
    if (started.get()) {
      throw new IllegalStateException("The dispatcher cannot be changed while the server is running.");
    }
    validateDispatcher(dispatcher);
    this.dispatcher = dispatcher;
    return this;
  }

  /**
   * Validates the specified https settings before they are set for the server.
   * @param https the candidate https settings.
   * @throws IllegalArgumentException if the settings is not suitable.
   */
  @SuppressWarnings("unused")
  protected void validateHttps(final Https https) {}

  /**
   * Sets the Https provider.
   * @param https the Https provider.
   * @return this
   */
  public final HttpServer https(final Https https) {
    if (started.get()) {
      throw new IllegalStateException("The certificates cannot be changed while the server is running.");
    }
    validateHttps(https);
    this.https = https;
    return this;
  }

  private Dispatcher dispatcher() {
    Dispatcher dispatcher = this.dispatcher;
    if (dispatcher == null) {
      dispatcher = this.dispatcher = new Dispatcher.Default();
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
  @SuppressWarnings("Duplicates")
  public void shutdown() {
    if (!started.get()) return;
    try {
      serverSocketLock.lock();
      if (serverSocket != null) serverSocket.close();
    }
    catch (final IOException ignore) {}
    finally {
      serverSocketLock.unlock();
    }
    try {
      serverSocketLock.lock();
      if (secureServerSocket != null) secureServerSocket.close();
    }
    catch (final IOException ignore) {}
    finally {
      serverSocketLock.unlock();
    }
    try {
      dispatcher.shutdown();
    }
    //catch (final Exception ignore) {}
    finally {
      started.set(false);
    }
  }

  /**
   * Returns whether the server is running or not.
   * @return true if the server has been started, false if it hasn't, or has been stopped since.
   */
  public final boolean isRunning() {
    return started.get();
  }

  /**
   * Starts the server.
   */
  public final void start() {
    if (started.getAndSet(true)) {
      throw new IllegalStateException("The server has already been started.");
    }
    try {
      if (!setup.getAndSet(true)) setup();
      final RequestHandler handler = requestHandler();
      if (handler instanceof AbstractRequestHandler) {
        ((AbstractRequestHandler)handler).init();
      }
      final Dispatcher dispatcher = dispatcher();
      dispatcher.start();
      final InetAddress address;
      if (hostname == null) {
        address = null; //new InetSocketAddress(0).getAddress();
      }
      else {
        address = InetAddress.getByName(hostname);
      }

      final ServerSocket serverSocket;
      if (port > 0) {
        serverSocket = new ServerSocket(port, -1, address);
        try {
          serverSocketLock.lock();
          this.serverSocket = serverSocket;
        }
        finally {
          serverSocketLock.unlock();
        }
        serverSocket.setReuseAddress(true);
      }
      else {
        serverSocket = null;
      }

      final Https https = this.https;
      final SecureServerSocket secureServerSocket;
      if (https != null && securePort > 0) {
        secureServerSocket = new SecureServerSocket(securePort, address);
        secureServerSocket.setReuseAddress(true);
        try {
          serverSocketLock.lock();
          this.secureServerSocket = secureServerSocket;
        }
        finally {
          serverSocketLock.unlock();
        }
      }
      else {
        secureServerSocket = this.secureServerSocket = null;
      }

      if (serverSocket != null) {
        new Thread(new Runnable() {
          @Override public void run() {
            try {
              //noinspection InfiniteLoopStatement
              while (true) {
                try {
                  dispatch(dispatcher, serverSocket.accept(), false, secureServerSocket == null);
                }
                catch (final BindException e) {
                  logger.warn("Could not bind to port: " + port, e);
                  break;
                }
                catch (final IOException e) {
                  if (serverSocket.isClosed()) {
                    break;
                  }
                  logger.warn("HTTP", e);
                }
              }
            }
            finally {
              try {
                if (!serverSocket.isClosed()) serverSocket.close();
              }
              catch (final IOException ignore) {}
              try {
                serverSocketLock.lock();
                HttpServer.this.serverSocket = null;
              }
              finally {
                serverSocketLock.unlock();
              }
            }
          }
        }).start();
      }

      if (secureServerSocket != null) {
        new Thread(new Runnable() {
          @Override public void run() {
            try {
              //noinspection InfiniteLoopStatement
              while (true) {
                try {
                  dispatch(dispatcher, secureServerSocket.accept(), true, false);
                }
                catch (final BindException e) {
                  logger.warn("Could not bind to port: " + port, e);
                  break;
                }
                catch (final IOException e) {
                  if (secureServerSocket.isClosed()) {
                    break;
                  }
                  logger.warn("HTTPS", e);
                }
              }
            }
            finally {
              try {
                if (!secureServerSocket.isClosed()) secureServerSocket.close();
              }
              catch (final IOException ignore) {}
              try {
                serverSocketLock.lock();
                HttpServer.this.secureServerSocket = null;
              }
              finally {
                serverSocketLock.unlock();
              }
            }
          }
        }).start();
      }
    }
    catch (final BindException e) {
      throw new RuntimeException(e);
    }
    catch (final IOException e) {
      logger.error(e.getMessage(), e);
    }
  }

  public final class Request {
    private final Socket mSocket;
    private final boolean mSecure;
    private final boolean mInsecureOnly;
    private Request(final Socket socket, final boolean secure, final boolean insecureOnly) {
      mSocket = socket;
      mSecure = secure;
      mInsecureOnly = insecureOnly;
    }
    public void serve() {
      final Socket socket = mSocket;
      if (mSecure) {
        boolean http2 = false;
        String hostname = null;
        SSLSocket sslSocket = null;
        try {
          final Handshake handshake = Handshake.read(socket);
          if (handshake != null) {
            final Https https = HttpServer.this.https;
            hostname = handshake.hostname;
            http2 = handshake.http2 && https.http2;
            try {
              sslSocket = https.createSSLSocket(socket, hostname, http2);
            }
            catch (final SSLHandshakeException e) {
              final String[] cipherSuites = handshake.getCipherSuites();
              final StringBuilder s = new StringBuilder();
              boolean addSeparator = false;
              for (final String value: cipherSuites) {
                if (addSeparator) {
                  s.append(' ');
                }
                else {
                  addSeparator = true;
                }
                s.append(value);
              }
              logger.info(s.toString());
              throw new IOException(e);
            }
          }
        }
        catch (final SocketTimeoutException ignore) {}
        catch (final Exception e) {
          logger.warn(e.getMessage(), e);
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
              if (HttpServer.this.hostname == null) {
                hostname = "localhost";
              }
              else {
                hostname = HttpServer.this.hostname;
              }
            }
            HttpServer.this.serveHttp2(sslSocket, hostname);
          }
          else {
            HttpServer.this.serveHttp1(sslSocket, true, false);
          }
        }
      }
      else {
        HttpServer.this.serveHttp1(socket, false, mInsecureOnly);
      }
    }
  }

  private void dispatch(final Dispatcher dispatcher, final Socket socket,
                        final boolean secure, final boolean insecureOnly) {
    if (!Thread.currentThread().isInterrupted()) {
      dispatcher.dispatch(new Request(socket, secure, insecureOnly));
    }
  }

  private void serveHttp1(final Socket socket, final boolean secure, final boolean insecureOnly) {
    try {
      Http11.serve(socket, secure, insecureOnly, maxRequestSize, keepAliveStrategy, requestHandler);
    }
    catch (final SocketTimeoutException ignore) {}
    catch (final Exception e) {
      logger.warn(e.getMessage(), e);
    }
  }

  private void serveHttp2(final SSLSocket socket, final String hostname) {
    try {
      Http2.serve(socket, hostname, maxRequestSize, keepAliveStrategy, requestHandler);
    }
    catch (final SocketTimeoutException ignore) {}
    catch (final Exception e) {
      logger.warn(e.getMessage(), e);
    }
  }

  private static class SecureServerSocket extends ServerSocket {
    SecureServerSocket(final int port, @Nullable final InetAddress address) throws IOException {
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
