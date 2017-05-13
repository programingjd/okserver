package info.jdavid.ok.server.handler;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import okhttp3.MediaType;


@SuppressWarnings({ "WeakerAccess" })
public class PreCachedFileRequestHandler extends FileRequestHandler {

  /**
   * Creates a new file handler that will accept all requests.
   * All files under the web root whose media type is allowed are cached during setup.
   * @param webRoot the web root directory containing the files.
   */
  public PreCachedFileRequestHandler(final File webRoot) {
    super(webRoot);
  }

  /**
   * Creates a new file handler that will accept all requests.
   * All files under the web root whose media type is allowed are cached during setup.
   * @param webRoot the web root directory containing the files.
   * @param indexNames the names for the index files (defaults to "index.html" and "index.htm").
   */
  public PreCachedFileRequestHandler(final File webRoot, final List<String> indexNames) {
    super(webRoot, indexNames);
  }

  /**
   * Creates a new file handler that will only accept requests whose url is matching the regular expression.
   * The regular expression must have a group capturing the path of a file relative to the web root
   * (for instance "/root1/(.*)").
   * All files under the web root whose media type is allowed are cached during setup.
   * @param regex the regex used for accepting the request and capturing the path relative to the web root.
   * @param webRoot the web root directory containing the files.
   */
  public PreCachedFileRequestHandler(final String regex, final File webRoot) {
    super(regex, webRoot);
  }

  /**
   * Creates a new file handler that will only accept requests whose url is matching the regular expression.
   * The regular expression must have a group capturing the path of a file relative to the web root
   * (for instance "/root1/(.*)").
   * All files under the web root whose media type is allowed are cached during setup.
   * @param regex the regex used for accepting the request and capturing the path relative to the web root.
   * @param indexNames the (ordered) list of file names for index (directory) requests.
   * @param webRoot the web root directory containing the files.
   */
  public PreCachedFileRequestHandler(String regex, File webRoot, List<String> indexNames) {
    super(regex, webRoot, indexNames);
  }

  @Override
  public Handler setup() {
    super.setup();
    final Deque<File> deque = new ArrayDeque<File>();
    deque.push(webRoot);
    while (!deque.isEmpty()) {
      final File current = deque.pop();
      if (current.isDirectory() && acceptDirectory(current)) {
        final File[] children = current.listFiles();
        if (children != null) {
          for (final File child: children) {
            deque.push(child);
          }
        }
      }
      else if (current.isFile()) {
        final MediaType mediaType = mediaType(current);
        if (acceptMediaType(mediaType)) {
          if (acceptFile(current)) {
            final boolean compress = config(mediaType).compress;
            cache(webRoot, etag(current, webRoot), config(mediaType).compress, compress);
          }
        }
      }
    }
    return this;
  }

  protected boolean acceptDirectory(final File directory) {
    return directory.getName().charAt(0) == '.';
  }

  protected boolean acceptMediaType(final MediaType mediaType) {
    return isAllowed(mediaType);
  }

  @SuppressWarnings("unused")
  protected boolean acceptFile(final File file) {
    return true;
  }

  @Override
  protected BufferedSourceWithSize fromCache(final File file, final String etag,
                                             final boolean compress, final boolean gzip) {
    return null;
  }

  @Override
  protected BufferedSourceWithSize fromCache(final File file, final String etag,
                                             final long start, final long end,
                                             final boolean compress, final boolean gzip) {
    return null;
  }

  @Override
  protected BufferedSourceWithSize cache(final File file, final String etag,
                                         final boolean compress, final boolean gzip) {
    return null;
  }

  @Override
  protected BufferedSourceWithSize cache(final File file, final String etag,
                                         final long start, final long end,
                                         final boolean compress, final boolean gzip) {
    return cache(file, etag, compress, gzip);
  }

}
