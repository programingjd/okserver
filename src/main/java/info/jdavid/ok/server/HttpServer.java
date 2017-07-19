package info.jdavid.ok.server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

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
  Dispatcher dispatcher = null;
  KeepAliveStrategy keepAliveStrategy = KeepAliveStrategy.DEFAULT;
  RequestHandler requestHandler = null;
  Https https = null;

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
      dispatcher = this.dispatcher = new SocketDispatcher.Default();
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
    if (dispatcher != null) {
      dispatcher.close();
      try {
        dispatcher.shutdown();
      }
      //catch (final Exception ignore) {}
      finally {
        dispatcher = null;
        started.set(false);
      }
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

      dispatcher.loop(port, securePort, https, address, hostname,
                      maxRequestSize, keepAliveStrategy, handler);
    }
    catch (final BindException e) {
      throw new RuntimeException(e);
    }
    catch (final IOException e) {
      logger.error(e.getMessage(), e);
    }
  }

}
