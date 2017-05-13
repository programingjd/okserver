package info.jdavid.ok.server.handler;

import java.io.File;
import java.util.Map;

import info.jdavid.ok.server.Response;
import okhttp3.HttpUrl;

public class AcmeChallengeHandler implements Handler {

  public AcmeChallengeHandler(final File acmeDirectory) {}

  public AcmeChallengeHandler(final Map<String, File> domainAcmeDirectories) {

  }

  @Override
  public Handler setup() {
    return this;
  }

  @Override
  public String[] matches(final String method, final HttpUrl url) {
    return new String[0];
  }

  @Override
  public Response.Builder handle(final Request request, final String[] params) {
    return null;
  }

}
