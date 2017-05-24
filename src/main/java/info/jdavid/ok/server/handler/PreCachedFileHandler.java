package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

import okhttp3.MediaType;
import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;
import okio.Okio;


/**
 * Extension of FileHandler that precaches all files.
 */
@SuppressWarnings({ "WeakerAccess" })
public class PreCachedFileHandler extends FileHandler {

  private static final String UTF8 = "UTF-8";

  String etagPrefix = null;

  final Map<String, Data> cache =
    new LinkedHashMap<String, Data>(4096, 0.75f, true);

  /**
   * Creates a new file handler that will accept all requests.
   * All files under the web root whose media type is allowed are cached during setup.
   * @param webRoot the web root directory containing the files.
   */
  public PreCachedFileHandler(final File webRoot) {
    super(webRoot);
  }

  /**
   * Creates a new file handler that will accept all requests.
   * All files under the web root whose media type is allowed are cached during setup.
   * @param webRoot the web root directory containing the files.
   * @param indexNames the names for the index files (defaults to "index.html" and "index.htm").
   */
  public PreCachedFileHandler(final File webRoot, final List<String> indexNames) {
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
  public PreCachedFileHandler(final String regex, final File webRoot) {
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
  public PreCachedFileHandler(final String regex, final File webRoot, final List<String> indexNames) {
    super(regex, webRoot, indexNames);
  }

  @Override
  public Handler setup() {
    super.setup();
    etagPrefix = etagPrefix(webRoot);
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
        if (mediaType != null && acceptMediaType(mediaType)) {
          if (acceptFile(current)) {
            final boolean compress = config(mediaType).compress;
            cache(current, etag(current, webRoot), config(mediaType).compress, compress);
          }
        }
      }
    }
    return this;
  }

  protected String etagPrefix(final File webRoot) {
    try {
      return Hex.hex(Md5.md5(webRoot.getCanonicalPath().getBytes(UTF8)));
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected boolean acceptDirectory(final File directory) {
    return directory.getName().charAt(0) != '.';
  }

  protected boolean acceptMediaType(final MediaType mediaType) {
    return isAllowed(mediaType);
  }

  @SuppressWarnings("unused")
  protected boolean acceptFile(final File file) {
    return true;
  }

  @Override
  protected final String etag(final File file, final File webRoot) {
    try {
      final String relativePath = relativePath(file);
      final byte[] pathBytes = relativePath.getBytes(UTF8);
      final String prefix = etagPrefix;
      return prefix + Hex.hex(pathBytes) + Hex.hex(BigInteger.valueOf(file.lastModified()));
    }
    catch (final IOException ignore) {
      return null;
    }
  }

  final String relativePath(final String etag) {
    final String prefix = etagPrefix;
    final String pathPortion = etag.substring(prefix.length(), etag.length() - 12);
    try {
      return new String(Hex.unhex(pathPortion), UTF8);
    }
    catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  final String relativePath(final File file) throws IOException {
    final String filePath = file.getCanonicalPath();
    final String rootPath = webRoot.getCanonicalPath();
    if (!filePath.startsWith(rootPath)) throw new RuntimeException();
    return filePath.substring(rootPath.length()).replace('\\','/');
  }

  @Override
  protected BufferedSourceWithSize fromCache(final File file, @Nullable final String etag,
                                             final boolean compress, final boolean gzip) {
    if (etag == null) return null;
    final String relativePath = relativePath(etag);
    final Data data = cache.get(relativePath);
    if (data != null) {
      final byte[] bytes;
      final Lock lock = data.lock.readLock();
      lock.lock();
      try {
        bytes = etag.equals(data.etag) ? data.bytes : null;
      }
      finally {
        lock.unlock();
      }
      if (bytes != null) {
        final Buffer buffer = new Buffer();
        if (gzip == data.compressed) buffer.write(bytes);
        else if (data.compressed) return decompress(bytes);
        else throw new RuntimeException();
        return new BufferedSourceWithSize(buffer, buffer.size());
      }
    }
    return null;
  }

  @Override
  protected BufferedSourceWithSize fromCache(final File file, @Nullable final String etag,
                                             final long start, final long end,
                                             final boolean compress, final boolean gzip) {
    if (compress == gzip) {
      if (etag == null) return null;
      final String relativePath = relativePath(etag);
      final Data data = cache.get(relativePath);
      if (data != null) {
        final byte[] bytes;
        final Lock lock = data.lock.readLock();
        lock.lock();
        try {
          bytes = etag.equals(data.etag) ? data.bytes : null;
        }
        finally {
          lock.unlock();
        }
        if (bytes != null) {
          final Buffer buffer = new Buffer();
          buffer.write(bytes, (int)start, (int)(end - start));
          return new BufferedSourceWithSize(buffer, buffer.size());
        }
      }
    }
    return null;
  }

  @Override
  protected BufferedSourceWithSize cache(final File file, @Nullable final String etag,
                                         final boolean compress, final boolean gzip) {
    if (etag == null) return null;
    final String relativePath = relativePath(etag);
    final Data data = cache.get(relativePath);
    final byte[] bytes = bytes(file, relativePath, etag, data, compress);
    if (bytes == null) return null;
    if (gzip == compress) {
      final Buffer buffer = new Buffer();
      buffer.write(bytes);
      return new BufferedSourceWithSize(buffer, buffer.size());
    }
    else if (compress) {
      return decompress(bytes);
    }
    else {
      throw new RuntimeException();
    }
  }

  @Override
  protected BufferedSourceWithSize cache(final File file, @Nullable final String etag,
                                         final long start, final long end,
                                         final boolean compress, final boolean gzip) {
    if (etag == null) return null;
    final String relativePath = relativePath(etag);
    final Data data = cache.get(relativePath);
    final byte[] bytes = bytes(file, relativePath, etag, data, compress);
    if (bytes == null) return null;
    if (gzip == compress) {
      final Buffer buffer = new Buffer();
      buffer.write(bytes, (int)start, (int)(end - start));
      return new BufferedSourceWithSize(buffer, buffer.size());
    }
    else {
      if (compress) {
        return null;
      }
      else {
        throw new RuntimeException();
      }
    }
  }

  private static BufferedSourceWithSize decompress(final byte[] bytes) {
    final Buffer buffer = new Buffer();
    final BufferedSource source = Okio.buffer(new GzipSource(new Buffer().write(bytes)));
    try {
      buffer.writeAll(source);
    }
    catch (final IOException ignore) {
      return null;
    }
    finally {
      try {
        source.close();
      }
      catch (final IOException ignore) {}
    }
    return new BufferedSourceWithSize(buffer, buffer.size());
  }

  private static byte[] bytes(final File file, final boolean compress) throws IOException {
    final BufferedSource buffered;
    if (compress) {
      buffered = Okio.buffer(new CompressedSource(Okio.source(file), true));
    }
    else {
      buffered = Okio.buffer(Okio.source(file));
    }
    try {
      return buffered.readByteArray();
    }
    finally {
      buffered.close();
    }
  }

  private byte[] bytes(final File file, final String relativePath, final String etag,
                       @Nullable final Data data, final boolean compress) {
    final byte[] bytes;
    if (data == null) {
      try {
        final Data d = new Data(file, etag, compress);
        bytes = d.bytes;
        cache.put(relativePath, d);
      }
      catch (final IOException ignore) {
        return null;
      }
    }
    else {
      try {
        bytes = bytes(file, compress);
        final Lock lock = data.lock.writeLock();
        lock.lock();
        try {
          data.etag = etag;
          data.bytes = bytes;
        }
        finally {
          lock.unlock();
        }
      }
      catch (final IOException ignore) {
        return null;
      }
    }
    return bytes;
  }

  static class Data {
    final boolean compressed;
    String etag;
    byte[] bytes;
    final ReadWriteLock lock = new ReentrantReadWriteLock();

    public Data(final File file, final String etag, final boolean compress) throws IOException {
      this.etag = etag;
      this.compressed = compress;
      this.bytes = bytes(file, compress);
    }

  }

}
