package info.jdavid.ok.server;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;

import okhttp3.Protocol;
import okhttp3.internal.http.StatusLine;


/**
 * List of predefined status lines for HTTP 1.1.
 */
@SuppressWarnings({ "unused", "WeakerAccess" })
public class StatusLines {
  public static final StatusLine CONTINUE = // 100
    c(StatusLine.HTTP_CONTINUE, "Continue");
  public static final StatusLine OK =
    c(HttpURLConnection.HTTP_OK, "OK"); // 200
  public static final StatusLine CREATED =
    c(HttpURLConnection.HTTP_CREATED, "Created"); // 201
  public static final StatusLine ACCEPTED =
    c(HttpURLConnection.HTTP_ACCEPTED, "Accepted"); // 202
  public static final StatusLine NO_CONTENT =
    c(HttpURLConnection.HTTP_NO_CONTENT, "No Content"); // 204
  public static final StatusLine RESET =
    c(HttpURLConnection.HTTP_RESET, "Reset Content"); // 205
  public static final StatusLine PARTIAL =
    c(HttpURLConnection.HTTP_PARTIAL, "Partial Content"); // 206
  public static final StatusLine MULTIPLE_CHOICES =
    c(HttpURLConnection.HTTP_MULT_CHOICE, "Multiple Choices"); // 300
  public static final StatusLine MOVED_PERMANENTLY =
    c(HttpURLConnection.HTTP_MOVED_PERM, "Moved Permanently"); // 301
  public static final StatusLine FOUND =
    c(HttpURLConnection.HTTP_MOVED_TEMP, "Found"); // 302
  public static final StatusLine SEE_OTHER =
    c(HttpURLConnection.HTTP_SEE_OTHER, "See Other"); // 303
  public static final StatusLine NOT_MODIFIED =
    c(HttpURLConnection.HTTP_NOT_MODIFIED, "Not Modified"); // 304
  public static final StatusLine USE_PROXY =
    c(HttpURLConnection.HTTP_USE_PROXY, "Use Proxy"); // 305
  public static final StatusLine TEMPORARY_REDIRECT =
    c(StatusLine.HTTP_TEMP_REDIRECT, "Temporary Redirect"); // 307
  public static final StatusLine PERMANENT_REDIRECT =
    c(StatusLine.HTTP_PERM_REDIRECT, "Permanent Redirect"); // 308
  public static final StatusLine BAD_REQUEST =
    c(HttpURLConnection.HTTP_BAD_REQUEST, "Bad Request"); // 400
  public static final StatusLine UNAUTHORIZED =
    c(HttpURLConnection.HTTP_UNAUTHORIZED, "Unauthorized"); // 401
  public static final StatusLine PAYMENT_REQUIRED =
    c(HttpURLConnection.HTTP_PAYMENT_REQUIRED, "Payment Required"); // 402
  public static final StatusLine FORBIDDEN =
    c(HttpURLConnection.HTTP_FORBIDDEN, "Forbidden"); // 403
  public static final StatusLine NOT_FOUND =
    c(HttpURLConnection.HTTP_NOT_FOUND, "Not Found"); // 404
  public static final StatusLine METHOD_NOT_ALLOWED =
    c(HttpURLConnection.HTTP_BAD_METHOD, "Method Not Allowed"); // 405
  public static final StatusLine NOT_ACCEPTABLE =
    c(HttpURLConnection.HTTP_NOT_ACCEPTABLE, "Not Acceptable"); // 406
  public static final StatusLine PROXY_AUTH_REQUIRED =
    c(HttpURLConnection.HTTP_PROXY_AUTH, "Proxy Authentication Required"); // 407
  public static final StatusLine REQUEST_TIMEOUT =
    c(HttpURLConnection.HTTP_CLIENT_TIMEOUT, "Request Timeout"); // 408
  public static final StatusLine CONFLICT =
    c(HttpURLConnection.HTTP_CONFLICT, "Conflict"); // 409
  public static final StatusLine GONE =
    c(HttpURLConnection.HTTP_GONE, "Gone"); // 410
  public static final StatusLine LENGTH_REQUIRED =
    c(HttpURLConnection.HTTP_LENGTH_REQUIRED, "Length Required"); // 411
  public static final StatusLine PRECONDITION_FAILED =
    c(HttpURLConnection.HTTP_PRECON_FAILED, "Precondition Failed"); // 412
  public static final StatusLine PAYLOAD_TOO_LARGE =
    c(HttpURLConnection.HTTP_ENTITY_TOO_LARGE, "Payload Too Large"); // 413
  public static final StatusLine REQUEST_URI_TOO_LONG =
    c(HttpURLConnection.HTTP_REQ_TOO_LONG, "Request-URI Too Long"); // 414
  public static final StatusLine UNSUPPORTED_MEDIA_TYPE =
    c(HttpURLConnection.HTTP_UNSUPPORTED_TYPE, "Unsupported Media Type"); // 415
  public static final StatusLine REQUEST_RANGE_NOT_SATISFIED =
    c(416, "Request Range Not Statisfied"); // 416
  public static final StatusLine EXPECTATION_FAILED =
    c(417, "Expectation Failed"); // 417
  public static final StatusLine AUTHENTICATION_TIMEOUT =
    c(419, "Authentication Timeout"); // 419
  public static final StatusLine METHOD_FAILURE =
    c(420, "Method Failure"); // 420
  public static final StatusLine MISDIRECTED_REQUEST =
    new StatusLine(Protocol.HTTP_2, 421, "Misdirected Request"); // 421
  public static final StatusLine UNPROCESSABLE_ENTITY =
    c(422, "Unprocessable Entity"); // 422
  public static final StatusLine LOCKED =
    c(423, "Locked"); // 423
  public static final StatusLine FAILED_DEPENDENCY =
    c(424, "Failed Dependency"); // 424
  public static final StatusLine UPGRADE_REQUIRED =
    c(426, "Upgrade Required"); // 426
  public static final StatusLine PRECONDITION_REQUIRED =
    c(428, "Precondition Required"); // 428
  public static final StatusLine TOO_MANY_REQUESTS =
    c(429, "Too Many Requests"); // 429
  public static final StatusLine REQUEST_HEADER_FIELDS_TOO_LARGE =
    c(431, "Request Header Fields Too Large"); // 431
  public static final StatusLine INTERNAL_SERVER_ERROR =
    c(HttpURLConnection.HTTP_INTERNAL_ERROR, "Internal Server Error"); // 500
  public static final StatusLine NOT_IMPLEMENTED =
    c(HttpURLConnection.HTTP_NOT_IMPLEMENTED, "Not Implemented"); // 501
  public static final StatusLine BAD_GATEWAY =
    c(HttpURLConnection.HTTP_BAD_GATEWAY, "Bad Gateway"); // 502
  public static final StatusLine SERVICE_UNAVAILABLE =
    c(HttpURLConnection.HTTP_UNAVAILABLE, "Service Unavailable"); // 503
  public static final StatusLine GATEWAY_TIMEOUT =
    c(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, "Gateway Timeout"); // 504
  public static final StatusLine HTTP_VERSION_NOT_SUPPORTED =
    c(HttpURLConnection.HTTP_VERSION, "HTTP Version Not Supported"); // 505
  public static final StatusLine VARIANT_ALSO_NEGOTIATES =
    c(506, "Variant Also Negotiates"); // 506
  public static final StatusLine INSUFFICIENT_STORAGE =
    c(507, "Insufficient Storage"); // 507
  public static final StatusLine LOOP_DETECTED =
    c(508, "Loop Detected"); // 508
  public static final StatusLine NOT_EXTENDED =
    c(510, "Not Extended"); // 510
  public static final StatusLine NETWORK_AUTHENTICATION_REQUIRED =
    c(511, "Network Authentication Required"); // 511
  public static final StatusLine UNKNOWN_ERROR =
    c(520, "Unknown Error"); // 520

  private static StatusLine c(final int code, final String message) {
    return new StatusLine(Protocol.HTTP_1_1, code, message);
  }

  /**
   * Get the status line for a specific code.
   * @param code the http code.
   * @return the status line.
   */
  public static StatusLine get(final int code) {
    for (final Field field: StatusLines.class.getDeclaredFields()) {
      if (field.getType() == StatusLine.class) {
        try {
          final StatusLine statusLine = (StatusLine)field.get(null);
          if (statusLine != null && statusLine.code == code) {
            return statusLine;
          }
        }
        catch (final IllegalAccessException ignore) {}
      }
    }
    return null;
  }
}
