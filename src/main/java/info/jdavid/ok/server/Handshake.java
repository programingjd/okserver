package info.jdavid.ok.server;

import java.io.IOException;
import java.net.Socket;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

import static info.jdavid.ok.server.Logger.log;

final class Handshake {

  final String host;
  final Buffer buffer;

  private Handshake(final Buffer buffer, final String host) {
    this.buffer = buffer;
    this.host = host;
  }

  private static int int24(final byte[] bytes) {
    return ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
  }

  static Handshake read(final Socket socket) throws IOException {
    final BufferedSource source = Okio.buffer(Okio.source(socket));
    final Buffer buffer = new Buffer();

    final byte handshake = source.readByte();
    buffer.writeByte(handshake);
    if (handshake != 0x16) return new Handshake(buffer, null);

    final byte major = source.readByte();
    final byte minor = source.readByte();
    buffer.writeByte(major);
    buffer.writeByte(minor);

    final short recordLength = source.readShort();
    buffer.writeShort(recordLength);


    final byte hello = source.readByte();
    buffer.writeByte(hello);
    if (hello != 0x01) return new Handshake(buffer, null);

    final byte[] lengthBytes = source.readByteArray(3);
    buffer.write(lengthBytes);
    final int handshakeLength = int24(lengthBytes);
    if (handshakeLength < recordLength - 4) {
      log("Handshake record is longer than TLS record.");
      return new Handshake(buffer, null);
    }
    else if (handshakeLength < 40) return new Handshake(buffer, null);

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
        if (extensionType == 0x0000) { // server_name RFC6066
          if (extensionLength > 3) {
            final Buffer b = new Buffer();
            b.write(extension);
            while (b.size() > 0) {
              b.readShort(); // list_length, ignore since list always has one element.
              final int nameType = b.readShort();
              final short nameLength = b.readShort();
              final String name = b.readUtf8(nameLength);
              if (nameType == 0x00) { // host_name
                return new Handshake(buffer, name);
              }
            }
          }
        }
      }
    }
    return new Handshake(buffer, null);
  }

}
