package info.jdavid.ok.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static info.jdavid.ok.server.Logger.logger;


/**
 * The dispatcher is responsible for dispatching requests to workers.
 */
@SuppressWarnings({ "WeakerAccess" })
public interface Dispatcher {

  /**
   * Starts the dispatcher.
   */
  public void start();

  /**
   * Dispatches a request.
   * @param request the request.
   */
  public void dispatch(final HttpServer.Request request);

  /**
   * Shuts down the dispatcher.
   */
  public void shutdown();

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
  public static class Logged implements Dispatcher {
    private ExecutorService mExecutors = null;
    private ExecutorService mExecutor = null;
    private final AtomicInteger mConnections = new AtomicInteger();
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
    @Override public void dispatch(final HttpServer.Request request) {
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
  public static class SameThreadDispatcher implements Dispatcher {
    @Override public void start() {}
    @Override public void dispatch(final HttpServer.Request request) {
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
  public static abstract class ThreadPoolDispatcher implements Dispatcher {
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
    @Override public void dispatch(final HttpServer.Request request) {
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
