package info.jdavid.ok.server.handler;

import java.math.BigInteger;

import okio.ByteString;


class Hex {

  private static final String ZERO = "0";

  private Hex() {}

  static String hex(final BigInteger bigInteger) {
    final String s = bigInteger.toString(16);
    return s.length() % 2 == 0 ? s : ZERO + s;
  }

  static String hex(final byte[] bytes) {
//    final String s = new BigInteger(1, bytes).toString(16);
//    return ZEROS.substring(0, bytes.length * 2 - s.length()) + s;
    final char[] chars = new char[bytes.length * 2];
    int i = 0;
    for (final byte b: bytes) {
      chars[i++] = Character.forDigit((b >> 4) & 0xf, 16);
      chars[i++] = Character.forDigit(b & 0xf, 16);
    }
    return new String(chars);
  }

  static byte[] unhex(final String hex) {
    return ByteString.decodeHex(hex).toByteArray();
  }

}
