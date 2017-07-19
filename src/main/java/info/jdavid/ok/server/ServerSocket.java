package info.jdavid.ok.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;

import javax.annotation.Nullable;

class ServerSocket extends java.net.ServerSocket implements Closeable {
  ServerSocket(final int port, @Nullable final InetAddress address) throws IOException {
    super(port, -1, address);
  }

  @Override public void close() throws IOException {
    super.close();
  }
}
