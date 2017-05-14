package info.jdavid.ok.server.handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import okhttp3.HttpUrl;
import okio.Okio;


@SuppressWarnings({ "WeakerAccess" })
public class AcmeChallengeHandler implements Handler {

  final File directory;
  final Map<String, File> domainDirectories;

  public AcmeChallengeHandler(final File acmeDirectory) {
    directory = acmeDirectory;
    domainDirectories = null;
  }

  public AcmeChallengeHandler(final Map<String, File> domainAcmeDirectories) {
    if (domainAcmeDirectories.isEmpty()) throw new IllegalArgumentException();
    directory = null;
    domainDirectories = domainAcmeDirectories;
  }

  @Override
  public Handler setup() {
    return this;
  }

  @Override
  public @Nullable String[] matches(final String method, final HttpUrl url) {
    final List<String> segments = url.pathSegments();
    return segments.toArray(new String[segments.size()]);
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    //noinspection ConstantConditions
    final File dir = directory == null ? domainDirectories.get(request.url.host()) : directory;
    if (dir == null) {
      return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
    }
    File file = dir;
    for (final String segment: params) {
      file = new File(file, segment);
      if (!file.exists()) return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
    }
    if (!file.isFile()) return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
    try {
      return new Response.Builder().
        statusLine(StatusLines.OK).
        body(Okio.buffer(Okio.source(file)), file.length());
    }
    catch (final FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

}
