package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import info.jdavid.ok.server.MediaTypes;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import info.jdavid.ok.server.header.AcceptEncoding;
import info.jdavid.ok.server.header.AcceptRanges;
import info.jdavid.ok.server.header.ETag;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSink;
import okio.Okio;
import okio.Sink;
import okio.Source;
import okio.Timeout;


/**
 * Handler for serving files under a web root.
 */
@SuppressWarnings("WeakerAccess")
public class FileHandler extends RegexHandler {

  final File webRoot;
  final Collection<MediaType> allowedMediaTypes = new ArrayList<MediaType>(48);
  final List<String> indexNames;

  /**
   * Media Type configuration settings (enable compression, enable range requests, immutable
   * and max age).
   */
  public static class MediaTypeConfig {
    final boolean compress;
    final boolean ranges;
    final boolean immutable;
    final int maxAge;

    /**
     * @param compress whether compression should be used.
     * @param ranges whether range requests are allowed.
     * @param immutable whether the content is immutable (will never change).
     * @param maxAge time (secs) before the content is considered stale (-1 means don't cache).
     */
    public MediaTypeConfig(final boolean compress, final boolean ranges,
                           final boolean immutable, final int maxAge) {
      this.compress = compress;
      this.ranges = ranges;
      this.immutable = immutable;
      this.maxAge = maxAge;
    }
  }

  /**
   * Container object for a BufferedSource and its size in bytes.
   */
  public static class BufferedSourceWithSize {
    public final BufferedSource source;
    public final long size;

    public BufferedSourceWithSize(final BufferedSource source, final long size) {
      this.source = source;
      this.size = size;
    }
  }

  /**
   * Gets the web root directory.
   * @return the web root directory.
   */
  @SuppressWarnings("unused")
  protected final File getWebRoot() {
    return webRoot;
  }

  /**
   * Finds out if a media type is allowed or not.
   * @param mediaType the media type.
   * @return true if the media type is allowed, false if it is not.
   */
  protected boolean isAllowed(final MediaType mediaType) {
    return allowedMediaTypes.contains(mediaType);
  }

  /**
   * Finds out if the specified file is a directory index or not.
   * @param file the file.
   * @return true if it is a directory index, false if it is not.
   */
  protected boolean isIndexFile(final File file) {
    final String filename = file.getName();
    if (file.isFile() && indexNames.contains(filename)) {
      final File index = index(file.getParentFile());
      if (index != null && index.getName().equals(filename)) {
        return true;
      }
    }
    return false;
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
      return new MediaTypeConfig(true, false, false, htmlMaxAge());
    }
    else if ("css".equals(subType)) {
      return new MediaTypeConfig(true, false, false, cssMaxAge());
    }
    else if ("javascript".equals(subType)) {
      return new MediaTypeConfig(true, false, false, jsMaxAge());
    }
    else if ("image".equals(type)) {
      return new MediaTypeConfig(subType.equals("svg+xml"), false, true, imageMaxAge());
    }
    else if ("font-woff".equals(subType) || "woff2".equals(subType) || "vnd.ms-fontobject".equals(subType)) {
      return new MediaTypeConfig(false,false, true, 31536000); // one year
    }
    else if ("opentype".equals(subType) || "truetype".equals(subType)) {
      return new MediaTypeConfig(true,false, true, 31536000); // one year
    }
    else if ("json".equals(subType) || "xml".equals(subType) || "atom+xml".equals(subType) ||
             "csv".equals(subType)) {
      return new MediaTypeConfig(true,false, false, smallFileMaxAge());
    }
    else if ("video".equals(type) || "audio".equals(type) ||
             "gzip".equals(subType) || "x-gtar".equals(subType) || "x-xz".equals(subType) ||
             "x-7z-compressed".equals(subType) || "zip".equals(subType) ||
             "octet-stream".equals(subType) || "pdf".equals(subType)) {
      return new MediaTypeConfig(false,true, false, largeFileMaxAge());
    }
    else {
      return new MediaTypeConfig(type.equals("text"), false, false, 0);
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
    allowedMediaTypes.addAll(allowedMediaTypes());
    return this;
  }

  private static final List<String> DEFAULT_INDEX_NAMES = Arrays.asList("index.html", "index.htm");

  /**
   * Creates a new file handler that will accept all requests.
   * @param webRoot the web root directory containing the files.
   */
  public FileHandler(final File webRoot) {
    this("(.*)", webRoot, DEFAULT_INDEX_NAMES);
  }

  /**
   * Creates a new file handler that will accept all requests.
   * @param webRoot the web root directory containing the files.
   * @param indexNames the names for the index files (defaults to "index.html" and "index.htm").
   */
  public FileHandler(final File webRoot, final List<String> indexNames) {
    this("(.*)", webRoot, indexNames);
  }

  /**
   * Creates a new file handler that will only accept requests whose url is matching the regular expression.
   * The regular expression must have a group capturing the path of a file relative to the web root
   * (for instance "/root1/(.*)").
   * @param regex the regex used for accepting the request and capturing the path relative to the web root.
   * @param webRoot the web root directory containing the files.
   */
  public FileHandler(final String regex, final File webRoot) {
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
  public FileHandler(final String regex,
                     final File webRoot, final List<String> indexNames) {
    super(Arrays.asList("HEAD", "GET"), regex);
    this.webRoot = webRoot;
    this.indexNames = indexNames;
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    final int n = params.length;
    if (n < 1) return new Response.Builder().statusLine(StatusLines.INTERNAL_SERVER_ERROR).noBody();
    final String path = params[n-1];
    final File file = file(path, request);
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
    if (isIndexFile(file)) {
      //noinspection ConstantConditions
      final HttpUrl redirectUrl = request.url.newBuilder("./").build();
      return new Response.Builder().
        statusLine(StatusLines.MOVED_PERMANENTLY).
        location(redirectUrl).
        noBody();
    }
    final MediaType mediaType = mediaType(file, request);
    if (mediaType == null) {
      return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
    }
    else {
      final MediaType m;
      final File f;
      if (mediaType == MediaTypes.DIRECTORY) {
        f = index(file);
        if (f == null) return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
        m = mediaType(f, request);
        assert(m != null);
      }
      else {
        f = file;
        if (!isAllowed(mediaType)) {
          return new Response.Builder().statusLine(StatusLines.FORBIDDEN).noBody();
        }
        m = mediaType;
      }
      final String etag = etag(request, f, webRoot);
      if (etag != null && etag.equalsIgnoreCase(request.headers.get(ETag.IF_NONE_MATCH))) {
          return new Response.Builder().statusLine(StatusLines.NOT_MODIFIED).noBody();
      }
      if (f.exists()) {
        try {
          final MediaTypeConfig config = config(mediaType);
          if (config == null) {
            return new Response.Builder().statusLine(StatusLines.INTERNAL_SERVER_ERROR).noBody();
          }
          final boolean compress = config.compress;
          final boolean gzip = compress && AcceptEncoding.supportsGZipEncoding(request.headers);
          final Response.Builder response = new Response.Builder().etag(etag);
          switch (config.maxAge) {
            case -1:
              response.noStore();
              break;
            case 0:
              response.noCache(etag);
              break;
            default:
              if (etag != null) response.etag(etag);
              response.maxAge(config.maxAge, config.immutable);
              break;
          }
          if (config.ranges) {
            response.header(AcceptRanges.HEADER, AcceptRanges.BYTES);
            final String rangeHeaderValue = request.headers.get(AcceptRanges.RANGE);
            if (rangeHeaderValue == null) {
              final BufferedSourceWithSize buffered = source(request, f, etag, compress, gzip);
              if (gzip) response.header(AcceptEncoding.CONTENT_ENCODING, AcceptEncoding.GZIP);
              return response.statusLine(StatusLines.OK).body(m, buffered.source, buffered.size);
            }
            else {
              if (!rangeHeaderValue.startsWith(AcceptRanges.BYTES)) {
                return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
              }
              if (etag != null) {
                if (!ifMatch(request, etag)) {
                  return response.statusLine(StatusLines.REQUEST_RANGE_NOT_SATISFIABLE).noBody();
                }
                if (!ifRangeMatch(request, etag)) {
                  final BufferedSourceWithSize buffered = source(request, f, etag, compress, gzip);
                  if (gzip) response.header(AcceptEncoding.CONTENT_ENCODING, AcceptEncoding.GZIP);
                  return response.statusLine(StatusLines.OK).body(m, buffered.source, buffered.size);
                }
              }
              final String bytesRanges = rangeHeaderValue.substring(AcceptRanges.BYTES.length() + 1);
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
                final long fileLength = f.length();
                final long start;
                if (dashIndex == 0) {
                  start = 0;
                }
                else {
                  try {
                    start = Long.parseLong(range.substring(0, dashIndex));
                  }
                  catch (final NumberFormatException ignore) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                  if (start > fileLength) {
                    return new Response.Builder().
                      statusLine(StatusLines.REQUEST_RANGE_NOT_SATISFIABLE).noBody();
                  }
                }
                final long end;
                if (dashIndex == range.length() - 1) {
                  end = fileLength;
                }
                else {
                  try {
                    end = Long.parseLong(range.substring(dashIndex + 1));
                  }
                  catch (final NumberFormatException ignore) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                  if (end > fileLength) {
                    return new Response.Builder().
                      statusLine(StatusLines.REQUEST_RANGE_NOT_SATISFIABLE).noBody();
                  }
                }
                if (start > end) {
                  return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                }
                try {
                  final BufferedSourceWithSize buffered =
                    source(request, f, etag, start, end, compress, gzip);
                  if (gzip) response.header(AcceptEncoding.CONTENT_ENCODING, AcceptEncoding.GZIP);
                  return response.statusLine(StatusLines.PARTIAL).
                    header(AcceptRanges.CONTENT_RANGE,
                           AcceptRanges.BYTES + " " + start + "-" + end + "/" + fileLength).
                    body(m, buffered.source, buffered.size);
                }
                catch (final FileNotFoundException ignore) {
                  return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
                }
                catch (final IOException ignored) {
                  return new Response.Builder().statusLine(StatusLines.INTERNAL_SERVER_ERROR).noBody();
                }
              }
              else {
                final long fileLength = f.length();
                for (final String range: ranges) {
                  final int dashIndex = range.indexOf('-');
                  if (dashIndex == -1) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                  if (range.indexOf('-', dashIndex + 1) != -1) { // negative number.
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                  final long start;
                  if (dashIndex != 0) {
                    try {
                      start = Long.parseLong(range.substring(0, dashIndex));
                    }
                    catch (final NumberFormatException ignore) {
                      return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                    }
                    if (start > fileLength) {
                      return new Response.Builder().
                        statusLine(StatusLines.REQUEST_RANGE_NOT_SATISFIABLE).noBody();
                    }
                  }
                  else {
                    start = 0;
                  }
                  final long end;
                  if (dashIndex != range.length() - 1) {
                    try {
                      end = Long.parseLong(range.substring(dashIndex + 1));
                    }
                    catch (final NumberFormatException ignore) {
                      return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                    }
                    if (end > fileLength) {
                      return new Response.Builder().
                        statusLine(StatusLines.REQUEST_RANGE_NOT_SATISFIABLE).noBody();
                    }
                  }
                  else {
                    end = fileLength;
                  }
                  if (start > end) {
                    return new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody();
                  }
                }
                final AcceptRanges.ByteRangesBody.Builder multipart =
                  new AcceptRanges.ByteRangesBody.Builder(m);
                for (final String range: ranges) {
                  final int dashIndex = range.indexOf('-');
                  final long start;
                  if (dashIndex == 0) {
                    start = 0;
                  }
                  else {
                    try {
                      start = Long.parseLong(range.substring(0, dashIndex));
                    }
                    catch (final NumberFormatException e) {
                      throw new RuntimeException(e);
                    }
                  }
                  final long end;
                  if (dashIndex == range.length() - 1) {
                    end = fileLength;
                  }
                  else {
                    try {
                      end = Long.parseLong(range.substring(dashIndex + 1));
                    }
                    catch (final NumberFormatException e) {
                      throw new RuntimeException(e);
                    }
                  }
                  try {
                    // never use gzip compression for multipart ranges.
                    final BufferedSourceWithSize buffered =
                      source(request, f, etag, start, end, compress, false);
                    multipart.addRange(buffered.source, start, end, fileLength);
                  }
                  catch (final FileNotFoundException ignore) {
                    return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
                  }
                  catch (final IOException ignored) {
                    return new Response.Builder().statusLine(StatusLines.INTERNAL_SERVER_ERROR).noBody();
                  }
                }
                return response.statusLine(StatusLines.PARTIAL).body(multipart.build());
              }
            }
          }
          else {
            final BufferedSourceWithSize buffered = source(request, f, etag, compress, gzip);
            if (gzip) response.header(AcceptEncoding.CONTENT_ENCODING, AcceptEncoding.GZIP);
            return response.statusLine(StatusLines.OK).body(m, buffered.source, buffered.size);
          }
        }
        catch (final FileNotFoundException ignore) {
          return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
        }
      }
      else {
        return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
      }
    }
  }

  private @Nullable File index(final File file) {
    for (final String name: indexNames) {
      final File index = new File(file, name);
      if (index.exists()) {
        return index;
      }
    }
    return null;
  }

  private boolean ifRangeMatch(final Request request, final String etag) {
    final String value = request.headers.get(AcceptRanges.IF_RANGE);
    if (value != null) {
      final int firstQuote = value.indexOf('"');
      final int lastQuote = value.lastIndexOf('"');
      if (firstQuote != -1 && lastQuote > firstQuote) {
        return etag.equals(value.substring(firstQuote + 1, lastQuote));
      }
    }
    return true;
  }

  private boolean ifMatch(final Request request, final String etag) {
    final String value = request.headers.get(ETag.IF_MATCH);
    if (value != null) {
      final String[] values = value.split(", ");
      for (final String v: values) {
        final int n = v.length();
        if (n > 2 && v.charAt(0) == '"' && v.charAt(n - 1) == '"') {
          if (etag.equals(v.substring(1, n-1))) return true;
        }
      }
      return false;
    }
    return true;
  }

  /**
   * Returns the file represented by the specified path.
   * @param path the path.
   * @param request the request object.
   * @return the file.
   */
  protected File file(final String path, @SuppressWarnings("unused") final Request request) {
    return new File(webRoot, path.startsWith("/") ? path.substring(1) : path);
  }

  /**
   * Returns the source and its size for the requested file. This looks into the cache (and updates it)
   * if possible.
   * @param request the request object.
   * @param f the file matching the request.
   * @param etag the resource etag.
   * @param compress true if the resource should be compressed (according to the config), false if not.
   * @param gzip true if the resourse should be compressed and the client supports gzip compression,
   * false otherwise.
   * @return the source with its size information.
   * @throws FileNotFoundException if the requested file is missing.
   */
  protected BufferedSourceWithSize source(@SuppressWarnings("unused") final Request request,
                                          final File f, @Nullable final String etag,
                                          final boolean compress,
                                          final boolean gzip) throws FileNotFoundException {
    final BufferedSourceWithSize source1 = fromCache(f, etag, compress, gzip);
    if (source1 != null) return source1;
    final BufferedSourceWithSize source2 = cache(f, etag, compress, gzip);
    if (source2 != null) return source2;
    final RandomAccessFileSource source = new RandomAccessFileSource(f);
    if (gzip) {
      final ByteCountingSink counting = new ByteCountingSink();
      final BufferedSource buffered = Okio.buffer(new CompressedSource(source, false));
      try {
        buffered.readAll(counting);
        buffered.close();
        source.reset(0L);
        return new BufferedSourceWithSize(
          Okio.buffer(new CompressedSource(source, true)),
          counting.length
        );
      }
      catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return new BufferedSourceWithSize(Okio.buffer(source), f.length());
    }
  }

  /**
   * Returns the source and its size for the requested file range. This looks into the cache (and updates it)
   * if possible.
   * @param request the request object.
   * @param f the file matching the request.
   * @param etag the resource etag.
   * @param start the start byte index.
   * @param end the end byte index.
   * @param compress true if the resource should be compressed (according to the config), false if not.
   * @param gzip true if the resourse should be compressed and the client supports gzip compression,
   * false otherwise.
   * @return the source with its size information.
   * @throws FileNotFoundException if the requested file is missing.
   */
  protected BufferedSourceWithSize source(@SuppressWarnings("unused") final Request request,
                                          final File f, @Nullable final String etag,
                                          final long start, final long end,
                                          final boolean compress,
                                          final boolean gzip) throws IOException {
    final BufferedSourceWithSize source1 = fromCache(f, etag, start, end, compress, gzip);
    if (source1 != null) return source1;
    final BufferedSourceWithSize source2 = cache(f, etag, start, end, compress, gzip);
    if (source2 != null) return source2;
    final RandomAccessFileSource source = new RandomAccessFileSource(f, start, end);
    if (gzip) {
      final ByteCountingSink counting = new ByteCountingSink();
      final BufferedSource buffered = Okio.buffer(new CompressedSource(source, false));
      try {
        buffered.readAll(counting);
        buffered.close();
        source.reset(start);
        return new BufferedSourceWithSize(
          Okio.buffer(new CompressedSource(source, true)),
          counting.length
        );
      }
      catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return new BufferedSourceWithSize(Okio.buffer(source), end - start);
    }
  }

  /**
   * Returns the media type of the specified file. It can return null for unsupported files (files that are
   * not supposed to be served by the server).
   * @param file the file.
   * @param request the request object.
   * @return the media type (can be null).
   */
  protected @Nullable MediaType mediaType(final File file,
                                          @SuppressWarnings("unused") final Request request) {
    return mediaType(file);
  }

  /**
   * Returns the media type of the specified file. It can return null for unsupported files (files that are
   * not supposed to be served by the server).
   * @param file the file.
   * @return the media type (can be null).
   */
  protected @Nullable MediaType mediaType(final File file) {
    return MediaTypes.fromFile(file);
  }

  /**
   * Calculates the E-Tag for the specified file. It can return null for unsupported files (files that are
   * not supposed to be served by the server).
   * @param  request the request object (can be used to access the query parameters, or the headers).
   * @param file the file.
   * @param webRoot the web root directory.
   * @return the E-Tag (can be null).
   */
  protected @Nullable String etag(@SuppressWarnings("unused") final Request request,
                                  final File file, final File webRoot) {
    return etag(file, webRoot);
  }

  /**
   * Calculates the E-Tag for the specified file. It can return null for unsupported files (files that are
   * not supposed to be served by the server).
   * @param file the file.
   * @param webRoot the web root directory.
   * @return the E-Tag (can be null).
   */
  protected @Nullable String etag(final File file, final File webRoot) {
    final String path = file.getAbsolutePath().
      substring(webRoot.getAbsolutePath().length()).
      replace('\\','/');
    return new AuthHandler.Base64Helper().encode(path) + String.format("%012x", file.lastModified());
  }

  /**
   * Gets the file content from the cache if it's available, returns null otherwise.
   * @param file the file.
   * @param etag the content E-Tag.
   * @param compress true if the content media type should be compressed (according to the config).
   * @param gzip true if the content should be compressed and the client supports gzip compression.
   * @return the cached content.
   */
  @SuppressWarnings("unused")
  protected BufferedSourceWithSize fromCache(final File file, @Nullable final String etag,
                                             final boolean compress, final boolean gzip) {
    return null;
  }

  /**
   * Gets the partial file content from the cache if it's available, returns null otherwise.
   * @param file the file.
   * @param etag the content E-Tag.
   * @param start the range start byte index.
   * @param end the range end byte index.
   * @param compress true if the content media type should be compressed (according to the config).
   * @param gzip true if the content should be compressed and the client supports gzip compression.
   * @return the cached content.
   */
  @SuppressWarnings("unused")
  protected BufferedSourceWithSize fromCache(final File file, @Nullable final String etag,
                                             final long start, final long end,
                                             final boolean compress, final boolean gzip) {
    return null;
  }

  /**
   * Caches the content of the file with the specified etag, and returns the cached content. This can return
   * null if no cache is used (the default).
   * @param file the file.
   * @param etag the E-Tag.
   * @param compress true if the content media type should be compressed (according to the config).
   * @param gzip true if the content should be compressed and the client supports gzip compression.
   * @return the cached content.
   */
  @SuppressWarnings("unused")
  protected BufferedSourceWithSize cache(final File file, @Nullable final String etag,
                                         final boolean compress, final boolean gzip) {
    return null;
  }

  /**
   * Caches the partial content of the file with the specified etag, and returns the partial cached content.
   * This can return null if no cache is used (the default).
   * @param file the file.
   * @param etag the E-Tag.
   * @param start the range start byte index.
   * @param end the range end byte index.
   * @param compress true if the content media type should be compressed (according to the config).
   * @param gzip true if the content should be compressed and the client supports gzip compression.
   * @return the cached content.
   */
  @SuppressWarnings("unused")
  protected BufferedSourceWithSize cache(final File file, @Nullable final String etag,
                                         final long start, final long end,
                                         final boolean compress, final boolean gzip) {
    return null;
  }

  class RandomAccessFileSource implements Source {

    final Timeout timeout = new Timeout();
    final RandomAccessFile randomAccessFile;
    final byte[] buffer = new byte[8192];
    int pos = 0;
    int len = 0;

    public RandomAccessFileSource(final File f) throws FileNotFoundException {
      randomAccessFile = new RandomAccessFile(f, "r");
    }

    public RandomAccessFileSource(final File f, final long start,
                                  @SuppressWarnings("unused") final long end) throws IOException {
      randomAccessFile = new RandomAccessFile(f, "r");
      randomAccessFile.seek(start);
    }

    @Override
    public long read(final Buffer sink, final long byteCount) throws IOException {
      if (len > pos) {
        final int n = (int)Math.min(len - pos, byteCount);
        sink.write(buffer, pos, n);
        if (pos + n == len) {
          pos = len = 0;
        }
        else {
          pos += n;
        }
        return n;
      }
      len = randomAccessFile.read(buffer, 0, buffer.length);
      if (len == -1) return -1L;
      final int n = (int)Math.min(len, byteCount);
      sink.write(buffer, 0, n);
      len -= n;
      pos += n;
      return n;
    }

    @Override
    public Timeout timeout() {
      return timeout;
    }

    @Override
    public void close() throws IOException {
      randomAccessFile.close();
    }

    public void reset(final long start) throws IOException {
      randomAccessFile.seek(start);
      pos = 0;
      len = 0;
    }

  }

  static class ByteCountingSink implements Sink {
    int length = 0;
    @Override public void write(final Buffer source, final long byteCount) throws IOException {
      source.skip(byteCount);
      length += byteCount;
    }
    @Override public void flush() {}
    @Override public Timeout timeout() { return null; }
    @Override public void close() {}
  }

  static class CompressedSource implements Source {

    final boolean closeSourceOnClose;
    final Source source;
    final Buffer sourceBuffer = new Buffer();
    final Buffer sinkBuffer = new Buffer();
    final GzipSink sink = new GzipSink(sinkBuffer);
    boolean closed = false;

    CompressedSource(final Source uncompressed, final boolean closeSourceOnClose) {
      this.source = uncompressed;
      this.closeSourceOnClose = closeSourceOnClose;
    }

    @Override
    public long read(final Buffer sink, final long byteCount) throws IOException {
      if (closed) return -1L;
      if (sinkBuffer.size() > 0) {
        final long count = Math.min(byteCount, sinkBuffer.size());
        sink.write(sinkBuffer, count);
        return count;
      }
      if (source.read(sourceBuffer, 262144L) == -1L) {
        this.sink.write(sourceBuffer, sourceBuffer.size());
        closed = true;
        this.sink.close();
      }
      else {
        this.sink.write(sourceBuffer, sourceBuffer.size());
        this.sink.flush();
      }
      final long count = Math.min(byteCount, sinkBuffer.size());
      sink.write(sinkBuffer, count);
      return count;
    }

    @Override
    public Timeout timeout() {
      return source.timeout();
    }

    @Override
    public void close() throws IOException {
      if (closeSourceOnClose) source.close();
      if (!closed) sink.close();
    }

  }

}
