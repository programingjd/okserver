package info.jdavid.ok.server.header;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import info.jdavid.ok.server.Response;
import okhttp3.Headers;
import okhttp3.HttpUrl;


/**
 * Preload link header used for http2 push.
 */
@SuppressWarnings({ "WeakerAccess", "unused" })
public final class Preload {

  private Preload() {}

  /**
   * Adds a preload link header. This is used to tell the server which urls to send via http 2 server push.
   * @param headers the builder for the response headers.
   * @param url the request url.
   * @param type the link type (can be null, for urls called via xhr or fetch).
   * @param noPush to specify that the preload link is not to be used for server push.
   */
  public static void addLink(final Headers.Builder headers, final HttpUrl url, @Nullable final TYPE type,
                             final boolean noPush) {
    if (type == null) {
      headers.add(
        LINK,
        "<" + url.toString() + (noPush ? ">; rel=preload; nopush" : ">; rel=preload")
      );
    }
    else {
      headers.add(
        LINK,
        "<" + url.toString() + ">; rel=preload; as=" + (noPush ? type.name + "; nopush" : type.name)
      );
    }
  }

  /**
   * Adds a preload link header. This is used to tell the server which urls to send via http 2 server push.
   * @param responseBuilder the builder for the response.
   * @param url the request url.
   * @param type the link type (can be null, for urls called via xhr or fetch).
   * @param noPush to specify that the preload link is not to be used for server push.
   * @return the response builder.
   */
  public static Response.Builder addLink(final Response.Builder responseBuilder, final HttpUrl url,
                                         @Nullable final TYPE type, final boolean noPush) {
    if (type == null) {
      responseBuilder.addHeader(
        LINK,
        "<" + url.toString() + (noPush ? ">; rel=preload; nopush" : ">; rel=preload")
      );
    }
    else {
      responseBuilder.addHeader(
        LINK,
        "<" + url.toString() + ">; rel=preload; as=" + (noPush ? type.name + "; nopush" : type.name)
      );
    }
    return responseBuilder;
  }

  /**
   * Gets the list of urls to send via http 2 server push based on the link header values.
   * @param responseBuilder the response builder.
   * @return the list of urls.
   */
  public static List<HttpUrl> getPushUrls(final Response.Builder responseBuilder) {
    return getPushUrls(responseBuilder.headers(LINK));
  }

  /**
   * Gets the list of urls to send via http 2 server push based on the link header values.
   * @param headers the response headers.
   * @return the list of urls.
   */
  public static List<HttpUrl> getPushUrls(final Headers headers) {
    return getPushUrls(headers.values(LINK));
  }

  private static List<HttpUrl> getPushUrls(@Nullable final List<String> values) {
    if (values == null || values.size() == 0) return Collections.emptyList();
    final List<HttpUrl> urls = new ArrayList<>(values.size());
    for (final String value: values) {
      final int index = value.indexOf(">; rel=preload");
      if (index != -1 && value.charAt(0) == '<' && !value.endsWith("; nopush")) {
        final HttpUrl url = HttpUrl.parse(value.substring(1, index));
        if (url != null) urls.add(url);
      }
    }
    return urls;
  }

  /**
   * Link header field name.
   */
  public static final String LINK = "Link";

  /**
   * Link preload types (<code>as</code> attribute values).
   */
  public static enum TYPE {

    SCRIPT("script"),
    STYLE("style"),
    FONT("font"),
    IMAGE("image"),
    MEDIA("media"),
    DOCUMENT("document"),
    OBJECT("object"),
    EMBED("embed"),
    WORKER("worker");

    private final String name;

    private TYPE(final String name) {
      this.name = name;
    }

  }

}
