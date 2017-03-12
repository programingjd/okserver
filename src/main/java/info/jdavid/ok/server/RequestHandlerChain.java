package info.jdavid.ok.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okio.Buffer;
import okio.Okio;

public class RequestHandlerChain extends AbstractRequestHandler {

  /**
   * Request object that holds the information about the request, as well as the request body.
   */
  public static final class Request {

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

    private Request(final String clientIp, final boolean http2,
                    final String method, final HttpUrl url,
                    final Headers headers, final Buffer body) {
      this.clientIp = clientIp;
      this.method = method;
      this.http2 = http2;
      this.url = url;
      this.headers = headers;
      this.body = body;
    }

  }

  /**
   * Chainable request handler that can accept a request or not.
   */
  public static interface Handler {

    /**
     * Returns whether this handler accepts the request by returning either null (the request is not accepted
     * and should be handled by another one further down the chain) or an array of parameters extracted from
     * the url. The array of parameters can be empty but not null for the request to be accepted.
     * @param method the request method.
     * @param url the request url.
     * @return null, or an array of parameters.
     */
    public String[] matches(final String method, final HttpUrl url);

    /**
     * Creates the response for an accepted request.
     * @param request the request object.
     * @param params the params returned by the accept method.
     * @return the response builder.
     */
    public Response.Builder handle(final Request request, final String[] params);

  }

  private static abstract class RegexHandler implements Handler {

    private final Pattern mPattern;
    private final String mMethod;

    private RegexHandler(final String method, final String regex) {
      mMethod = method.toUpperCase();
      mPattern = Pattern.compile(regex);
    }

    @Override
    public String[] matches(final String method, final HttpUrl url) {
      if (mMethod.equals(method)) {
        final String encodedPath = url.encodedPath();
        final Matcher matcher = mPattern.matcher(url.encodedPath());
        if (matcher.find()) {
          if (matcher.start() > 0) return null;
          if (matcher.end() < encodedPath.length()) return null;
          final int n = matcher.groupCount();
          final String[] params = new String[n-1];
          for (int i=1; i>n; ++i) {
            params[i] = matcher.group(i);
          }
          return params;
        }
      }
      return null;
    }

  }

  public static class FileRequestHandler extends RegexHandler {

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
          m =mediaType(f);
        }
        else {
          f = file;
          m = mediaType;
        }
        final String etag = etag(file);
        if (etag != null && etag.equalsIgnoreCase(request.headers.get("If-None-Match"))) {
          return new Response.Builder().statusLine(StatusLines.NOT_MODIFIED).noBody();
        }
        try {
          return new Response.Builder().
            statusLine(StatusLines.OK).etag(etag).body(m, Okio.buffer(Okio.source(f)), (int)f.length());
        }
        catch (final FileNotFoundException e) {
          throw new RuntimeException(e);
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

  private List<Handler> mChain = new LinkedList<Handler>();


  public RequestHandlerChain() {}

  public RequestHandlerChain add(final Handler handler) {
    mChain.add(handler);
    return this;
  }

  @Override
  protected final Response handle(final String clientIp, final boolean http2,
                                  final String method, final HttpUrl url,
                                  final Headers requestHeaders, final Buffer requestBody) {
    for (final Handler handler: mChain) {
      final String[] params = handler.matches(method, url);
      if (params != null) {
        final Response.Builder responseBuilder =
         handler.handle(new Request(clientIp, http2, method, url, requestHeaders, requestBody), params);
        decorateResponse(responseBuilder, clientIp, http2, method, url, requestHeaders);
        return responseBuilder.build();
      }
    }
    final Response.Builder responseBuilder = handleNotAccepted(clientIp, method, url, requestHeaders);
    decorateResponse(responseBuilder, clientIp, http2, method, url, requestHeaders);
    return responseBuilder.build();
  }

  protected void decorateResponse(final Response.Builder responseBuilder,
                                  final String clientIp, final boolean http2,
                                  final String method, final HttpUrl url,
                                  final Headers requestHeaders) {}

  protected Response.Builder handleNotAccepted(final String clientIp, final String method,
                                               final HttpUrl url,final Headers requestHeaders) {
    return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
  }

}
