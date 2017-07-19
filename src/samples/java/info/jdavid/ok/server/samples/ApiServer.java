package info.jdavid.ok.server.samples;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import info.jdavid.ok.json.Builder;
import info.jdavid.ok.json.Parser;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.MediaTypes;
import info.jdavid.ok.server.RequestHandlerChain;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.StatusLines;
import info.jdavid.ok.server.handler.EndpointHandler;
import info.jdavid.ok.server.handler.Request;
import okio.Buffer;


@SuppressWarnings("WeakerAccess")
public class ApiServer {

  final HttpServer mServer;

  final AtomicInteger nextId = new AtomicInteger();

  final ConcurrentHashMap<String, Item> resources = new ConcurrentHashMap<>();

  @SuppressWarnings("Convert2Lambda")
  public ApiServer() {
    //noinspection Duplicates
    resources.put("item" +  nextId.incrementAndGet(), new Item("test1"));
    resources.put("item" +  nextId.incrementAndGet(), new Item("test2"));
    mServer = new HttpServer().
      requestHandler(
        new RequestHandlerChain().
          add(
            new EndpointHandler().
              get("/resources/?", new EndpointHandler.Resolver<EndpointHandler.ResourceAction>() {
                @Override public EndpointHandler.ResourceAction resolve(final String[] params) {
                  return new ItemCollection();
                }
              }).
              head("/resources/([^/]+)", new EndpointHandler.Resolver<EndpointHandler.ResourceAction>() {
                @Override public EndpointHandler.ResourceAction resolve(final String[] params) {
                  return new HeadItem(params[0]);
                }
              }).
              get("/resources/([^/]+)", new EndpointHandler.Resolver<EndpointHandler.ResourceAction>() {
                @Override public EndpointHandler.ResourceAction resolve(final String[] params) {
                  return new GetItem(params[0]);
                }
              }).
              delete("/resources/([^/]+)", new EndpointHandler.Resolver<EndpointHandler.ResourceAction>() {
                @Override public EndpointHandler.ResourceAction resolve(final String[] params) {
                  return new DeleteItem(params[0]);
                }
              }).
              post("/resources/?", new EndpointHandler.Resolver<EndpointHandler.ResourceAction>() {
                @Override public EndpointHandler.ResourceAction resolve(final String[] params) {
                  return new PostItem();
                }
              }).
              put("/resources/([^/]+)", new EndpointHandler.Resolver<EndpointHandler.ResourceAction>() {
                @Override public EndpointHandler.ResourceAction resolve(final String[] params) {
                  return new PutItem(params[0]);
                }
              })
//              get("/resources/?", (final String[] params) -> new ItemCollection()).
//              head("/resources/([^/]+)", (final String[] params) -> new HeadItem(params[0])).
//              get("/resources/([^/]+)", (final String[] params) -> new GetItem(params[0])).
//              delete("/resources/([^/]+)", (final String[] params) -> new DeleteItem(params[0])).
//              post("/resources/?", (final String[] params) -> new PostItem()).
//              put("/resources/([^/]+)", (final String[] params) -> new PutItem(params[0]))
          )
      ).
      port(8080);
  }

  class ItemCollection implements EndpointHandler.ResourceAction {

    @Override
    public Response.Builder response(final Request request) {
      final Buffer buffer = new Buffer();
      Builder.build(buffer, resources);
      return new Response.Builder().
        statusLine(StatusLines.OK).
        body(MediaTypes.JSON, buffer);
    }

  }

  class HeadItem implements EndpointHandler.ResourceAction {

    private final boolean exists;

    HeadItem(final String name) {
      exists = resources.get(name) != null;
    }

    @Override
    public Response.Builder response(final Request request) {
      return new Response.Builder().
        statusLine(exists ? StatusLines.OK : StatusLines.NOT_FOUND);
    }

  }

  class GetItem implements EndpointHandler.ResourceAction {

    private final Item item;

    GetItem(final String name) {
      item = resources.get(name);
    }

    @Override
    public Response.Builder response(final Request request) {
      if (item == null) {
        return new Response.Builder().
          statusLine(StatusLines.NOT_FOUND);
      }
      else {
        final Buffer buffer = new Buffer();
        Builder.build(buffer, item);
        return new Response.Builder().
          statusLine(StatusLines.OK).
          body(MediaTypes.JSON, buffer);
      }
    }

  }

  class DeleteItem implements EndpointHandler.ResourceAction {

    private final boolean removed;

    DeleteItem(final String name) {
      removed = resources.remove(name) != null;
    }

    @Override
    public Response.Builder response(final Request request) {
      return new Response.Builder().
        statusLine(removed ? StatusLines.NO_CONTENT : StatusLines.NOT_FOUND);
    }

  }

  class PostItem implements EndpointHandler.ResourceAction {

    @Override
    public Response.Builder response(final Request request) {
      try {
        final Map<String, ?> json = Parser.parse(request.body);
        //noinspection ConstantConditions
        final String name = (String)json.get(Item.NAME);
        if (name == null) throw new NullPointerException();
        final Item item = new Item(name);
        resources.put("item" + nextId.incrementAndGet(), item);
        final Buffer buffer = new Buffer();
        Builder.build(buffer, item);
        return new Response.Builder().
          statusLine(StatusLines.OK).
          body(MediaTypes.JSON, buffer);
      }
      catch (final Exception ignore) {
        return new Response.Builder().
          statusLine(StatusLines.BAD_REQUEST);
      }
    }

  }

  class PutItem implements EndpointHandler.ResourceAction {

    private final Item item;

    PutItem(final String name) {
      item = resources.get(name);
    }

    @Override
    public Response.Builder response(final Request request) {
      if (item == null) {
        return new Response.Builder().
          statusLine(StatusLines.NOT_FOUND);
      }
      else {
        try {
          final Map<String, ?> json = Parser.parse(request.body);
          //noinspection ConstantConditions
          final String name = (String)json.get(Item.NAME);
          if (name == null) throw new NullPointerException();
          item.put(Item.NAME, name);
          return new Response.Builder().
            statusLine(StatusLines.NO_CONTENT);
        }
        catch (final Exception ignore) {
          return new Response.Builder().
            statusLine(StatusLines.BAD_REQUEST);
        }
      }
    }

  }

  static class Item extends AbstractMap<String, String> {

    public static final String NAME = "name";

    Entry<String, String> entry;

    public Item(final String name) {
      entry = new SimpleEntry<>(NAME, name);
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Set<Entry<String, String>> entrySet() {
      final Set<Entry<String, String>> set = new HashSet<>(1);
      set.add(entry);
      return set;
    }

    @Override
    public String put(final String key, final String value) {
      if (!NAME.equals(key)) throw new UnsupportedOperationException();
      final String previous = entry.getValue();
      entry.setValue(value);
      return previous;
    }

  }

  public void start() {
    mServer.start();
  }

  @SuppressWarnings("unused")
  public void stop() {
    mServer.shutdown();
  }


  public static void main(final String[] args) {
    new ApiServer().start();
  }

}
