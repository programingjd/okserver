package info.jdavid.ok.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

import static info.jdavid.ok.server.Logger.log;

abstract class Platform {

  private static final String JAVA_SPEC_VERSION = Runtime.class.getPackage().getSpecificationVersion();
  private static final Platform PLATFORM = findPlatform();

  public static Platform get() {
    return PLATFORM;
  }

  public abstract List<String> defaultProtocols();

  public abstract List<String> defaultCipherSuites();

  public abstract Object createSSLSocketParameters(final Https https);

  public abstract SSLSocket createSSLSocket(final Socket socket, final Https https) throws IOException;


  private static Platform findPlatform() {
    final Platform jdk9 = Jdk9Platform.buildIfSupported();
    if (jdk9 != null) return jdk9;
    final Platform jdkJettyBoot = JdkJettyBootPlatform.buildIfSupported();
    if (jdkJettyBoot != null) return jdkJettyBoot;
    final Platform jdk8 = Jdk8Platform.buildIfSupported();
    if (jdk8 != null) return jdk8;
    final Platform android = Android16Platform.buildIfSupported();
    if (android != null) return android;
    throw new RuntimeException("Unsupported platform.");
  }

  private static class Jdk9Platform extends Platform {

    static Platform buildIfSupported() {
      return JAVA_SPEC_VERSION.startsWith("1.9") ? new Jdk9Platform() : null;
    }

    private Jdk9Platform() {
      super();
      log("JDK9 Platform");
    }

    @Override public List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override public List<String> defaultCipherSuites() {
      return Arrays.asList(
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override public SSLSocket createSSLSocket(final Socket socket, final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

  }

  private static class JdkJettyBootPlatform extends Platform {

    static Platform buildIfSupported() {
      try {
        Class.forName("org.eclipse.jetty.alpn.ALPN");
      }
      catch (final ClassNotFoundException ignore) {
        return null;
      }
      return new JdkJettyBootPlatform();
    }

    private JdkJettyBootPlatform() {
      super();
      log("Jetty Boot Platform");
    }

    @Override public List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override public List<String> defaultCipherSuites() {
      return Arrays.asList(
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override public SSLSocket createSSLSocket(final Socket socket, final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

  }

  private static class Jdk8Platform extends Platform {

    static Platform buildIfSupported() {
      return JAVA_SPEC_VERSION.startsWith("1.8") ? new Jdk8Platform() : null;
    }

    private Jdk8Platform() {
      super();
      log("JDK8 Platform");
    }

    @Override public List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override public List<String> defaultCipherSuites() {
      return Arrays.asList(
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      final SSLParameters parameters = new SSLParameters();
      parameters.setProtocols((String[])https.protocols.toArray());
      parameters.setCipherSuites((String[])https.cipherSuites.toArray());
      return parameters;
    }

    @Override public SSLSocket createSSLSocket(final Socket socket, final Https https) throws IOException {
      if (https == null) return null;
      final BufferedSource source = Okio.buffer(Okio.source(socket));
      final Buffer buffer = new Buffer();
      final byte handshake = source.readByte();
      buffer.writeByte(handshake);
      assertThat(handshake == 0x16,
                 "Invalid TLS record type: " + handshake + " (expected 22 for 'handshake').");
      final byte major = source.readByte();
      final byte minor = source.readByte();
      buffer.writeByte(major);
      buffer.writeByte(minor);
      final short recordLength = source.readShort();
      buffer.writeShort(recordLength);
      final byte hello = source.readByte();
      buffer.writeByte(hello);
      assertThat(hello == 0x01, "Invalid handshake type: " + hello + " (expected 1 for 'client_hello').");
      final byte[] lengthBytes = source.readByteArray(3);
      buffer.write(lengthBytes);
      final int handshakeLength = int24(lengthBytes);
      assertThat(handshakeLength <= recordLength - 4, "Handshake record is longer than TLS record.");
      final byte helloMajor = source.readByte();
      final byte helloMinor = source.readByte();
      buffer.writeByte(helloMajor);
      buffer.writeByte(helloMinor);
      final byte[] random = source.readByteArray(32);
      buffer.write(random);
      final byte sessionIdLength = source.readByte();
      buffer.writeByte(sessionIdLength);
      final byte[] sessionId = source.readByteArray(sessionIdLength);
      buffer.write(sessionId);
      final short cipherSuiteLength = source.readShort();
      buffer.writeShort(cipherSuiteLength);
      final byte[] cipherSuite = source.readByteArray(cipherSuiteLength);
      buffer.write(cipherSuite);
      final byte compressionMethodsLength = source.readByte();
      buffer.writeByte(compressionMethodsLength);
      final byte[] compressionMethods = source.readByteArray(compressionMethodsLength);
      buffer.write(compressionMethods);
      if (buffer.size() < handshakeLength + 9) {
        final short extensionsLength = source.readShort();
        buffer.writeShort(extensionsLength);
        int len = extensionsLength;
        while (len > 0) {
          final short extensionType = source.readShort();
          buffer.writeShort(extensionType);
          final short extensionLength = source.readShort();
          buffer.writeShort(extensionLength);
          final byte[] extension = source.readByteArray(extensionLength);
          buffer.write(extension);
          len -= extensionLength + 4;
        }
      }
      final ByteArrayInputStream consumed = new ByteArrayInputStream(buffer.readByteArray());
      final SSLSocketFactory sslFactory = https.context.getSocketFactory();
      final SSLSocket sslSocket = (SSLSocket)sslFactory.createSocket(socket, consumed, true);
      sslSocket.setSSLParameters((SSLParameters)https.parameters);
      return sslSocket;
    }

    private static int int24(final byte[] bytes) {
      return ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
    }

  }

  private static class Android16Platform extends Platform {

    static Platform buildIfSupported() {
      try {
        Class.forName("android.os.Build");
      }
      catch (final ClassNotFoundException ignore) {
        return null;
      }
      //noinspection ConstantConditions
      return android.os.Build.VERSION.SDK_INT < 16 ? null : new Android16Platform();
    }

    private Android16Platform() {
      super();
      log("Android Platform");
    }

    @Override public List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override public List<String> defaultCipherSuites() {
      return Arrays.asList(
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override public SSLSocket createSSLSocket(final Socket socket, final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

  }

  private static void assertThat(final boolean assertion, final String message) {
    if (!assertion) throw new RuntimeException(message);
  }

}
