package info.jdavid.ok.server.header;

import okhttp3.Headers;
import okhttp3.HttpUrl;

/**
 * Preload link header used for http2 push.
 */
public final class Preload {

  private Preload() {}

  public static void addLink(final Headers.Builder headers, final HttpUrl url, final TYPE type) {
    headers.add(LINK, "<" + url.toString() + ">; rel=preload; as=" + type.name);
  }

  /**
   * Link header field name.
   */
  public static final String LINK = "Link";

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
