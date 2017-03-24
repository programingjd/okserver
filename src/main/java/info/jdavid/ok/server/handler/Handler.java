package info.jdavid.ok.server.handler;

import info.jdavid.ok.server.Response;
import okhttp3.HttpUrl;


/**
 * Chainable request handler that can accept a request or not.
 */
public interface Handler {

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
