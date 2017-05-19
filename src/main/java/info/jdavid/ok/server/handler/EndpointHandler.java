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


public class EndpointHandler implements Handler {

  final Map<String, Map<String, Map.Entry<Pattern, Resolver<? extends Resource>>>> resolvers =
    new HashMap<String, Map<String, Map.Entry<Pattern, Resolver<? extends Resource>>>>(6);

  @Override
  public Handler setup() {
    return this;
  }

  @Nullable
  @Override
  public String[] matches(final String method, final HttpUrl url) {
    final Map<String, Map.Entry<Pattern, Resolver<? extends Resource>>> resolvers =
      this.resolvers.get(method);
    if (resolvers.size() > 0) {
      final String path = url.encodedPath();
      for (final Map.Entry<String, Map.Entry<Pattern, Resolver<? extends Resource>>> entry :
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
    final Resolver<? extends Resource> resolver = resolvers.get(request.method).get(params[0]).getValue();
    final String[] shifted = new String[params.length - 1];
    System.arraycopy(params, 1, shifted, 0, shifted.length);
    return resolver.resolve(shifted).response(request);
  }

  public EndpointHandler head(final String regex, final Resolver<? extends Resource> resolver) {
    add("HEAD", regex, resolver);
    return this;
  }

  public EndpointHandler head(final String regex, final Resource resource) {
    add("HEAD", regex, new SingleResourceResolver(resource));
    return this;
  }

  public EndpointHandler get(final String regex, final Resolver<? extends Resource> resolver) {
    add("GET", regex, resolver);
    return this;
  }

  public EndpointHandler get(final String regex, final Resource resource) {
    add("GET", regex, new SingleResourceResolver(resource));
    return this;
  }

  public EndpointHandler post(final String regex, final Resolver<? extends Resource> resolver) {
    add("POST", regex, resolver);
    return this;
  }

  public EndpointHandler post(final String regex, final Resource resource) {
    add("POST", regex, new SingleResourceResolver(resource));
    return this;
  }

  public EndpointHandler put(final String regex, final Resolver<? extends Resource> resolver) {
    add("PUT", regex, resolver);
    return this;
  }

  public EndpointHandler put(final String regex, final Resource resource) {
    add("PUT", regex, new SingleResourceResolver(resource));
    return this;
  }

  public EndpointHandler delete(final String regex, final Resolver<? extends Resource> resolver) {
    add("DELETE", regex, resolver);
    return this;
  }

  public EndpointHandler delete(final String regex, final Resource resource) {
    add("DELETE", regex, new SingleResourceResolver(resource));
    return this;
  }

  public EndpointHandler patch(final String regex, final Resolver<? extends Resource> resolver) {
    add("PATCH", regex, resolver);
    return this;
  }

  public EndpointHandler patch(final String regex, final Resource resource) {
    add("PATCH", regex, new SingleResourceResolver(resource));
    return this;
  }

  public EndpointHandler options(final String regex, final Resolver<? extends Resource> resolver) {
    add("OPTIONS", regex, resolver);
    return this;
  }

  public EndpointHandler options(final String regex, final Resource resource) {
    add("OPTIONS", regex, new SingleResourceResolver(resource));
    return this;
  }


  protected void add(final String method, final String regex, final Resolver<? extends Resource> resolver) {
    Map<String, Map.Entry<Pattern, Resolver<? extends Resource>>> map = resolvers.get(method);
    if (map == null) {
      map = new LinkedHashMap<String, Map.Entry<Pattern, Resolver<? extends Resource>>>();
      resolvers.put(method, map);
    }
    map.put(
      UUID.randomUUID().toString(),
      new AbstractMap.SimpleEntry<Pattern, Resolver<? extends Resource>>(
        Pattern.compile(regex),
        resolver
      )
    );
  }

  public static interface Resolver<T extends Resource> {

    public T resolve(final String[] params);

  }

  public static interface Resource {

    public Response.Builder response(final Request request);

  }

  static class SingleResourceResolver implements Resolver<Resource> {

    public final Resource resource;

    public SingleResourceResolver(final Resource resource) {
      this.resource = resource;
    }

    @Override
    public Resource resolve(final String[] params) {
      return resource;
    }

  }

}
