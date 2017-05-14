package info.jdavid.ok.server.handler;

import javax.annotation.Nullable;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;


/**
 * Request object that holds the information about the request, as well as the request body.
 */
public final class Request {

  /**
   * The client ip.
   */
  public final String clientIp;
  /**
   * true when the request is being served with the HTTP 2 protocol rather than HTTP 1.1.
   */
  public final boolean http2;
  /**
   * The request method.
   */
  public final String method;
  /**
   * The request url.
   */
  public final HttpUrl url;
  /**
   * The request headers.
   */
  public final Headers headers;
  /**
   * The request body.
   */
  public final Buffer body;

  public Request(final String clientIp, final boolean http2,
                 final String method, final HttpUrl url,
                 final Headers headers, @Nullable final Buffer body) {
    this.clientIp = clientIp;
    this.method = method;
    this.http2 = http2;
    this.url = url;
    this.headers = headers;
    this.body = body;
  }

}
