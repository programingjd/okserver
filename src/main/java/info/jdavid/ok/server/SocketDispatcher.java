package info.jdavid.ok.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import static info.jdavid.ok.server.Logger.logger;

public abstract class SocketDispatcher extends Dispatcher<ServerSocket> {

  @Override
  protected void loop(final ServerSocket socket,
                      final boolean secure, final boolean insecureOnly,
                      final @Nullable Https https,
                      final @Nullable String hostname,
                      final long maxRequestSize,
                      final KeepAliveStrategy keepAliveStrategy,
                      final RequestHandler requestHandler) {
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          dispatchLoop(socket, secure, insecureOnly, https, hostname,
                       maxRequestSize, keepAliveStrategy, requestHandler);
        }
        finally {
          close(socket);
        }
      }
    }).start();
  }

  private void dispatchLoop(final ServerSocket socket,
                            final boolean secure, final boolean insecureOnly,
                            final @Nullable Https https,
                            final @Nullable String hostname,
                            final long maxRequestSize,
                            final KeepAliveStrategy keepAliveStrategy,
                            final RequestHandler requestHandler) {
    while (true) {
      try {
        if (!Thread.currentThread().isInterrupted()) {
          dispatch(new Request(socket.accept(), secure, insecureOnly, https, hostname,
                               maxRequestSize, keepAliveStrategy, requestHandler));
        }
      }
      catch (final BindException e) {
        logger.warn("Could not bind to " + (secure ? "secure" : "insecure") + " port.", e);
        break;
      }
      catch (final IOException e) {
        if (socket.isClosed()) {
          break;
        }
        logger.warn(secure ? "HTTPS" : "HTTP", e);
      }
    }
  }

  @Override
  protected void initSockets(final int insecurePort, final int securePort, final @Nullable Https https,
                             final @Nullable InetAddress address) throws IOException {
    if (insecurePort > 0) {
      insecureSocket = new ServerSocket(insecurePort, address);
      insecureSocket.setReuseAddress(true);
    }
    if (securePort > 0 && https != null) {
      secureSocket = new SecureServerSocket(securePort, address);
      secureSocket.setReuseAddress(true);
    }
  }

  private static class SecureServerSocket extends ServerSocket implements Closeable {
    SecureServerSocket(final int port, @Nullable final InetAddress address) throws IOException {
      super(port, address);
    }
    @Override public Socket accept() throws IOException {
      if (isClosed()) throw new SocketException("Socket is closed");
      if (!isBound()) throw new SocketException("Socket is not bound yet");
      final Handshake.HandshakeSocket s = new Handshake.HandshakeSocket();
      implAccept(s);
      return s;
    }
  }

  public static final class Request {
    private final Socket socket;
    private final boolean secure;
    private final boolean insecureOnly;
    private final Https https;
    private final String hostname;
    private final long maxRequestSize;
    private final KeepAliveStrategy keepAliveStrategy;
    private final RequestHandler requestHandler;

    private Request(final Socket socket,
                    final boolean secure, final boolean insecureOnly,
                    final @Nullable Https https,
                    final @Nullable String hostname,
                    final long maxRequestSize,
                    final KeepAliveStrategy keepAliveStrategy,
                    final RequestHandler requestHandler) {
      this.socket = socket;
      this.secure = secure;
      this.insecureOnly = insecureOnly;
      this.https = https;
      this.hostname = hostname;
      this.maxRequestSize = maxRequestSize;
      this.keepAliveStrategy = keepAliveStrategy;
      this.requestHandler = requestHandler;
    }

    public void serve() {
      if (secure) {
        assert https != null;
        boolean http2 = false;
        String hostname = null;
        SSLSocket sslSocket = null;
        try {
          final Handshake handshake = Handshake.read(socket);
          if (handshake != null) {
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
              if (this.hostname == null) {
                hostname = "localhost";
              }
              else {
                hostname = this.hostname;
              }
            }
            serveHttp2(sslSocket, hostname);
          }
          else {
            serveHttp1(sslSocket, true, false);
          }
        }
      }
      else {
        serveHttp1(socket, false, insecureOnly);
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

  }

  /**
   * Default dispatcher. Requests are handled by a set of threads from a CachedThreadPool.
   */
  public static class Default extends ThreadPoolDispatcher {
    @Override protected ExecutorService createThreadPool() {
      return Executors.newCachedThreadPool();
    }
  }

  /**
   * Variation on the default dispatcher that keeps track of the number of active connections.
   */
  @SuppressWarnings("unused")
  public static class Logged extends SocketDispatcher {
    private ExecutorService mExecutors = null;
    private ExecutorService mExecutor = null;
    private final AtomicInteger mConnections = new AtomicInteger();

    public Logged() throws IOException { super(); }
    @Override public void start() {
      mConnections.set(0);
      mExecutors = Executors.newCachedThreadPool();
      mExecutor = Executors.newSingleThreadExecutor();
      mExecutor.execute(new Runnable() {
        @Override public void run() {
          while (!Thread.interrupted()) {
            try { Thread.sleep(60000L); } catch (final InterruptedException ignore) { break; }
            System.gc();
            final float used =
              Math.round((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024f);
            logger.info(used + "k");
          }
        }
      });
    }
    @Override public void dispatch(final Request request) {
      mExecutors.execute(
        new Runnable() {
          @Override public void run() {
            logger.info("Connections: " + mConnections.incrementAndGet());
            try {
              request.serve();
            }
            finally {
              logger.info("Connections: " + mConnections.decrementAndGet());
            }
          }
        }
      );
    }
    @Override public void shutdown() {
      if (mConnections.getAndSet(-9999) < 0) return;
      mExecutors.shutdownNow();
      try {
        if (!mExecutors.awaitTermination(15, TimeUnit.SECONDS)) {
          throw new RuntimeException("Failed to stop request handler.");
        }
      }
      catch (final InterruptedException ignore) {}
      mExecutors = null;
      mExecutor.shutdownNow();
      try {
        if (!mExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
          throw new RuntimeException("Failed to stop request handler.");
        }
      }
      catch (final InterruptedException ignore) {}
      mExecutor = null;
    }
  }

  /**
   * Dispatcher implementation that simply runs the dispatch job synchronously on the current thread.
   * WARNING: most clients keep connections alive and therefore will keep the dispatch thread busy for a
   * little while even after the request has been served.
   */
  @SuppressWarnings("unused")
  public static class SameThreadDispatcher extends SocketDispatcher {
    @Override public void start() {}
    @Override public void dispatch(final Request request) {
      request.serve();
    }
    @Override public void shutdown() {}
  }

  /**
   * Dispatcher implementation that only uses one thread.
   * WARNING: most clients keep connections alive and therefore will keep the dispatch thread busy for a
   * little while even after the request has been served.
   */
  @SuppressWarnings("unused")
  public static class SingleThreadDispatcher extends ThreadPoolDispatcher {
    @Override protected ExecutorService createThreadPool() {
      return Executors.newSingleThreadExecutor();
    }
  }

  /**
   * Dispatcher implementation that uses a fixed thread pool with the specified number of threads.
   */
  @SuppressWarnings("unused")
  public static class MultiThreadsDispatcher extends ThreadPoolDispatcher {
    private final int threadCount;
    public MultiThreadsDispatcher(final int threadCount) {
      this.threadCount = threadCount;
    }
    @Override protected ExecutorService createThreadPool() {
      return Executors.newFixedThreadPool(threadCount);
    }
  }

  /**
   * Dispatcher implementation that uses a thread pool.
   * WARNING: most clients keep connections alive and therefore will keep the dispatch thread busy for a
   * little while even after the request has been served.
   */
  public static abstract class ThreadPoolDispatcher extends SocketDispatcher {
    private ExecutorService mExecutors = null;
    private final AtomicBoolean mShutdown = new AtomicBoolean();

    /**
     * Creates the thread pool that will be used to handle the server requests.
     * @return the thread pool.
     */
    protected abstract ExecutorService createThreadPool();

    @Override public void start() {
      mShutdown.set(false);
      mExecutors = createThreadPool();
    }
    @Override public void dispatch(final Request request) {
      mExecutors.execute(
        new Runnable() {
          @Override public void run() {
            request.serve();
          }
        }
      );
    }
    @Override public void shutdown() {
      if (mShutdown.getAndSet(true)) return;
      mExecutors.shutdownNow();
      try {
        if (!mExecutors.awaitTermination(15, TimeUnit.SECONDS)) {
          throw new RuntimeException("Failed to stop request handler.");
        }
      }
      catch (final InterruptedException ignore) {}
      mExecutors = null;
    }
  }

}
