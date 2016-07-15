package info.jdavid.ok.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static info.jdavid.ok.server.HttpServer.log;

@SuppressWarnings("WeakerAccess")
public interface Dispatcher {

  public void start();

  public void dispatch(final HttpServer.Request request);

  public void shutdown();

  public static class Default implements Dispatcher {
    private ExecutorService mExecutors = null;
    @Override public void start() { mExecutors = Executors.newCachedThreadPool(); }
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
      mExecutors.shutdownNow();
      try {
        mExecutors.awaitTermination(5, TimeUnit.SECONDS);
      }
      catch (final InterruptedException ignore) {}
      mExecutors = null;
    }
  }

  public static class Logged implements Dispatcher {
    private ExecutorService mExecutors = null;
    private final AtomicInteger mConnections = new AtomicInteger();
    @Override public void start() { mExecutors = Executors.newCachedThreadPool(); }
    @Override public void dispatch(final HttpServer.Request request) {
      mExecutors.execute(
        new Runnable() {
          @Override public void run() {
            log("Connections: " + mConnections.incrementAndGet());
            try {
              request.serve();
            }
            finally {
              log("Connections: " + mConnections.decrementAndGet());
            }
          }
        }
      );
    }
    @Override public void shutdown() {
      mExecutors.shutdownNow();
      try {
        mExecutors.awaitTermination(5, TimeUnit.SECONDS);
      }
      catch (final InterruptedException ignore) {}
      mExecutors = null;
    }
  }

}
