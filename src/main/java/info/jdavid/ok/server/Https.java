package info.jdavid.ok.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import static info.jdavid.ok.server.Logger.log;

/**
 * Https is used to access the server certificates required by the server for HTTPS.
 * Certificates should be in pkcs12 format.<br>
 * If necessary, you can convert an openssl certificate with: <br>
 *   <code>openssl pkcs12 -export -in path_to_certificate.crt -inkey path_to_key.key -out path_for_generated.p12 -passout pass:</code>
 */
@SuppressWarnings("WeakerAccess")
public final class Https {

  private final SSLContext mContext;
  private final Map<String, SSLContext> mAdditionalContexts;
  private final Platform mPlatform;
  final String[] protocols;
  final String[] cipherSuites;
  final boolean http2;

  private Https(final byte[] cert, final Map<String, byte[]> additionalCerts,
                final List<String> protocols, final List<String> cipherSuites) {
    if (cert == null && additionalCerts == null && protocols == null && cipherSuites == null) {
      mContext = null;
      mAdditionalContexts = null;
      mPlatform = null;
      this.protocols = null;
      this.cipherSuites = null;
      http2 = false;
    }
    else {
      mContext = createSSLContext(cert);
      final Platform platform = mPlatform = Platform.findPlatform();
      mAdditionalContexts = new HashMap<String, SSLContext>(additionalCerts.size());
      for (final Map.Entry<String, byte[]> entry : additionalCerts.entrySet()) {
        final SSLContext additionalContext = createSSLContext(entry.getValue());
        if (additionalContext != null) mAdditionalContexts.put(entry.getKey(), additionalContext);
      }
      final List<String> protos = protocols == null ? platform.defaultProtocols() : protocols;
      this.protocols = protos.toArray(new String[protos.size()]);
      final List<String> ciphers = cipherSuites == null ? platform.defaultCipherSuites() : cipherSuites;
      this.cipherSuites = ciphers.toArray(new String[ciphers.size()]);
      this.http2 = platform.supportsHttp2();
    }
  }

  SSLContext getContext(final String host) {
    if (host == null) return mContext;
    final SSLContext additionalContext = mAdditionalContexts.get(host);
    return additionalContext == null ? mContext : additionalContext;
  }

  SSLSocket createSSLSocket(final Socket socket,
                            final String hostname, final boolean http2) throws IOException {
    final SSLSocketFactory sslFactory = getContext(hostname).getSocketFactory();
    final SSLSocket sslSocket = (SSLSocket)sslFactory.createSocket(socket, null, socket.getPort(), true);
    mPlatform.setupSSLSocket(sslSocket, http2);
    sslSocket.setUseClientMode(false);
    sslSocket.setEnabledProtocols(protocols);
    sslSocket.setEnabledCipherSuites(cipherSuites);
    sslSocket.startHandshake();
    return sslSocket;
  }

  private static SSLContext createSSLContext(final byte[] certificate) {
    if (certificate == null) return null;
    final InputStream cert = new ByteArrayInputStream(certificate);
    try {
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(cert, new char[0]);
      final KeyManagerFactory kmf =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, new char[0]);
      final KeyStore trustStore = KeyStore.getInstance("JKS");
      trustStore.load(null, null);
      final TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trustStore);
      final SSLContext context = SSLContext.getInstance("TLS");
      context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
      return context;
    }
    catch (final GeneralSecurityException e) {
      log(e);
      return null;
    }
    catch (final IOException e) {
      log(e);
      return null;
    }
    finally {
      try {
        cert.close();
      }
      catch (final IOException ignore) {}
    }
  }

  /**
   * Instance used for servers that don't use HTTPS.
   */
  public static final Https DISABLED =
    new Https(null, null, null, null);

  @SuppressWarnings({ "WeakerAccess", "unused" })
  public enum Protocol {
    SSL_3("SSLv3"), TLS_1("TLSv1"), TLS_1_1("TLSv1.1"), TLS_1_2("TLSv1.2");

    final String name;

    Protocol(final String name) {
      this.name = name;
    }

  }

  /**
   * Builder for the Https class.
   */
  @SuppressWarnings("unused")
  public static final class Builder {

    private List<String> mProtocols = null;
    private List<String> mCipherSuites = null;
    private byte[] mCertificate = null;
    private final Map<String, byte[]> mAdditionalCertificates = new HashMap<String, byte[]>(4);

    public Builder() {}

    /**
     * Sets the primary certificate.
     * @param bytes the certificate (pkcs12).
     * @return this.
     */
    public Builder certificate(final byte[] bytes) {
      if (bytes == null) throw new NullPointerException();
      if (mCertificate != null) throw new IllegalStateException("Main certificate already set.");
      mCertificate = bytes;
      return this;
    }

    /**
     * Adds an additional hostname certificate.
     * @param hostname the hostname.
     * @param bytes the certificate (pkcs12).
     * @return this.
     */
    public Builder addCertificate(final String hostname, final byte[] bytes) {
      if (hostname == null) throw new NullPointerException();
      if (bytes == null) throw new NullPointerException();
      if (mAdditionalCertificates.containsKey(hostname)) {
        throw new IllegalStateException("Certificate for host \"" + hostname + "\" has already been set.");
      }
      mAdditionalCertificates.put(hostname, bytes);
      return this;
    }

    /**
     * Sets the only allowed protocol.
     * @param protocol the protocol.
     * @return this.
     */
    public Builder protocol(final Protocol protocol) {
      mProtocols = Collections.singletonList(protocol.name);
      return this;
    }

    /**
     * Sets the list of allowed protocols.
     * @param protocols the protocols.
     * @return this.
     */
    public Builder protocols(final Protocol[] protocols) {
      final List<String> list = new ArrayList<String>(protocols.length);
      for (final Protocol protocol: protocols) {
        list.add(protocol.name);
      }
      this.mProtocols = list;
      return this;
    }

    /**
     * Sets the list of allowed cipher suites.
     * @param cipherSuites the cipher suites.
     * @return this.
     */
    public Builder cipherSuites(final String[] cipherSuites) {
      this.mCipherSuites = Arrays.asList(cipherSuites);
      return this;
    }

    /**
     * Creates the Https instance.
     * @return the Https instance.
     */
    public Https build() {
      return new Https(mCertificate, mAdditionalCertificates, mProtocols, mCipherSuites);
    }

  }

}
