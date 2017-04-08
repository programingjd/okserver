package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import info.jdavid.ok.server.MediaTypes;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import info.jdavid.ok.server.handler.header.AcceptRanges;
import okhttp3.MediaType;
import okio.BufferedSource;
import okio.Okio;


public class FileRequestHandler extends RegexHandler {

  private final File mWebRoot;
  private final Collection<MediaType> mAllowedMediaTypes = new ArrayList<MediaType>(48);
  private final List<String> mIndexNames;


  protected static class MediaTypeConfig {
    public final boolean ranges;
    public final boolean immutable;
    public final int maxAge;

    /**
     *
     * @param ranges whether range requests are allowed.
     * @param immutable whether the content is immutable (will never change).
     * @param maxAge time (secs) before the content is considered stale (-1 means don't cache).
     */
    public MediaTypeConfig(final boolean ranges, final boolean immutable, final int maxAge) {
      this.ranges = ranges;
      this.immutable = immutable;
      this.maxAge = maxAge;
    }
  }

  /**
   * Returns the (cache) max-age for images.
   * @return the max age in seconds.
   */
  protected int imageMaxAge() {
    return 31536000; // one year.
  }

  /**
   * Returns the (cache) max-age for large files (videos, archives, ...).
   * @return the max age in seconds;
   */
  protected int largeFileMaxAge() {
    return 0; // always re-validate.
  }

  /**
   * Returns the (cache) max-age for small files (xml, json, ...).
   * @return the max age in seconds;
   */
  protected int smallFileMaxAge() {
    return -1; // don't cache.
  }

  /**
   * Returns the (cache) max-age for css files (videos, archives, ...).
   * @return the max age in seconds;
   */
  protected int cssMaxAge() {
    return 0; // always re-validate.
  }

  /**
   * Returns the (cache) max-age for javascript files (videos, archives, ...).
   * @return the max age in seconds;
   */
  protected int jsMaxAge() {
    return 0; // always re-validate.
  }

  /**
   * Returns the (cache) max-age for html files.
   * @return the max age in seconds;
   */
  protected int htmlMaxAge() {
    return 0; // always re-validate.
  }


  /**
   * Returns the config for the specified media type.
   * @param mediaType the media type.
   * @return the config.
   */
  protected MediaTypeConfig config(final MediaType mediaType) {
    final String type = mediaType.type();
    final String subType = mediaType.subtype();
    if ("html".equals(subType) || "xhtml+xml".equals(subType) || "manifest+json".equals(subType)) {
      return new MediaTypeConfig(false, false, htmlMaxAge());
    }
    else if ("css".equals(subType)) {
      return new MediaTypeConfig(false, false, cssMaxAge());
    }
    else if ("javascript".equals(subType)) {
      return new MediaTypeConfig(false, false, jsMaxAge());
    }
    else if ("image".equals(type)) {
      return new MediaTypeConfig(false, true, imageMaxAge());
    }
    else if ("font-woff".equals(subType) || "woff2".equals(subType) || "opentype".equals(subType) ||
             "truetype".equals(subType) || "vnd.ms-fontobject".equals(subType)) {
      return new MediaTypeConfig(false, true, 31536000); // one year
    }
    else if ("json".equals(subType) || "xml".equals(subType) || "atom+xml".equals(subType) ||
             "csv".equals(subType)) {
      return new MediaTypeConfig(false, false, smallFileMaxAge());
    }
    else if ("video".equals(type) || "audio".equals(type) ||
             "gzip".equals(subType) || "x-gtar".equals(subType) || "x-xz".equals(subType) ||
             "x-7z-compressed".equals(subType) || "zip".equals(subType) ||
             "octet-stream".equals(subType) || "pdf".equals(subType)) {
      return new MediaTypeConfig(true, false, largeFileMaxAge());
    }
    else {
      return new MediaTypeConfig(false, false, 0);
    }
  }

  /**
   * Gets the list of allowed media types.
   * @return the list of media types.
   */
  protected Collection<MediaType> allowedMediaTypes() {
    return MediaTypes.defaultAllowedMediaTypes();
  }

  @Override
  public Handler setup() {
    super.setup();
    mAllowedMediaTypes.addAll(allowedMediaTypes());
    return this;
  }

  private static List<String> DEFAULT_INDEX_NAMES = Arrays.asList("index.html", "index.htm");

  /**
   * Creates a new file handler that will accept all requests.
   * @param webRoot the web root directory containing the files.
   */
  public FileRequestHandler(final File webRoot) {
    this("(.*)", webRoot, DEFAULT_INDEX_NAMES);
  }

  /**
   * Creates a new file handler that will accept all requests.
   * @param webRoot the web root directory containing the files.
   * @param indexNames the names for the index files (defaults to "index.html" and "index.htm").
   */
  public FileRequestHandler(final File webRoot, final List<String> indexNames) {
    this("(.*)", webRoot, indexNames);
  }

  /**
   * Creates a new file handler that will only accept requests whose url is matching the regular expression.
   * The regular expression must have a group capturing the path of a file relative to the web root
   * (for instance "/root1/(.*)").
   * @param regex the regex used for accepting the request and capturing the path relative to the web root.
   * @param webRoot the web root directory containing the files.
   */
  public FileRequestHandler(final String regex, final File webRoot) {
    this(regex, webRoot, DEFAULT_INDEX_NAMES);
  }

  /**
   * Creates a new file handler that will only accept requests whose url is matching the regular expression.
   * The regular expression must have a group capturing the path of a file relative to the web root
   * (for instance "/root1/(.*)").
   * @param regex the regex used for accepting the request and capturing the path relative to the web root.
   * @param indexNames the (ordered) list of file names for index (directory) requests.
   * @param webRoot the web root directory containing the files.
   */
  public FileRequestHandler(final String regex,
                            final File webRoot, final List<String> indexNames) {
    super("GET", regex);
    mWebRoot = webRoot;
    mIndexNames = indexNames;
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final int n = params.length;
    if (n < 1) return new Response.Builder().statusLine(StatusLines.INTERNAL_SERVER_ERROR).noBody();
    final String path = params[n-1];
    final File file = new File(mWebRoot, path.startsWith("/") ? path.substring(1) : path);
    if (file.isDirectory()) {
      final int pathLength = path.length();
      if (pathLength > 0 && path.charAt(pathLength - 1) != '/') {
        final File index = index(file);
        if (index == null) {
          return new Response.Builder().
            statusLine(StatusLines.FORBIDDEN).
            noBody();
        }
        else {
          return new Response.Builder().
            statusLine(StatusLines.MOVED_PERMANENTLY).
            location(request.url.newBuilder().addPathSegment("").build()).
            noBody();
        }
      }
    }
    final String filename = file.getName();
    if (file.isFile() && mIndexNames.contains(filename)) {
      final File index = index(file.getParentFile());
      if (index != null && index.getName().equals(filename)) {
        return new Response.Builder().
          statusLine(StatusLines.MOVED_PERMANENTLY).
          location(request.url.newBuilder("./").build()).
          noBody();
      }
    }
    final MediaType mediaType = mediaType(file);
    if (mediaType == null) {
      return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
    }
    else {
      final MediaType m;
      final File f;
      if (mediaType == MediaTypes.DIRECTORY) {
        f = index(file);
        if (f == null) return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
        m = mediaType(f);
      }
      else {
        f = file;
        if (mAllowedMediaTypes.size() > 0 && !mAllowedMediaTypes.contains(mediaType)) {
          return new Response.Builder().statusLine(StatusLines.FORBIDDEN).noBody();
        }
        m = mediaType;
      }
      final String etag = etag(file);
      if (etag != null && etag.equalsIgnoreCase(request.headers.get("If-None-Match"))) {
        return new Response.Builder().statusLine(StatusLines.NOT_MODIFIED).noBody();
      }
      if (f.exists()) {
        try {
          final MediaTypeConfig config = config(mediaType);
          if (config == null) {
            return new Response.Builder().statusLine(StatusLines.INTERNAL_SERVER_ERROR).noBody();
          }
          final Response.Builder response = new Response.Builder().etag(etag);
          switch (config.maxAge) {
            case -1:
              response.noStore();
              break;
            case 0:
              response.noCache(null);
              break;
            default:
              response.maxAge(config.maxAge, config.immutable);
              break;
          }
          if (config.ranges) {
            response.header(AcceptRanges.HEADER, AcceptRanges.BYTES);
            final String rangeHeaderValue = request.headers.get(AcceptRanges.RANGE);
            if (rangeHeaderValue == null) {
              response.statusLine(StatusLines.OK).body(m, Okio.buffer(Okio.source(f)), (int)f.length());
            }
            else {
              if (!rangeHeaderValue.startsWith("Range: bytes=")) {
                return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
              }
              final String bytesRanges = rangeHeaderValue.substring(13);
              final String[] ranges = bytesRanges.split(", ");
              if (ranges.length == 0) {
                return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
              }
              if (ranges.length == 1) {
                final String range = ranges[0];
                final int dashIndex = range.indexOf('-');
                if (dashIndex == -1) {
                  return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                }
                if (range.indexOf('-', dashIndex + 1) != -1) { // negative number.
                  return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                }
                final long start;
                if (dashIndex == 0) {
                  start = 0;
                } else {
                  try {
                    start = Long.parseLong(range.substring(0, dashIndex));
                  }
                  catch (final NumberFormatException ignore) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                  if (start > f.length()) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                }
                final long end;
                if (dashIndex == range.length() - 1) {
                  end = f.length();
                }
                else {
                  try {
                    end = Long.parseLong(range.substring(dashIndex + 1));
                  }
                  catch (final NumberFormatException ignore) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                  if (end > f.length()) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                }
                if (start > end) {
                  return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                }
                // todo
                //response.statusLine(StatusLines.PARTIAL).body(m, )
              }
              else {
                // todo multipart ranges
                // https://developer.mozilla.org/en-US/docs/Web/HTTP/Range_requests
              }
            }
          }
          else {
            response.statusLine(StatusLines.OK).body(m, Okio.buffer(Okio.source(f)), (int)f.length());
          }
          return response;
        }
        catch (final FileNotFoundException e) {
          return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
        }
      }
      else {
        return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
      }
    }
  }

  protected MediaType mediaType(final File file) {
    return MediaTypes.fromFile(file);
  }

  protected String etag(final File file) {
    return String.format("%012x", file.lastModified());
  }

  private File index(final File file) {
    for (final String name: mIndexNames) {
      final File index = new File(file, name);
      if (index.exists()) {
        return index;
      }
    }
    return null;
  }

}
