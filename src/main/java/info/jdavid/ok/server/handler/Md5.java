package info.jdavid.ok.server.handler;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


class Md5 {

  private Md5() {}

  @SuppressWarnings("FieldCanBeLocal")
  private static final String MD5 = "MD5";

  static byte[] md5(final byte[] bytes) {
    try {
      return MessageDigest.getInstance(MD5).digest(bytes);
    }
    catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

}
