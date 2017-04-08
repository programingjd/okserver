package info.jdavid.ok.server.handler;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okio.ByteString;


/**
 * Handler that adds digest auth to another handler.
 */
public class DigestAuthHandler extends AuthHandler {

  private static final String AUTHORIZATION = "Authorization";
  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

  private static String AES = "AES";
  private static String AES_CBC = "AES/CBC/PKCS5Padding";
  private static String MD5 = "MD5";

  static {
    try {
      MessageDigest.getInstance(MD5);
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

  private final String mName;
  private final Map<String, String> mCredentials;
  private final SecretKey mKey;
  private final byte[] mNonceIv;
  private final String mOpaque;

  //private final MessageDigest MD5 = MessageDigest.getInstance("MD5");


  /**
   * Creates a new handler with digest auth for the specified list of username/password that delegates to the
   * specified handler. The seed should probably not change upon restarts or from server to server behind a
   * load balancer, unless you don't mind users having to re-enter their credentials.
   * @param credentials a map of username to password.
   * @param seed the seed used for random bytes generation.
   * @param delegate the delegate handler.
   */
  public DigestAuthHandler(final Map<String, String> credentials,
                           final byte[] seed, final Handler delegate) {
    this(credentials, "digest", seed, delegate);
  }

  /**
   * Creates a new handler with digest auth for the specified list of username/password that delegates to the
   * specified handler. The seed should probably not change upon restarts or from server to server behind a
   * load balancer, unless you don't mind users having to re-enter their credentials.
   * @param credentials a map of username to password.
   * @param digestName the name of the realm.
   * @param seed the seed used for random bytes generation.
   * @param delegate the delegate handler.
   */
  public DigestAuthHandler(final Map<String, String> credentials, final String digestName,
                           final byte[] seed,
                           final Handler delegate) {
    super(delegate);
    mName = digestName;
    final SecureRandom secureRandom = new SecureRandom(seed);
    final byte[] bytes = new byte[16];
    secureRandom.nextBytes(bytes);
    mKey = secretKey(bytes);
    secureRandom.nextBytes(bytes);
    mOpaque = opaque(mName, mKey, iv(bytes));
    secureRandom.nextBytes(bytes);
    mNonceIv = iv(bytes);
    mCredentials = credentials == null ? Collections.<String, String>emptyMap() : credentials;
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final String auth = request.headers.get(AUTHORIZATION);
    if (auth != null && auth.startsWith("Digest ") && areCredentialsValid(request)) {
      return handleAuthenticated(request, params);
    }
    else {
      final String realm = mName + "@" + request.url.host();
      final String nonce = nonce(request, mKey, mNonceIv);
      //noinspection UnnecessaryLocalVariable
      final String opaque = mOpaque;
      final String digest =
        "Digest " +
        "realm=\"" + realm + "\", " +
        "qop=\"auth\", " +
        "algorithm=MD5, " +
        "nonce=\"" + nonce + "\", " +
        "opaque=\"" + opaque + "\"";
      return new Response.Builder().
        statusLine(StatusLines.UNAUTHORIZED).
        addHeader(WWW_AUTHENTICATE, digest).
        noBody();
    }
  }

  /**
   * Gets the password for the specified username.
   * @param username the username.
   * @return the password or null if the username doesn't exist.
   */
  protected String getPassword(final String username) {
    return mCredentials.get(username);
  }

  private static String nonce(final Request request, final SecretKey key, final byte[] iv) {
    final String time = hex(BigInteger.valueOf(System.currentTimeMillis()));
    final String random = hex(new SecureRandom().generateSeed(8));
    final String host = request.url.host();
    final String path = request.url.encodedPath();
    return encrypt(key, iv, bytes(time + random + host + path));
  }

  private static String opaque(final String name, final Key key, final byte[] iv) {
    return encrypt(key, md5(name), iv);
  }

  private static final Pattern AUTHORIZATION_VALUE_REGEX =
    Pattern.compile("([^=]+)=(?:\"([^\"]*)\"|([0-9a-f]{8})|(auth)|(MD5)),?\\s?");

  static Map<String, String> parseHeaderValue(final String headerValue) {
    final Matcher matcher =
      AUTHORIZATION_VALUE_REGEX.matcher(headerValue.substring(headerValue.indexOf(' ') + 1));
    final Map<String, String> map = new HashMap<String, String>(12);
    while (matcher.find()) {
      final String group1 = matcher.group(1);
      final String group2 = matcher.group(2);
      final String group3 = matcher.group(3);
      final String group4 = matcher.group(4);
      final String group5 = matcher.group(5);
      if (group2 != null) {
        map.put(group1, group2);
      }
      else if (group3 != null) {
        map.put(group1, group3);
      }
      else if (group4 != null) {
        map.put(group1, group4);
      }
      else if (group5 != null) {
        map.put(group1, group5);
      }
    }
    return map;
  }

  private boolean areCredentialsValid(final Request request) {
    final String headerValue = request.headers.get(AUTHORIZATION);
    if (!headerValue.startsWith("Digest ")) return false;
    final Map<String, String> map = parseHeaderValue(headerValue);
    final String username = map.get("username");
    if (username == null) return false;
    final String password = getPassword(username);
    if (password == null) return false;
    final String realm = map.get("realm");
    if (!(mName + "@" + request.url.host()).equals(realm)) return false;
    final String nonce = map.get("nonce");
    if (nonce == null) return false;
    final String uri = map.get("uri");
    if (!uri.equals(request.url.encodedPath())) return false;
    final String qop = map.get("qop");
    if (!qop.equals("auth")) return false;
    final String nc = map.get("nc");
    if (nc == null) return false;
    final String cnonce = map.get("cnonce");
    if (cnonce == null) return false;
    final String response = map.get("response");
    if (response == null) return false;
    final String opaque = map.get("opaque");
    if (!mOpaque.equals(opaque)) return false;
    final String decrypted = string(decrypt(mKey, mNonceIv, nonce));
    final long time = Long.parseLong(decrypted.substring(0, 12), 16);
    if ((System.currentTimeMillis() - time) > 600000) return false; // 10 mins old at the most.
    final String hostAndPath = decrypted.substring(28);
    if (!hostAndPath.equals(request.url.host() + request.url.encodedPath())) return false;

    final String ha1 = hex(md5(username + ":" + realm + ":" + password));
    final String ha2 = hex(md5(request.method + ":" + uri));

    final String expected = hex(md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2));
    return response.equals(expected);
  }

  private static byte[] md5(final String name) {
    return digest(MD5).digest(bytes(name));
  }

  private static final String ZERO = "0";
  private static final String ZEROS = "0000000000000000";

  private static String hex(final BigInteger bigInteger) {
    final String s = bigInteger.toString(16);
    return s.length() % 2 == 0 ? s : ZERO + s;
  }

  private static String hex(final byte[] bytes) {
    final String s = new BigInteger(1, bytes).toString(16);
    return ZEROS.substring(0, bytes.length * 2 - s.length()) + s;
  }

  private static byte[] unhex(final String hex) {
    return ByteString.decodeHex(hex).toByteArray();
  }

  private static String encrypt(final Key key, final byte[] iv, final byte[] bytes) {
    try {
      final Cipher cipher = cipher(AES_CBC);
      cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
      final byte[] encrypted = cipher.doFinal(bytes);
      return hex(encrypted);
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] decrypt(final Key key, final byte[] iv, final String crypted) {
    final byte[] bytes = unhex(crypted);
    try {
      final Cipher cipher = cipher(AES_CBC);
      cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
      return cipher.doFinal(bytes);
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] iv(final byte[] seed) {
    final byte[] bytes = new byte[16];
    new SecureRandom(seed).nextBytes(bytes);
    return bytes;
  }

  private static SecretKey secretKey(final byte[] seed) {
    try {
      final KeyGenerator keygen = KeyGenerator.getInstance(AES);
      keygen.init(new SecureRandom(seed));
      return keygen.generateKey();
    }
    catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static Cipher cipher(final String algorithm) {
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

  private static MessageDigest digest(final String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    }
    catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] bytes(final String s) {
    try {
      return s.getBytes("ASCII");
    }
    catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String string(final byte[] bytes) {
    try {
      return new String(bytes, "ASCII");
    }
    catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

}
