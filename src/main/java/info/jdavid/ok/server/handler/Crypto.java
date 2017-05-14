package info.jdavid.ok.server.handler;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

class Crypto {

  private Crypto() {}


  private static final String AES = "AES";
  private static final String AES_CBC = "AES/CBC/PKCS5Padding";

  static {
    try {
      KeyGenerator.getInstance(AES);
      Cipher.getInstance(AES_CBC);
    }
    catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    catch (final NoSuchPaddingException e) {
      throw new RuntimeException(e);
    }
  }

  static String encrypt(final Key key, final byte[] iv, final byte[] bytes) {
    try {
      final Cipher cipher = cipher(AES_CBC);
      cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
      final byte[] encrypted = cipher.doFinal(bytes);
      return Hex.hex(encrypted);
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  static byte[] decrypt(final Key key, final byte[] iv, final String crypted) {
    final byte[] bytes = Hex.unhex(crypted);
    try {
      final Cipher cipher = cipher(AES_CBC);
      cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
      return cipher.doFinal(bytes);
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  static byte[] iv(final byte[] seed /*, final boolean secure*/) {
    final byte[] bytes = new byte[16];
//    if (secure) {
    new SecureRandom(seed).nextBytes(bytes);
//    }
//    else {
//      final long l = seed == null ? System.currentTimeMillis() :
//                                    new BigInteger(md5(seed).getBytes()).longValue();
//      new Random(l).nextBytes(bytes);
//    }
    return bytes;
  }

  static SecretKey secretKey(final byte[] iv) {
    try {
      final KeyGenerator keygen = KeyGenerator.getInstance(AES);
      keygen.init(new SecureRandom(iv));
      return keygen.generateKey();
    }
    catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  static Cipher cipher(final String algorithm) {
    try {
      return Cipher.getInstance(algorithm);
    }
    catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    catch (final NoSuchPaddingException e) {
      throw new RuntimeException(e);
    }
  }

}
