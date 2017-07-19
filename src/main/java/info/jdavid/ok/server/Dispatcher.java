package info.jdavid.ok.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;


/**
 * The dispatcher is responsible for dispatching requests to workers.
 */
@SuppressWarnings({ "WeakerAccess" })
public abstract class Dispatcher<T extends Closeable> {

  protected @Nullable T insecureSocket = null;
  protected @Nullable T secureSocket = null;

  private final Lock serverSocketLock = new ReentrantLock();

  /**
   * Starts the dispatcher.
   */
  public abstract void start();

  /**
   * Dispatches a request.
   * @param request the request.
   */
  public abstract void dispatch(final SocketDispatcher.Request request);

  /**
   * Shuts down the dispatcher.
   */
  public abstract void shutdown();

  public void close() {
    if (insecureSocket != null) close(insecureSocket);
    if (secureSocket != null) close(secureSocket);
  }

  protected void close(final Closeable socket) {
    serverSocketLock.lock();
    try {
      socket.close();
    }
    catch (final IOException ignore) {}
    finally {
      serverSocketLock.unlock();
    }
  }

  protected abstract void loop(final T socket, final boolean secure, final boolean insecureOnly,
                               final @Nullable Https https, final @Nullable String hostname,
                               final long maxRequestSize,
                               final KeepAliveStrategy keepAliveStrategy,
                               final RequestHandler requestHandler);

  protected abstract void initSockets(final int insecurePort, final int securePort,
                                      final @Nullable Https https,
                                      final @Nullable InetAddress address) throws IOException;

  final void loop(final int insecurePort, final int securePort,
                  final @Nullable Https https,
                  final @Nullable InetAddress address,
                  final @Nullable String hostname,
                  final long maxRequestSize,
                  final KeepAliveStrategy keepAliveStrategy,
                  final RequestHandler requestHandler) throws IOException {
    initSockets(insecurePort, securePort, https, address);
    if (insecureSocket != null) {
      loop(insecureSocket, false, secureSocket == null, https, hostname,
           maxRequestSize, keepAliveStrategy, requestHandler);
    }
    if (secureSocket != null) {
      loop(secureSocket, true, false, https, hostname,
           maxRequestSize, keepAliveStrategy, requestHandler);
    }
  }

}
