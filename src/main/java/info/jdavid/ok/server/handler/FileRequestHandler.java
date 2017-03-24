package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.jdavid.ok.server.MediaTypes;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.MediaType;
import okio.Okio;


public class FileRequestHandler extends RegexHandler {

  private final File mWebRoot;
  private final Map<MediaType, Integer> mAcceptMediaTypes = new HashMap<MediaType, Integer>();
  private final List<String> mIndexNames;

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
   * Creates a new file handler that will only accept requests matching the regex. The regex must
   * have a group capturing the path of file relative to the web root (for instance "/root1/(.*)").
   * @param regex the regex used for accepting the request and capturing the path relative to the web root.
   * @param webRoot the web root directory containing the files.
   */
  public FileRequestHandler(final String regex, final File webRoot) {
    this(regex, webRoot, DEFAULT_INDEX_NAMES);
  }

  /**
   * Creates a new file handler that will only accept requests matching the regex. The regex must
   * have a group capturing the path of file relative to the web root (for instance "/root1/(.*)").
   * @param regex the regex used for accepting the request and capturing the path relative to the web root.
   * @param webRoot the web root directory containing the files.
   */
  public FileRequestHandler(final String regex,
                            final File webRoot, final List<String> indexNames) {
    super("GET", "(.*)");
    mWebRoot = webRoot;
    mIndexNames = indexNames;
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final int n = params.length;
    if (n < 1) return new Response.Builder().statusLine(StatusLines.INTERNAL_SERVER_ERROR).noBody();
    final String path = params[n-1];
    final File file = new File(mWebRoot, path.startsWith("/") ? path.substring(1) : path);
    final String filename = file.getName();
    if (mIndexNames.contains(filename)) {
      // redirect index files e.g. /a/b/index.html to /a/b/
      return new Response.Builder().
        statusLine(StatusLines.PERMANENT_REDIRECT).
        location(request.url.newBuilder("./").build()).
        noBody();
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
        if (mAcceptMediaTypes.size() > 0 && !mAcceptMediaTypes.containsKey(mediaType)) {
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
          return new Response.Builder().
            statusLine(StatusLines.OK).etag(etag).body(m, Okio.buffer(Okio.source(f)), (int)f.length());
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
    return null;
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
