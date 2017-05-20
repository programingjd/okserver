package info.jdavid.ok.server.handler;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import info.jdavid.ok.server.Response;
import okhttp3.HttpUrl;


/**
 * Handler for implementing api endpoints.
 */
@SuppressWarnings("WeakerAccess")
public class EndpointHandler implements Handler {

  final Map<String, Map<String, Map.Entry<Pattern, Resolver<? extends ResourceAction>>>> resolvers =
    new HashMap<String, Map<String, Map.Entry<Pattern, Resolver<? extends ResourceAction>>>>(6);

  @Override
  public Handler setup() {
    return this;
  }

  @Nullable
  @Override
  public String[] matches(final String method, final HttpUrl url) {
    final Map<String, Map.Entry<Pattern, Resolver<? extends ResourceAction>>> resolvers =
      this.resolvers.get(method);
    if (resolvers.size() > 0) {
      final String path = url.encodedPath();
      for (final Map.Entry<String, Map.Entry<Pattern, Resolver<? extends ResourceAction>>> entry :
           resolvers.entrySet()) {
        final Matcher matcher = entry.getValue().getKey().matcher(path);
        if (matcher.find() && matcher.start() == 0 && matcher.end() == path.length()) {
          final String[] params = new String[matcher.groupCount() + 1];
          params[0] = entry.getKey();
          for (int i=1; i<params.length; ++i) {
            params[i] = matcher.group(i);
          }
          return params;
        }
      }
    }
    return null;
  }

  @Override
  public final Response.Builder handle(final Request request, final String[] params) {
    final Resolver<? extends ResourceAction> resolver = resolvers.get(request.method).get(params[0]).getValue();
    final String[] shifted = new String[params.length - 1];
    System.arraycopy(params, 1, shifted, 0, shifted.length);
    return resolver.resolve(shifted).response(request);
  }

  /**
   * Adds a HEAD request handler for the endpoint with the route matching the specified regular expression.
   * The regular expression should capture the parameters necessary for the resolver to decide which
   * mResourceAction to use for handling a request.
   * @param regex the regex for the route.
   * @param resolver the resolver.
   * @return this.
   */
  public EndpointHandler head(final String regex, final Resolver<? extends ResourceAction> resolver) {
    add("HEAD", regex, resolver);
    return this;
  }

  /**
   * Adds a HEAD request handler for the endpoint with the route matching the specified regular expression.
   * @param regex the regex for the route.
   * @param resourceAction the resourceAction that will handle requests.
   * @return this.
   */
  public EndpointHandler head(final String regex, final ResourceAction resourceAction) {
    add("HEAD", regex, new SingleResourceResolver(resourceAction));
    return this;
  }

  /**
   * Adds a GET request handler for the endpoint with the route matching the specified regular expression.
   * The regular expression should capture the parameters necessary for the resolver to decide which
   * mResourceAction to use for handling a request.
   * @param regex the regex for the route.
   * @param resolver the resolver.
   * @return this.
   */
  public EndpointHandler get(final String regex, final Resolver<? extends ResourceAction> resolver) {
    add("GET", regex, resolver);
    return this;
  }

  /**
   * Adds a GET request handler for the endpoint with the route matching the specified regular expression.
   * @param regex the regex for the route.
   * @param resourceAction the resourceAction that will handle requests.
   * @return this.
   */
  public EndpointHandler get(final String regex, final ResourceAction resourceAction) {
    add("GET", regex, new SingleResourceResolver(resourceAction));
    return this;
  }

  /**
   * Adds a POST request handler for the endpoint with the route matching the specified regular expression.
   * The regular expression should capture the parameters necessary for the resolver to decide which
   * mResourceAction to use for handling a request.
   * @param regex the regex for the route.
   * @param resolver the resolver.
   * @return this.
   */
  public EndpointHandler post(final String regex, final Resolver<? extends ResourceAction> resolver) {
    add("POST", regex, resolver);
    return this;
  }

  /**
   * Adds a POST request handler for the endpoint with the route matching the specified regular expression.
   * @param regex the regex for the route.
   * @param resourceAction the resourceAction that will handle requests.
   * @return this.
   */
  public EndpointHandler post(final String regex, final ResourceAction resourceAction) {
    add("POST", regex, new SingleResourceResolver(resourceAction));
    return this;
  }

  /**
   * Adds a PUT request handler for the endpoint with the route matching the specified regular expression.
   * The regular expression should capture the parameters necessary for the resolver to decide which
   * mResourceAction to use for handling a request.
   * @param regex the regex for the route.
   * @param resolver the resolver.
   * @return this.
   */
  public EndpointHandler put(final String regex, final Resolver<? extends ResourceAction> resolver) {
    add("PUT", regex, resolver);
    return this;
  }

  /**
   * Adds a PUT request handler for the endpoint with the route matching the specified regular expression.
   * @param regex the regex for the route.
   * @param resourceAction the resourceAction that will handle requests.
   * @return this.
   */
  public EndpointHandler put(final String regex, final ResourceAction resourceAction) {
    add("PUT", regex, new SingleResourceResolver(resourceAction));
    return this;
  }

  /**
   * Adds a DELETE request handler for the endpoint with the route matching the specified regular expression.
   * The regular expression should capture the parameters necessary for the resolver to decide which
   * mResourceAction to use for handling a request.
   * @param regex the regex for the route.
   * @param resolver the resolver.
   * @return this.
   */
  public EndpointHandler delete(final String regex, final Resolver<? extends ResourceAction> resolver) {
    add("DELETE", regex, resolver);
    return this;
  }

  /**
   * Adds a DELETE request handler for the endpoint with the route matching the specified regular expression.
   * @param regex the regex for the route.
   * @param resourceAction the resourceAction that will handle requests.
   * @return this.
   */
  public EndpointHandler delete(final String regex, final ResourceAction resourceAction) {
    add("DELETE", regex, new SingleResourceResolver(resourceAction));
    return this;
  }

  /**
   * Adds a PATCH request handler for the endpoint with the route matching the specified regular expression.
   * The regular expression should capture the parameters necessary for the resolver to decide which
   * mResourceAction to use for handling a request.
   * @param regex the regex for the route.
   * @param resolver the resolver.
   * @return this.
   */
  public EndpointHandler patch(final String regex, final Resolver<? extends ResourceAction> resolver) {
    add("PATCH", regex, resolver);
    return this;
  }

  /**
   * Adds a PATCH request handler for the endpoint with the route matching the specified regular expression.
   * @param regex the regex for the route.
   * @param resourceAction the resourceAction that will handle requests.
   * @return this.
   */
  public EndpointHandler patch(final String regex, final ResourceAction resourceAction) {
    add("PATCH", regex, new SingleResourceResolver(resourceAction));
    return this;
  }

  /**
   * Adds a OPTIONS request handler for the endpoint with the route matching the specified regular expression.
   * The regular expression should capture the parameters necessary for the resolver to decide which
   * mResourceAction to use for handling a request.
   * @param regex the regex for the route.
   * @param resolver the resolver.
   * @return this.
   */
  public EndpointHandler options(final String regex, final Resolver<? extends ResourceAction> resolver) {
    add("OPTIONS", regex, resolver);
    return this;
  }

  /**
   * Adds a OPTIONS request handler for the endpoint with the route matching the specified regular expression.
   * @param regex the regex for the route.
   * @param resourceAction the resourceAction that will handle requests.
   * @return this.
   */
  public EndpointHandler options(final String regex, final ResourceAction resourceAction) {
    add("OPTIONS", regex, new SingleResourceResolver(resourceAction));
    return this;
  }

  /**
   * Adds a resolver for the specified method with the specified regex for the route.
   * @param method the request method for the endpoint.
   * @param regex the regular expression for the endpoint route.
   * @param resolver the resolver.
   */
  protected void add(final String method, final String regex, final Resolver<? extends ResourceAction> resolver) {
    Map<String, Map.Entry<Pattern, Resolver<? extends ResourceAction>>> map = resolvers.get(method);
    if (map == null) {
      map = new LinkedHashMap<String, Map.Entry<Pattern, Resolver<? extends ResourceAction>>>();
      resolvers.put(method, map);
    }
    map.put(
      UUID.randomUUID().toString(),
      new AbstractMap.SimpleEntry<Pattern, Resolver<? extends ResourceAction>>(
        Pattern.compile(regex),
        resolver
      )
    );
  }

  /**
   * Resolver class responsible for returning the mResourceAction associated with the request based
   * on the params extracted from the url.
   * @param <T> the mResourceAction type.
   */
  public static interface Resolver<T extends ResourceAction> {

    /**
     * Returns the mResourceAction that will handle the request.
     * @param params the url params (captured groups from the regex).
     * @return the mResourceAction.
     */
    public T resolve(final String[] params);

  }

  /**
   * ResourceAction class responsible for handling the request.
   */
  public static interface ResourceAction {

    /**
     * Handles the request and returns the response.
     * @param request the request.
     * @return the response (builder).
     */
    public Response.Builder response(final Request request);

  }

  static class SingleResourceResolver implements Resolver<ResourceAction> {

    public final ResourceAction mResourceAction;

    public SingleResourceResolver(final ResourceAction resourceAction) {
      this.mResourceAction = resourceAction;
    }

    @Override
    public ResourceAction resolve(final String[] params) {
      return mResourceAction;
    }

  }

}
