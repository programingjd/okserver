package info.jdavid.ok.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

import okio.Buffer;

public class SocketWrapper extends Socket {

  final Socket delegate;
  final Buffer buffer;

  public SocketWrapper(final Socket socket, final Buffer buffer) {
    this.delegate = socket;
    this.buffer = buffer;
    assert socket.isConnected();
  }

  @Override public InputStream getInputStream() throws IOException {
    return new SequenceInputStream(buffer.inputStream(), delegate.getInputStream());
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return delegate.getOutputStream();
  }

  @Override public SocketChannel getChannel() {
    throw new UnsupportedOperationException();
  }

  @Override public void connect(final SocketAddress endpoint) {
    throw new UnsupportedOperationException();
  }

  @Override public void connect(final SocketAddress endpoint, final int timeout) {
    throw new UnsupportedOperationException();
  }

  @Override public void bind(final SocketAddress bindpoint) {
    throw new UnsupportedOperationException();
  }

  @Override public void sendUrgentData(final int data) {
    throw new UnsupportedOperationException();
  }

  @Override public void setOOBInline(final boolean on) {
    throw new UnsupportedOperationException();
  }

  @Override public boolean getOOBInline() {
    throw new UnsupportedOperationException();
  }

  @Override public void shutdownInput() {
    throw new UnsupportedOperationException();
  }

  @Override public void shutdownOutput() {
    throw new UnsupportedOperationException();
  }

  @Override public boolean isInputShutdown() {
    return delegate.isInputShutdown();
  }

  @Override public boolean isOutputShutdown() {
    return delegate.isOutputShutdown();
  }

  @Override public InetAddress getInetAddress() {
    return delegate.getInetAddress();
  }

  @Override public InetAddress getLocalAddress() {
    return delegate.getLocalAddress();
  }

  @Override public int getPort() {
    return delegate.getPort();
  }

  @Override public int getLocalPort() {
    return delegate.getLocalPort();
  }

  @Override public SocketAddress getRemoteSocketAddress() {
    return delegate.getRemoteSocketAddress();
  }

  @Override public SocketAddress getLocalSocketAddress() {
    return delegate.getLocalSocketAddress();
  }

  @Override public boolean isConnected() {
    return delegate.isConnected();
  }

  @Override public boolean isBound() {
    return delegate.isBound();
  }

  @Override public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override public synchronized void close() throws IOException {
    delegate.close();
  }

  @Override protected final void finalize() throws Throwable {
    try {
      close();
    }
    catch (IOException ignore) {}
    super.finalize();
  }

  @Override public void setTcpNoDelay(final boolean on) throws SocketException {
    delegate.setTcpNoDelay(on);
  }

  @Override public boolean getTcpNoDelay() throws SocketException {
    return delegate.getTcpNoDelay();
  }

  @Override public void setSoLinger(final boolean on, final int linger) throws SocketException {
    delegate.setSoLinger(on, linger);
  }

  @Override public int getSoLinger() throws SocketException {
    return delegate.getSoLinger();
  }

  @Override public synchronized void setSoTimeout(final int timeout) throws SocketException {
    delegate.setSoTimeout(timeout);
  }

  @Override public synchronized int getSoTimeout() throws SocketException {
    return delegate.getSoTimeout();
  }

  @Override public synchronized void setSendBufferSize(final int size) throws SocketException {
    delegate.setSendBufferSize(size);
  }

  @Override public synchronized int getSendBufferSize() throws SocketException {
    return delegate.getSendBufferSize();
  }

  @Override public synchronized void setReceiveBufferSize(final int size) throws SocketException {
    delegate.setReceiveBufferSize(size);
  }

  @Override public synchronized int getReceiveBufferSize() throws SocketException {
    return delegate.getReceiveBufferSize();
  }

  @Override public void setKeepAlive(final boolean on) throws SocketException {
    delegate.setKeepAlive(on);
  }

  @Override public boolean getKeepAlive() throws SocketException {
    return delegate.getKeepAlive();
  }

  @Override public void setTrafficClass(final int tc) throws SocketException {
    delegate.setTrafficClass(tc);
  }

  @Override public int getTrafficClass() throws SocketException {
    return delegate.getTrafficClass();
  }

  @Override public void setReuseAddress(final boolean on) throws SocketException {
    delegate.setReuseAddress(on);
  }

  @Override public boolean getReuseAddress() throws SocketException {
    return delegate.getReuseAddress();
  }

  @Override public void setPerformancePreferences(final int connectionTime,
                                                  final int latency, final int bandwidth) {
    delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
  }

}
