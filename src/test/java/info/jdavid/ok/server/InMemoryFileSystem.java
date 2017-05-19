package info.jdavid.ok.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import okhttp3.internal.io.FileSystem;
import okio.Buffer;
import okio.Sink;
import okio.Source;


public class InMemoryFileSystem implements FileSystem {

  private Map<String, InMemoryFile> mInMemoryFiles = new HashMap<String, InMemoryFile>();

  private static class InMemoryFile {
    private Buffer buffer = new Buffer();
  }

  private InMemoryFile getOrCreate(final File file) {
    final InMemoryFile inMemoryFile = mInMemoryFiles.get(file.getAbsolutePath());
    if (inMemoryFile == null) {
      final InMemoryFile created = new InMemoryFile();
      mInMemoryFiles.put(file.getAbsolutePath(), created);
      return created;
    }
    else {
      return inMemoryFile;
    }
  }


  @Override
  public Source source(final File file) throws FileNotFoundException {
    final InMemoryFile inMemoryFile = mInMemoryFiles.get(file.getAbsolutePath());
    if (inMemoryFile == null) throw new FileNotFoundException();
    return inMemoryFile.buffer.clone();
  }

  @Override
  public Sink sink(final File file) throws FileNotFoundException {
    final InMemoryFile inMemoryFile = getOrCreate(file);
    final Buffer buffer = inMemoryFile.buffer;
    buffer.readByteArray();
    return buffer;
  }

  @Override
  public Sink appendingSink(final File file) throws FileNotFoundException {
    final InMemoryFile inMemoryFile = getOrCreate(file);
    return inMemoryFile.buffer;
  }

  @Override
  public void delete(final File file) throws IOException {
    final InMemoryFile inMemoryFile = mInMemoryFiles.remove(file.getAbsolutePath());
    if (inMemoryFile != null) {
      inMemoryFile.buffer.close();
    }
  }

  @Override
  public boolean exists(final File file) {
    return mInMemoryFiles.get(file.getAbsolutePath()) != null;
  }

  @Override
  public long size(final File file) {
    final InMemoryFile inMemoryFile = mInMemoryFiles.get(file.getAbsolutePath());
    return inMemoryFile == null ? 0 : inMemoryFile.buffer.size();
  }

  @Override
  public void rename(final File from, final File to) throws IOException {
    final InMemoryFile inMemoryFile = mInMemoryFiles.remove(from.getAbsolutePath());
    if (inMemoryFile != null) {
      mInMemoryFiles.put(to.getAbsolutePath(), inMemoryFile);
    }
  }

  @Override
  public void deleteContents(final File directory) throws IOException {
    final String path = directory.getAbsolutePath();
    final Iterator<Map.Entry<String, InMemoryFile>> iterator = mInMemoryFiles.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<String, InMemoryFile> entry = iterator.next();
      if (entry.getKey().startsWith(path)) {
        iterator.remove();
        entry.getValue().buffer.close();
      }
    }
  }

}
