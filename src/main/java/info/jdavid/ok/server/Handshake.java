package info.jdavid.ok.server;

import java.io.IOException;
import java.net.Socket;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

final class Handshake {

  public final byte[] bytes;

  private Handshake(final Buffer buffer) {
    this.bytes = buffer.readByteArray();
  }


  private static void assertThat(final boolean assertion, final String message) {
    if (!assertion) throw new RuntimeException(message);
  }

  private static int int24(final byte[] bytes) {
    return ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
  }

  public static Handshake read(final Socket socket) throws IOException {
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
    return new Handshake(buffer);
  }



}
