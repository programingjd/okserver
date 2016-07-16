package info.jdavid.ok.server;

/**
 * The Keep-Alive strategy for server sockets.
 */
@SuppressWarnings("WeakerAccess")
public interface KeepAliveStrategy {

  /**
   * Returns how long the connection should be kept alive, in seconds. Anything inferior or equal to zero
   * means that the connection should not be reused.
   * @param reuse the reuse count (0 for the first time).
   * @return the number of seconds before closing the socket.
   */
  public int timeout(final int reuse);

  /**
   * The Default Keep-Alive strategy: 30s for the first connection, and then 5 seconds for reuse.
   */
  public static final KeepAliveStrategy DEFAULT = new DefaultKeepAliveStrategy();

  static class DefaultKeepAliveStrategy implements KeepAliveStrategy {

    @Override public int timeout(final int reuse) {
      return reuse == 0 ? 30 : 5;
    }

  }

}
