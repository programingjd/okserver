package info.jdavid.ok.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;


@SuppressWarnings({ "WeakerAccess" })
final class Handshake {

  final byte[] cipherSuites;
  String hostname = null;
  boolean http2 = false;

  private Handshake(final byte[] cipherSuites) {
    this.cipherSuites = cipherSuites;
  }

  String[] getCipherSuites() {
    final Buffer buffer = new Buffer();
    buffer.write(cipherSuites);
    final List<String> list = new ArrayList<String>(cipherSuites.length / 2);
    while (buffer.size() > 0) {
      final String name = CIPHER_SUITES.get(buffer.readShort());
      if (name != null) {
        list.add(name);
      }
    }
    return list.toArray(new String[list.size()]);
  }

  static final Map<Short, String> CIPHER_SUITES = createCipherSuitesMap();

  private static int int24(final byte[] bytes) {
    return ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
  }

  @SuppressWarnings("unused")
  static Handshake read(final Socket socket) throws IOException {
    final InputStream inputStream = socket.getInputStream();
    if (!inputStream.markSupported()) throw new IOException();
    inputStream.mark(4096);
    try {
      final BufferedSource source = Okio.buffer(Okio.source(inputStream));

      int size = 0;

      final byte recordType = source.readByte();
      ++size;
      if (recordType != 0x16) return null; // handshake

      final byte major = source.readByte();
      ++size;
      final byte minor = source.readByte();
      ++size;

      final short recordLength = source.readShort();
      size += 2;

      final byte hello = source.readByte();
      size += 2;
      if (hello != 0x01) return null;

      final byte[] lengthBytes = source.readByteArray(3);
      size += 3;
      final int handshakeLength = int24(lengthBytes);
      if (handshakeLength < recordLength - 4) return null; // Handshake record is longer than TLS record
      if (handshakeLength < 40) return null;

      final byte helloMajor = source.readByte();
      ++size;
      final byte helloMinor = source.readByte();
      ++size;

      final byte[] random = source.readByteArray(32);
      size += 32;

      final byte sessionIdLength = source.readByte();
      size += 1;
      final byte[] sessionId = source.readByteArray(sessionIdLength);
      size += sessionIdLength;

      final short cipherSuitesLength = source.readShort();
      size += 2;
      final byte[] cipherSuites = source.readByteArray(cipherSuitesLength);
      size += cipherSuitesLength;

      final byte compressionMethodsLength = source.readByte();
      size += 1;
      final byte[] compressionMethods = source.readByteArray(compressionMethodsLength);
      size += compressionMethodsLength;

      final Handshake handshake = new Handshake(cipherSuites);

      if (size < handshakeLength + 9) {

        final short extensionsLength = source.readShort();
        size += 2;
        int len = extensionsLength;

        while (len > 0) {

          final short extensionType = source.readShort();
          size += 2;
          final short extensionLength = source.readShort();
          size += 2;

          final byte[] extension = source.readByteArray(extensionLength);
          size += extensionLength;

          len -= extensionLength + 4;

          switch (extensionType) {

            case 0x0000: // server_name RFC6066
              if (extensionLength > 3) {
                final Buffer b = new Buffer();
                b.write(extension);
                while (b.size() > 0) {
                  b.readShort(); // list_length, ignored since list always has one element.
                  final byte nameType = b.readByte();
                  final short nameLength = b.readShort();
                  final String name = b.readUtf8(nameLength);
                  if (nameType == 0x00) { // host_name
                    handshake.hostname = name;
                    break;
                  }
                }
              }
              break;

            case 0x0001: // max_fragment_length
              break;

            case 0x0002: // client_certificate_url
              break;

            case 0x0003: // trusted_ca_keys
              break;

            case 0x0004: // truncated_hmac
              break;

            case 0x0005: // status_request
              break;

            case 0x0006: // user_mapping
              break;

            case 0x0007: // client_authz
              break;

            case 0x0008: // server_authz
              break;

            case 0x0009: // cert_type
              break;

            case 0x000a: // supported_groups (elliptic_curves)
              break;

            case 0x000b: // ec_point_formats
              break;

            case 0x000c: // srp
              break;

            case 0x000d: // signature_algorithms
              break;

            case 0x000e: // use_srtp
              break;

            case 0x000f: // heartbeat
              break;

            case 0x0010: // application_layer_protocol_negotiation (alpn)
              if (extensionLength > 3) {
                final Buffer b = new Buffer();
                b.write(extension);
                b.readShort(); // list_length, ignored
                while (b.size() > 0) {
                  final short protocolNameLength = b.readByte();
                  final String protocolName = b.readUtf8(protocolNameLength);
                  if ("h2".equals(protocolName)) handshake.http2 = true;
                }
              }
              break;

            case 0x0011: // status_request_v2
              break;

            case 0x0012: // signed_certificate_timestamp
              break;

            case 0x0013: // client_certificate_type
              break;

            case 0x0014: // server_certificate_type
              break;

            case 0x0015: // padding
              break;

            case 0x0016: // encrypt_then_mac
              break;

            case 0x0017: // extended_master_secret
              break;

            case 0x0018: // token_binding
              break;

            case 0x0019: // cached_info
              break;

            case 0x0023: // SessionTicket TLS
              break;

            case -0xff: // renegotiation_info
              break;

            default:
              break;

          }
        }
      }

      return handshake;
    }
    finally {
      inputStream.reset();
    }
  }

  static class HandshakeSocket extends Socket {

    private InputStream mBuffered = null;

    HandshakeSocket() throws IOException {
      super((SocketImpl)null);
    }

    @Override public InputStream getInputStream() throws IOException {
      return mBuffered == null ? mBuffered = new InputStream(super.getInputStream()) : mBuffered;
    }

    @Override
    public synchronized void close() throws IOException {
      mBuffered = null;
      super.close();
    }

    static class InputStream extends java.io.InputStream {

      private final java.io.InputStream mDelegate;
      private final byte mBuffer[] = new byte[4096];
      private int mBufferPosition = 0;
      private int mBufferSize = 0;
      private boolean mReset = false;

      InputStream(final java.io.InputStream inputStream) {
        super();
        this.mDelegate = inputStream;
      }

      @Override public int read() throws IOException {
        if (mReset) {
          if (mBufferPosition == mBufferSize) {
            return mDelegate.read();
          }
          else {
            return mBuffer[mBufferPosition++] & 0xff;
          }
        }
        else {
          if (mBuffer.length == mBufferSize) throw new IOException();
          final int n = mDelegate.read(mBuffer, mBufferSize, 1);
          if (n == -1) return -1;
          final byte b = mBuffer[mBufferPosition];
          mBufferPosition += n;
          mBufferSize += n;
          return b & 0xff;
        }
      }

      @Override public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
      }

      @Override public int read(final byte[] b, final int off, final int len) throws IOException {
        if (mReset) {
          if (mBufferPosition == mBufferSize) {
            return mDelegate.read(b, off, len);
          }
          else {
            final int n = Math.min(len, mBuffer.length - mBufferSize);
            System.arraycopy(mBuffer, mBufferPosition, b, off, n);
            mBufferPosition += n;
            return n;
          }
        }
        else {
          if (mBuffer.length == mBufferSize) throw new IOException();
          final int n = mDelegate.read(mBuffer, mBufferPosition, Math.min(len, mBuffer.length - mBufferSize));
          if (n == -1) return -1;
          System.arraycopy(mBuffer, mBufferPosition, b, off, n);
          mBufferPosition += n;
          mBufferSize += n;
          return n;
        }
      }

      @Override public int available() throws IOException {
        if (mReset) {
          if (mBufferPosition == mBufferSize) {
            return mDelegate.available();
          }
          else {
            return mBufferSize - mBufferPosition;
          }
        }
        else {
          return mDelegate.available();
        }
      }

      @Override public void close() throws IOException {
        mDelegate.close();
      }

      @Override public synchronized void mark(final int readlimit) {}

      @Override public synchronized void reset() throws IOException {
        if (mReset) throw new IOException();
        mBufferPosition = 0;
        mReset = true;
      }

      @Override public boolean markSupported() {
        return true;
      }

    }

  }

  static Map<Short, String> createCipherSuitesMap() {
    // http://www.iana.org/assignments/tls-parameters/tls-parameters.xml
    final Map<Short, String> map = new HashMap<Short, String>(1024);
    map.put((short)0, "TLS_NULL_WITH_NULL_NULL");
    map.put((short)1, "TLS_RSA_WITH_NULL_MD5");
    map.put((short)2, "TLS_RSA_WITH_NULL_SHA");
    map.put((short)3, "TLS_RSA_EXPORT_WITH_RC4_40_MD5");
    map.put((short)4, "TLS_RSA_WITH_RC4_128_MD5");
    map.put((short)5, "TLS_RSA_WITH_RC4_128_SHA");
    map.put((short)6, "TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5");
    map.put((short)7, "TLS_RSA_WITH_IDEA_CBC_SHA");
    map.put((short)8, "TLS_RSA_EXPORT_WITH_DES40_CBC_SHA");
    map.put((short)9, "TLS_RSA_WITH_DES_CBC_SHA");
    map.put((short)10, "TLS_RSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)11, "TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA");
    map.put((short)12, "TLS_DH_DSS_WITH_DES_CBC_SHA");
    map.put((short)13, "TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA");
    map.put((short)14, "TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA");
    map.put((short)15, "TLS_DH_RSA_WITH_DES_CBC_SHA");
    map.put((short)16, "TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)17, "TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
    map.put((short)18, "TLS_DHE_DSS_WITH_DES_CBC_SHA");
    map.put((short)19, "TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA");
    map.put((short)20, "TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA");
    map.put((short)21, "TLS_DHE_RSA_WITH_DES_CBC_SHA");
    map.put((short)22, "TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)23, "TLS_DH_anon_EXPORT_WITH_RC4_40_MD5");
    map.put((short)24, "TLS_DH_anon_WITH_RC4_128_MD5");
    map.put((short)25, "TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA");
    map.put((short)26, "TLS_DH_anon_WITH_DES_CBC_SHA");
    map.put((short)27, "TLS_DH_anon_WITH_3DES_EDE_CBC_SHA");
    map.put((short)30, "TLS_KRB5_WITH_DES_CBC_SHA");
    map.put((short)31, "TLS_KRB5_WITH_3DES_EDE_CBC_SHA");
    map.put((short)32, "TLS_KRB5_WITH_RC4_128_SHA");
    map.put((short)33, "TLS_KRB5_WITH_IDEA_CBC_SHA");
    map.put((short)34, "TLS_KRB5_WITH_DES_CBC_MD5");
    map.put((short)35, "TLS_KRB5_WITH_3DES_EDE_CBC_MD5");
    map.put((short)36, "TLS_KRB5_WITH_RC4_128_MD5");
    map.put((short)37, "TLS_KRB5_WITH_IDEA_CBC_MD5");
    map.put((short)38, "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA");
    map.put((short)39, "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA");
    map.put((short)40, "TLS_KRB5_EXPORT_WITH_RC4_40_SHA");
    map.put((short)41, "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5");
    map.put((short)42, "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5");
    map.put((short)43, "TLS_KRB5_EXPORT_WITH_RC4_40_MD5");
    map.put((short)44, "TLS_PSK_WITH_NULL_SHA");
    map.put((short)45, "TLS_DHE_PSK_WITH_NULL_SHA");
    map.put((short)46, "TLS_RSA_PSK_WITH_NULL_SHA");
    map.put((short)47, "TLS_RSA_WITH_AES_128_CBC_SHA");
    map.put((short)48, "TLS_DH_DSS_WITH_AES_128_CBC_SHA");
    map.put((short)49, "TLS_DH_RSA_WITH_AES_128_CBC_SHA");
    map.put((short)50, "TLS_DHE_DSS_WITH_AES_128_CBC_SHA");
    map.put((short)51, "TLS_DHE_RSA_WITH_AES_128_CBC_SHA");
    map.put((short)52, "TLS_DH_anon_WITH_AES_128_CBC_SHA");
    map.put((short)53, "TLS_RSA_WITH_AES_256_CBC_SHA");
    map.put((short)54, "TLS_DH_DSS_WITH_AES_256_CBC_SHA");
    map.put((short)55, "TLS_DH_RSA_WITH_AES_256_CBC_SHA");
    map.put((short)56, "TLS_DHE_DSS_WITH_AES_256_CBC_SHA");
    map.put((short)57, "TLS_DHE_RSA_WITH_AES_256_CBC_SHA");
    map.put((short)58, "TLS_DH_anon_WITH_AES_256_CBC_SHA");
    map.put((short)59, "TLS_RSA_WITH_NULL_SHA256");
    map.put((short)60, "TLS_RSA_WITH_AES_128_CBC_SHA256");
    map.put((short)61, "TLS_RSA_WITH_AES_256_CBC_SHA256");
    map.put((short)62, "TLS_DH_DSS_WITH_AES_128_CBC_SHA256");
    map.put((short)63, "TLS_DH_RSA_WITH_AES_128_CBC_SHA256");
    map.put((short)64, "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256");
    map.put((short)65, "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA");
    map.put((short)66, "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA");
    map.put((short)67, "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA");
    map.put((short)68, "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA");
    map.put((short)69, "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA");
    map.put((short)70, "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA");
    map.put((short)103, "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256");
    map.put((short)104, "TLS_DH_DSS_WITH_AES_256_CBC_SHA256");
    map.put((short)105, "TLS_DH_RSA_WITH_AES_256_CBC_SHA256");
    map.put((short)106, "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256");
    map.put((short)107, "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256");
    map.put((short)108, "TLS_DH_anon_WITH_AES_128_CBC_SHA256");
    map.put((short)109, "TLS_DH_anon_WITH_AES_256_CBC_SHA256");
    map.put((short)132, "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA");
    map.put((short)133, "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA");
    map.put((short)134, "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA");
    map.put((short)135, "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA");
    map.put((short)136, "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA");
    map.put((short)137, "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA");
    map.put((short)138, "TLS_PSK_WITH_RC4_128_SHA");
    map.put((short)139, "TLS_PSK_WITH_3DES_EDE_CBC_SHA");
    map.put((short)140, "TLS_PSK_WITH_AES_128_CBC_SHA");
    map.put((short)141, "TLS_PSK_WITH_AES_256_CBC_SHA");
    map.put((short)142, "TLS_DHE_PSK_WITH_RC4_128_SHA");
    map.put((short)143, "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA");
    map.put((short)144, "TLS_DHE_PSK_WITH_AES_128_CBC_SHA");
    map.put((short)145, "TLS_DHE_PSK_WITH_AES_256_CBC_SHA");
    map.put((short)146, "TLS_RSA_PSK_WITH_RC4_128_SHA");
    map.put((short)147, "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA");
    map.put((short)148, "TLS_RSA_PSK_WITH_AES_128_CBC_SHA");
    map.put((short)149, "TLS_RSA_PSK_WITH_AES_256_CBC_SHA");
    map.put((short)150, "TLS_RSA_WITH_SEED_CBC_SHA");
    map.put((short)151, "TLS_DH_DSS_WITH_SEED_CBC_SHA");
    map.put((short)152, "TLS_DH_RSA_WITH_SEED_CBC_SHA");
    map.put((short)153, "TLS_DHE_DSS_WITH_SEED_CBC_SHA");
    map.put((short)154, "TLS_DHE_RSA_WITH_SEED_CBC_SHA");
    map.put((short)155, "TLS_DH_anon_WITH_SEED_CBC_SHA");
    map.put((short)156, "TLS_RSA_WITH_AES_128_GCM_SHA256");
    map.put((short)157, "TLS_RSA_WITH_AES_256_GCM_SHA384");
    map.put((short)158, "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256");
    map.put((short)159, "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384");
    map.put((short)160, "TLS_DH_RSA_WITH_AES_128_GCM_SHA256");
    map.put((short)161, "TLS_DH_RSA_WITH_AES_256_GCM_SHA384");
    map.put((short)162, "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256");
    map.put((short)163, "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384");
    map.put((short)164, "TLS_DH_DSS_WITH_AES_128_GCM_SHA256");
    map.put((short)165, "TLS_DH_DSS_WITH_AES_256_GCM_SHA384");
    map.put((short)166, "TLS_DH_anon_WITH_AES_128_GCM_SHA256");
    map.put((short)167, "TLS_DH_anon_WITH_AES_256_GCM_SHA384");
    map.put((short)168, "TLS_PSK_WITH_AES_128_GCM_SHA256");
    map.put((short)169, "TLS_PSK_WITH_AES_256_GCM_SHA384");
    map.put((short)170, "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256");
    map.put((short)171, "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384");
    map.put((short)172, "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256");
    map.put((short)173, "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384");
    map.put((short)174, "TLS_PSK_WITH_AES_128_CBC_SHA256");
    map.put((short)175, "TLS_PSK_WITH_AES_256_CBC_SHA384");
    map.put((short)176, "TLS_PSK_WITH_NULL_SHA256");
    map.put((short)177, "TLS_PSK_WITH_NULL_SHA384");
    map.put((short)178, "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256");
    map.put((short)179, "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384");
    map.put((short)180, "TLS_DHE_PSK_WITH_NULL_SHA256");
    map.put((short)181, "TLS_DHE_PSK_WITH_NULL_SHA384");
    map.put((short)182, "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256");
    map.put((short)183, "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384");
    map.put((short)184, "TLS_RSA_PSK_WITH_NULL_SHA256");
    map.put((short)185, "TLS_RSA_PSK_WITH_NULL_SHA384");
    map.put((short)186, "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)187, "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)188, "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)189, "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)190, "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)191, "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)192, "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256");
    map.put((short)193, "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256");
    map.put((short)194, "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256");
    map.put((short)195, "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256");
    map.put((short)196, "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256");
    map.put((short)197, "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256");
    map.put((short)255, "TLS_EMPTY_RENEGOTIATION_INFO_SCSV");
    map.put((short)22016, "TLS_FALLBACK_SCSV");
    map.put((short)-16383, "TLS_ECDH_ECDSA_WITH_NULL_SHA");
    map.put((short)-16382, "TLS_ECDH_ECDSA_WITH_RC4_128_SHA");
    map.put((short)-16381, "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16380, "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA");
    map.put((short)-16379, "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA");
    map.put((short)-16378, "TLS_ECDHE_ECDSA_WITH_NULL_SHA");
    map.put((short)-16377, "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA");
    map.put((short)-16376, "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16375, "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA");
    map.put((short)-16374, "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA");
    map.put((short)-16373, "TLS_ECDH_RSA_WITH_NULL_SHA");
    map.put((short)-16372, "TLS_ECDH_RSA_WITH_RC4_128_SHA");
    map.put((short)-16371, "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16370, "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA");
    map.put((short)-16369, "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA");
    map.put((short)-16368, "TLS_ECDHE_RSA_WITH_NULL_SHA");
    map.put((short)-16367, "TLS_ECDHE_RSA_WITH_RC4_128_SHA");
    map.put((short)-16366, "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16365, "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA");
    map.put((short)-16364, "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA");
    map.put((short)-16363, "TLS_ECDH_anon_WITH_NULL_SHA");
    map.put((short)-16362, "TLS_ECDH_anon_WITH_RC4_128_SHA");
    map.put((short)-16361, "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16360, "TLS_ECDH_anon_WITH_AES_128_CBC_SHA");
    map.put((short)-16359, "TLS_ECDH_anon_WITH_AES_256_CBC_SHA");
    map.put((short)-16358, "TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16357, "TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16356, "TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16355, "TLS_SRP_SHA_WITH_AES_128_CBC_SHA");
    map.put((short)-16354, "TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA");
    map.put((short)-16353, "TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA");
    map.put((short)-16352, "TLS_SRP_SHA_WITH_AES_256_CBC_SHA");
    map.put((short)-16351, "TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA");
    map.put((short)-16350, "TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA");
    map.put((short)-16349, "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256");
    map.put((short)-16348, "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384");
    map.put((short)-16347, "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256");
    map.put((short)-16346, "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384");
    map.put((short)-16345, "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256");
    map.put((short)-16344, "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384");
    map.put((short)-16343, "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256");
    map.put((short)-16342, "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384");
    map.put((short)-16341, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");
    map.put((short)-16340, "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384");
    map.put((short)-16339, "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256");
    map.put((short)-16338, "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384");
    map.put((short)-16337, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    map.put((short)-16336, "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
    map.put((short)-16335, "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256");
    map.put((short)-16334, "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384");
    map.put((short)-16333, "TLS_ECDHE_PSK_WITH_RC4_128_SHA");
    map.put((short)-16332, "TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA");
    map.put((short)-16331, "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA");
    map.put((short)-16330, "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA");
    map.put((short)-16329, "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256");
    map.put((short)-16328, "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384");
    map.put((short)-16327, "TLS_ECDHE_PSK_WITH_NULL_SHA");
    map.put((short)-16326, "TLS_ECDHE_PSK_WITH_NULL_SHA256");
    map.put((short)-16325, "TLS_ECDHE_PSK_WITH_NULL_SHA384");
    map.put((short)-16324, "TLS_RSA_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16323, "TLS_RSA_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16322, "TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16321, "TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16320, "TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16319, "TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16318, "TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16317, "TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16316, "TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16315, "TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16314, "TLS_DH_anon_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16313, "TLS_DH_anon_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16312, "TLS_ECDHE_ECDSA_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16311, "TLS_ECDHE_ECDSA_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16310, "TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16309, "TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16308, "TLS_ECDHE_RSA_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16307, "TLS_ECDHE_RSA_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16306, "TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16305, "TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16304, "TLS_RSA_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16303, "TLS_RSA_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16302, "TLS_DHE_RSA_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16301, "TLS_DHE_RSA_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16300, "TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16299, "TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16298, "TLS_DHE_DSS_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16297, "TLS_DHE_DSS_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16296, "TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16295, "TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16294, "TLS_DH_anon_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16293, "TLS_DH_anon_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16292, "TLS_ECDHE_ECDSA_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16291, "TLS_ECDHE_ECDSA_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16290, "TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16289, "TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16288, "TLS_ECDHE_RSA_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16287, "TLS_ECDHE_RSA_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16286, "TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16285, "TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16284, "TLS_PSK_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16283, "TLS_PSK_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16282, "TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16281, "TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16280, "TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16279, "TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16278, "TLS_PSK_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16277, "TLS_PSK_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16276, "TLS_DHE_PSK_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16275, "TLS_DHE_PSK_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16274, "TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256");
    map.put((short)-16273, "TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384");
    map.put((short)-16272, "TLS_ECDHE_PSK_WITH_ARIA_128_CBC_SHA256");
    map.put((short)-16271, "TLS_ECDHE_PSK_WITH_ARIA_256_CBC_SHA384");
    map.put((short)-16270, "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16269, "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16268, "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16267, "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16266, "TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16265, "TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16264, "TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16263, "TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16262, "TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16261, "TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16260, "TLS_DHE_RSA_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16259, "TLS_DHE_RSA_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16258, "TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16257, "TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16256, "TLS_DHE_DSS_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16255, "TLS_DHE_DSS_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16254, "TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16253, "TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16252, "TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16251, "TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16250, "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16249, "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16248, "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16247, "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16246, "TLS_ECDHE_RSA_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16245, "TLS_ECDHE_RSA_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16244, "TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16243, "TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16242, "TLS_PSK_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16241, "TLS_PSK_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16240, "TLS_DHE_PSK_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16239, "TLS_DHE_PSK_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16238, "TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256");
    map.put((short)-16237, "TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384");
    map.put((short)-16236, "TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16235, "TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16234, "TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16233, "TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16232, "TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16231, "TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16230, "TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256");
    map.put((short)-16229, "TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384");
    map.put((short)-16228, "TLS_RSA_WITH_AES_128_CCM");
    map.put((short)-16227, "TLS_RSA_WITH_AES_256_CCM");
    map.put((short)-16226, "TLS_DHE_RSA_WITH_AES_128_CCM");
    map.put((short)-16225, "TLS_DHE_RSA_WITH_AES_256_CCM");
    map.put((short)-16224, "TLS_RSA_WITH_AES_128_CCM_8");
    map.put((short)-16223, "TLS_RSA_WITH_AES_256_CCM_8");
    map.put((short)-16222, "TLS_DHE_RSA_WITH_AES_128_CCM_8");
    map.put((short)-16221, "TLS_DHE_RSA_WITH_AES_256_CCM_8");
    map.put((short)-16220, "TLS_PSK_WITH_AES_128_CCM");
    map.put((short)-16219, "TLS_PSK_WITH_AES_256_CCM");
    map.put((short)-16218, "TLS_DHE_PSK_WITH_AES_128_CCM");
    map.put((short)-16217, "TLS_DHE_PSK_WITH_AES_256_CCM");
    map.put((short)-16216, "TLS_PSK_WITH_AES_128_CCM_8");
    map.put((short)-16215, "TLS_PSK_WITH_AES_256_CCM_8");
    map.put((short)-16214, "TLS_PSK_DHE_WITH_AES_128_CCM_8");
    map.put((short)-16213, "TLS_PSK_DHE_WITH_AES_256_CCM_8");
    map.put((short)-16212, "TLS_ECDHE_ECDSA_WITH_AES_128_CCM");
    map.put((short)-16211, "TLS_ECDHE_ECDSA_WITH_AES_256_CCM");
    map.put((short)-16210, "TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8");
    map.put((short)-16209, "TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8");
    map.put((short)-13144, "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256");
    map.put((short)-13143, "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256");
    map.put((short)-13142, "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256");
    map.put((short)-13141, "TLS_PSK_WITH_CHACHA20_POLY1305_SHA256");
    map.put((short)-13140, "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256");
    map.put((short)-13139, "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256");
    map.put((short)-13138, "TLS_RSA_PSK_WITH_CHACHA20_POLY1305_SHA256");
    return map;
  }

}
