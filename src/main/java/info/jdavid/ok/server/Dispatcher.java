package info.jdavid.ok.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public interface Dispatcher {

  public void start();

  public void dispatch(final HttpServer.Request request);

  public void shutdown();

  static class Default implements Dispatcher {
    private ExecutorService mExecutors = null;
    @Override public void start() { mExecutors = Executors.newCachedThreadPool(); }
    @Override public void dispatch(final HttpServer.Request request) {
      mExecutors.execute(new Runnable() { @Override public void run() { request.serve(); } });
    }
    @Override public void shutdown() {
      try {
        mExecutors.awaitTermination(5, TimeUnit.SECONDS);
      }
      catch (final InterruptedException ignore) {}
      mExecutors.shutdownNow();
      mExecutors = null;
    }
  }

}
