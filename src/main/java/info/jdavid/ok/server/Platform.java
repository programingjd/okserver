package info.jdavid.ok.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLSocket;

import static info.jdavid.ok.server.Logger.log;

abstract class Platform {

  private static final String JAVA_SPEC_VERSION = Runtime.class.getPackage().getSpecificationVersion();

  abstract List<String> defaultProtocols();

  abstract List<String> defaultCipherSuites();

  abstract boolean supportsHttp2();

  abstract void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException;

  static Platform findPlatform() {
    final Platform jdk9 = Jdk9Platform.buildIfSupported();
    if (jdk9 != null) return jdk9;
    final Platform jdk8 = Jdk8Platform.buildIfSupported();
    if (jdk8 != null) return jdk8;
    final Platform jdk7 = Jdk7Platform.buildIfSupported();
    if (jdk7 != null) return jdk7;
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

    @Override List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override List<String> defaultCipherSuites() {
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

    @Override void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException {}

    @Override boolean supportsHttp2() {
      return false;
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

    @Override List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override List<String> defaultCipherSuites() {
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

    @Override void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException {}

    @Override boolean supportsHttp2() {
      return false;
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

    @Override List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override List<String> defaultCipherSuites() {
      return Arrays.asList(
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA256"
      );
    }

    @Override void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException {}

    @Override boolean supportsHttp2() {
      return false;
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
      mVersion = version;
    }

    @Override List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override List<String> defaultCipherSuites() {
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

    @Override void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException {}

    @Override boolean supportsHttp2() {
      return false;
    }

  }

}
