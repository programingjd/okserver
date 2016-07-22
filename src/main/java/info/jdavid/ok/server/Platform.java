package info.jdavid.ok.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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

  public abstract SSLSocket createSSLSocket(final SecureSocket socket,
                                            final Https https) throws IOException;


  private static Platform findPlatform() {
    final Platform jdk9 = Jdk9Platform.buildIfSupported();
    if (jdk9 != null) return jdk9;
//    final Platform jdkJettyBoot = JdkJettyBootPlatform.buildIfSupported();
//    if (jdkJettyBoot != null) return jdkJettyBoot;
    final Platform jdk8 = Jdk8Platform.buildIfSupported();
    if (jdk8 != null) return jdk8;
    final Platform jdk7 = Jdk7Platform.buildIfSupported();
    if (jdk7 != null) return jdk7;
    final Platform android = Android16Platform.buildIfSupported();
    if (android != null) return android;
    throw new RuntimeException("Unsupported platform.");
  }

  private static SSLParameters defaultCreateSSLSocketParameters(final Https https) {
    final SSLParameters parameters = new SSLParameters();
    parameters.setProtocols(https.protocols.toArray(new String[https.protocols.size()]));
    parameters.setCipherSuites(https.cipherSuites.toArray(new String[https.cipherSuites.size()]));
    return parameters;
  }

  private static SSLSocket defaultCreateSSLSocket(final SecureSocket socket,
                                                  final Https https) throws IOException {
    if (https == null) return null;
    final SecureSocket.ReplayableInputStream inputStream = socket.getInputStream();
    final Handshake handshake = Handshake.read(inputStream);
    final String hostname = handshake == null ? null : handshake.hostname;
    inputStream.reset();
    final SSLSocketFactory sslFactory = https.getContext(hostname).getSocketFactory();
    final SSLSocket sslSocket = (SSLSocket)sslFactory.createSocket(socket, null, socket.getPort(), true);
    sslSocket.setUseClientMode(false);
    sslSocket.setSSLParameters((SSLParameters)https.parameters);
    try {
      sslSocket.startHandshake();
    }
    catch (final SSLHandshakeException e) {
      if (handshake != null)  { log(handshake.getCipherSuites()); }
      throw new IOException(e);
    }
    return sslSocket;
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
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      return Platform.defaultCreateSSLSocketParameters(https);
    }

    @Override public SSLSocket createSSLSocket(final SecureSocket socket,
                                               final Https https) throws IOException {
      return Platform.defaultCreateSSLSocket(socket, https);
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
      System.out.println("Jetty");
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
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", // NOT FOR JDK 7
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", // NOT FOR JDK 7
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256", // NOT FOR JDK 7
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      final SSLParameters parameters = new SSLParameters();
      parameters.setProtocols(https.protocols.toArray(new String[https.protocols.size()]));
      parameters.setCipherSuites(https.cipherSuites.toArray(new String[https.cipherSuites.size()]));
      return parameters;
    }

    @Override public SSLSocket createSSLSocket(final SecureSocket socket,
                                               final Https https) throws IOException {
      final SSLSocketFactory sslFactory = https.getContext(null).getSocketFactory();
      final SSLSocket sslSocket = (SSLSocket)sslFactory.createSocket(socket, null, socket.getPort(), true);
      sslSocket.setSSLParameters((SSLParameters)https.parameters);
//      final org.eclipse.jetty.alpn.ALPN.ServerProvider provider =
//        new org.eclipse.jetty.alpn.ALPN.ServerProvider() {
//        @Override public void unsupported() {
//          org.eclipse.jetty.alpn.ALPN.remove(sslSocket);
//        }
//        @Override public String select(final List<String> list) throws SSLException {
//          org.eclipse.jetty.alpn.ALPN.remove(sslSocket);
//          return list.contains("h2") ? "h2" : "http/1.1";
//        }
//      };
//      org.eclipse.jetty.alpn.ALPN.put(sslSocket, provider);
      return sslSocket;
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
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      return Platform.defaultCreateSSLSocketParameters(https);
    }

    @Override public SSLSocket createSSLSocket(final SecureSocket socket,
                                               final Https https) throws IOException {
      return Platform.defaultCreateSSLSocket(socket, https);
    }

  }

  private static class Jdk7Platform extends Platform {

    static Platform buildIfSupported() {
      return JAVA_SPEC_VERSION.startsWith("1.7") ? new Jdk8Platform() : null;
    }

    private Jdk7Platform() {
      super();
      log("JDK7 Platform");
    }

    @Override public List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override public List<String> defaultCipherSuites() {
      return Arrays.asList(
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA256"
      );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      return Platform.defaultCreateSSLSocketParameters(https);
    }

    @Override public SSLSocket createSSLSocket(final SecureSocket socket,
                                               final Https https) throws IOException {
      return Platform.defaultCreateSSLSocket(socket, https);
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
      final int version = android.os.Build.VERSION.SDK_INT;
      //noinspection ConstantConditions
      return version < 16 ? null : new Android16Platform(version);
    }

    private final int mVersion;

    private Android16Platform(final int version) {
      super();
      log("Android Platform");
      this.mVersion = version;
    }

    @Override public List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override public List<String> defaultCipherSuites() {
      return mVersion < 20 ?
             Arrays.asList(
           "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
           "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
           "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
           "TLS_RSA_WITH_AES_128_CBC_SHA"
         ) :
             Arrays.asList(
          "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
          "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
          "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
          "TLS_RSA_WITH_AES_128_GCM_SHA256",

          "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
          "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
          "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
          "TLS_RSA_WITH_AES_128_CBC_SHA"
         );
    }

    @Override public Object createSSLSocketParameters(final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override public SSLSocket createSSLSocket(final SecureSocket socket, final Https https) {
      throw new UnsupportedOperationException("Not implemented.");
    }

  }

}
