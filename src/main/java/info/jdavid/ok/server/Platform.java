package info.jdavid.ok.server;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import static info.jdavid.ok.server.Logger.logger;


@SuppressWarnings({ "WeakerAccess" })
abstract class Platform {

  private static final String JAVA_SPEC_VERSION;
  static {
    final String spec = Runtime.class.getPackage().getSpecificationVersion();
    if (spec == null) {
      JAVA_SPEC_VERSION = System.getProperty("java.specification.version");
    }
    else {
      JAVA_SPEC_VERSION = spec;
    }
  }

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

  private static Field findApplicationProtocolsField() {
    try {
      final Class<?> socketImplClass = Class.forName("sun.security.ssl.SSLSocketImpl");
      final Field field = socketImplClass.getDeclaredField("applicationProtocols");
      field.setAccessible(true);
      return field;
    }
    catch (final ClassNotFoundException ignore) {}
    catch (final NoSuchFieldException ignored) {}
    return null;
  }

  private static Method findApplicationProtocolsMethod() {
    try {
      return SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
    }
    catch (final NoSuchMethodException ignore) {}
    return null;
  }

  private static final String[] protocols = new String[] { "h2", "http/1.1" };

  private static void setHttp2Protocol(final SSLSocket socket, final Field field) {
    try {
      field.set(socket, protocols);
    }
    catch (final IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setHttp2Protocol(final SSLSocket socket, final Method method) {
    final SSLParameters parameters = socket.getSSLParameters();
    try {
      method.invoke(parameters, new Object[] { protocols });
      socket.setSSLParameters(parameters);
    }
    catch (final IllegalAccessException e1) {
      throw new RuntimeException(e1);
    }
    catch (final InvocationTargetException e2) {
      throw new RuntimeException(e2);
    }
  }

  static class Jdk9Platform extends Platform {

    static Platform buildIfSupported() {
      return Float.parseFloat(JAVA_SPEC_VERSION) >= 9 ? new Jdk9Platform() : null;
    }

    private final Method applicationProtocols;

    private Jdk9Platform() {
      super();
      logger.info("JDK9 Platform");
      applicationProtocols = findApplicationProtocolsMethod();
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
        "TLS_RSA_WITH_AES_128_CBC_SHA"
      );
    }

    @Override void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException {
      Platform.setHttp2Protocol(socket, applicationProtocols);
    }

    @Override boolean supportsHttp2() {
      return true;
    }

  }

  static class Jdk8Platform extends Platform {

    static Platform buildIfSupported() {
      return JAVA_SPEC_VERSION.startsWith("1.8") ? new Jdk8Platform() : null;
    }

    private final Field applicationProtocols;

    private Jdk8Platform() {
      super();
      logger.info("JDK8 Platform");
      applicationProtocols = findApplicationProtocolsField();
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
        "TLS_RSA_WITH_AES_128_CBC_SHA"
      );
    }

    @Override void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException {
      if (http2) Platform.setHttp2Protocol(socket, applicationProtocols);
    }

    @Override boolean supportsHttp2() {
      return applicationProtocols != null;
    }

  }

  static class Jdk7Platform extends Platform {

    static Platform buildIfSupported() {
      return JAVA_SPEC_VERSION.startsWith("1.7") ? new Jdk8Platform() : null;
    }

    private final Field applicationProtocols;

    private Jdk7Platform() {
      super();
      logger.info("JDK7 Platform");
      applicationProtocols = findApplicationProtocolsField();
    }

    @Override List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override List<String> defaultCipherSuites() {
      return Arrays.asList(
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA"
      );
    }

    @Override void setupSSLSocket(final SSLSocket socket, final boolean http2) throws IOException {
      if (http2) Platform.setHttp2Protocol(socket, applicationProtocols);
    }

    @Override boolean supportsHttp2() {
      return applicationProtocols != null;
    }

  }

  static class Android16Platform extends Platform {

    static Platform buildIfSupported() {
      try {
        final int version =
          Class.forName("android.os.Build$VERSION").getDeclaredField("SDK_INT").getInt(null);
        //noinspection ConstantConditions
        return version < 16 ? null : new Android16Platform(version);
      }
      catch (final Exception ignore) {
        return null;
      }
    }

    private final int version;

    private Android16Platform(final int version) {
      super();
      logger.info("Android Platform");
      this.version = version;
    }

    @Override List<String> defaultProtocols() {
      return Collections.singletonList("TLSv1.2");
    }

    @Override List<String> defaultCipherSuites() {
      return version < 20 ?
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
