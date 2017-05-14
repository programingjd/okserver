package info.jdavid.ok.server.handler;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;

import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;


/**
 * Handler that adds digest auth to another handler.
 */
@SuppressWarnings({ "WeakerAccess" })
public class DigestAuthHandler extends AuthHandler {

  private static final String AUTHORIZATION = "Authorization";
  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

  final String name;
  final Map<String, String> credentials;
  final SecretKey key;
  final byte[] nonceIv;

  //private final MessageDigest MD5 = MessageDigest.getInstance("MD5");


  /**
   * Creates a new handler with digest auth for the specified list of username/password that delegates to the
   * specified handler. The seed should probably not change upon restarts or from server to server behind a
   * load balancer, unless you don't mind users having to re-enter their credentials.
   * @param credentials a map of username to password.
   * @param seed the seed used for random bytes generation.
   * @param delegate the delegate handler.
   */
  public DigestAuthHandler(final Map<String, String> credentials, final byte[] seed, final Handler delegate) {
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
                           final byte[] seed, final Handler delegate) {
    super(delegate);
    name = digestName;
    final byte[] bytes = new SecureRandom(seed).generateSeed(16);
    key = Crypto.secretKey(bytes);
    nonceIv = Crypto.iv(seed);
    this.credentials = credentials;
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final String auth = request.headers.get(AUTHORIZATION);
    if (auth != null && auth.startsWith("Digest ") && areCredentialsValid(request)) {
      return handleAuthenticated(request, params);
    }
    else {
      final String realm = name + "@" + request.url.host();
      final String nonce = nonce(request, key, nonceIv);
      //noinspection UnnecessaryLocalVariable
      final String opaque = opaque(request);
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
    return credentials.get(username);
  }

  /**
   * Calculates the opaque from the request object. The opaque can be used to carry state if necessary.
   * @param request the request object.
   * @return the opaque string.
   */
  protected String opaque(final Request request) {
    return new Base64Helper().encode( name+ "@" + request.url.host());
  }

  private static String nonce(final Request request, final SecretKey key, final byte[] iv) {
    final String time = Hex.hex(BigInteger.valueOf(System.currentTimeMillis()));
    final String random = Hex.hex(new SecureRandom().generateSeed(8));
    final String host = request.url.host();
    final String path = request.url.encodedPath();
    return Crypto.encrypt(key, iv, bytes(time + random + host + path));
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
    if (headerValue == null || !headerValue.startsWith("Digest ")) return false;
    final Map<String, String> map = parseHeaderValue(headerValue);
    final String username = map.get("username");
    if (username == null) return false;
    final String password = getPassword(username);
    if (password == null) return false;
    final String realm = map.get("realm");
    if (!(name + "@" + request.url.host()).equals(realm)) return false;
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
    if (!opaque(request).equals(opaque)) return false;
    final String decrypted = string(Crypto.decrypt(key, nonceIv, nonce));
    final long time = Long.parseLong(decrypted.substring(0, 12), 16);
    if ((System.currentTimeMillis() - time) > 600000) return false; // 10 mins old at the most.
    final String hostAndPath = decrypted.substring(28);
    if (!hostAndPath.equals(request.url.host() + request.url.encodedPath())) return false;

    final String ha1 = Hex.hex(md5(username + ":" + realm + ":" + password));
    final String ha2 = Hex.hex(md5(request.method + ":" + uri));

    final String expected = Hex.hex(md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2));
    return response.equals(expected);
  }

  static byte[] md5(final String name) {
    return Md5.md5(bytes(name));
  }

  private static byte[] bytes(final String s) {
    try {
      return s.getBytes(ASCII);
    }
    catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String string(final byte[] bytes) {
    try {
      return new String(bytes, ASCII);
    }
    catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private static final String ASCII = "ASCII";

}
