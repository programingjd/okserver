package info.jdavid.ok.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import info.jdavid.ok.server.handler.AcmeChallengeHandler;
import info.jdavid.ok.server.handler.FileHandler;
import info.jdavid.ok.server.handler.Handler;
import info.jdavid.ok.server.handler.Request;
import info.jdavid.ok.server.header.Preload;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;


/**
 * A RequestHandler that uses a chain of Handlers to try to satisfy a request. The handlers are tried one by
 * one until one of them accepts the request.
 */
@SuppressWarnings({ "WeakerAccess" })
public class RequestHandlerChain extends AbstractRequestHandler {

  public static void main(final String[] args) {
    cmd(args);
  }

  @SuppressWarnings("UnusedReturnValue")
  static HttpServer cmd(final String[] args) {
    //final String[] args = new String[] { "--root", "i:/jdavid/pierreblanche.bitbucket.org" };
    final Map<String, String> map = new HashMap<String, String>(args.length);
    String key = null;
    String value = null;
    for (final String arg: args) {
      if (arg.startsWith("--")) {
        if (key != null) {
          map.put(key, value);
        }
        key = arg.substring(2);
        value = null;
      }
      else {
        value = value == null ? arg : value + " " + arg;
      }
    }
    if (key != null) {
      map.put(key, value);
    }
    if (map.containsKey("help")) {
      usage();
    }
    else {
      final HttpServer server = new HttpServer();
      if (map.containsKey("port")) {
        try {
          final int port = Integer.parseInt(map.get("port"));
          server.port(port);
        }
        catch (final NullPointerException ignore) {
          usage();
        }
        catch (final NumberFormatException ignore) {
          usage();
        }
      }
      if (map.containsKey("sport")) {
        try {
          final int port = Integer.parseInt(map.get("sport"));
          server.securePort(port);
        }
        catch (final NullPointerException ignore) {
          usage();
        }
        catch (final NumberFormatException ignore) {
          usage();
        }
      }
      if (map.containsKey("hostname")) {
        final String hostname = map.get("hostname");
        if (hostname == null) {
          usage();
        }
        else {
          server.hostname(hostname);
        }
      }
      if (map.containsKey("cert")) {
        final String path = map.get("cert");
        if (path == null) {
          usage();
        }
        else {
          BufferedSource source = null;
          try {
            source = Okio.buffer(Okio.source(new File(path)));
            final byte[] cert = source.readByteArray();
            server.https(new Https.Builder().certificate(cert, true).build());
          }
          catch (final FileNotFoundException ignore) {
            System.out.println("Could not find certificate: \"" + path + "\".");
          }
          catch (final IOException ignore) {
            System.out.println("Could not read certificate: \"" + path + "\".");
          }
          finally {
            if (source != null) {
              try {
                source.close();
              }
              catch (final IOException ignore) {}
            }
          }
        }
      }
      final File root;
      if (map.containsKey("root")) {
        final String path = map.get("root");
        if (path == null) {
          root = new File(".");
        }
        else {
          root = new File(path);
        }
      }
      else {
        root = new File(".");
      }
      server.requestHandler(new RequestHandlerChain().add(new FileHandler(root)));
      server.start();
      return server;
    }
    return null;
  }

  private static void usage() {
    System.out.println("Usage:\nAll parameters are optional.");
    System.out.println(pad("--port portnumber", 30) +
                       "the port to bind to for http (insecure) connections.");
    System.out.println(pad("--sport portnumber", 30) +
                       "the port to bind to for https (secure) connections.");
    System.out.println(pad("--cert path/to/cert.p12", 30) +
                       "the path to the p12 certificate for https.");
    System.out.println(pad("--hostname hostname", 30) +
                       "the hostname to bind to.");
    System.out.println(pad("--root path/to/webroot", 30) +
                       "the path to the web root directory.");
    System.out.println(pad("--help", 30) +
                       "prints this help.");
  }

  private static String pad(final String s, final int n) {
    final String tenSpaces = "          ";
    final StringBuilder builder = new StringBuilder(s);
    while (builder.length() < n) {
      builder.append(tenSpaces);
    }
    return builder.substring(0, n);
  }

  final Handler acmeHandler;
  final List<Handler> chain = new LinkedList<Handler>();

  /**
   * Creates the default chain: a file handler serving the current directory.
   * @return the default request handler chain.
   */
  public static RequestHandlerChain createDefaultChain() {
    return createDefaultChain(new File("."));
  }

  /**
   * Creates te default chain: a file handler serving the specified directory.
   * @param webRoot the directory to serve.
   * @return the default request handler chain.
   */
  public static RequestHandlerChain createDefaultChain(final File webRoot) {
    if (webRoot.isFile()) throw new IllegalArgumentException("Web root should be a directory.");
    final File directory;
    try {
      directory = webRoot.getCanonicalFile();
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return new RequestHandlerChain(new AcmeChallengeHandler(directory)).
      add(new FileHandler(directory));
  }

  /**
   * Creates a new empty request handler chain.
   */
  public RequestHandlerChain() {
    acmeHandler = null;
  }

  /**
   * Creates a new empty request handler chain with the specified handler for acme challenges.
   * @param acmeChallengeHandler the acme challenge handler.
   */
  public RequestHandlerChain(final Handler acmeChallengeHandler) {
    acmeHandler = acmeChallengeHandler;
  }

  /**
   * Appends a Handler to the chain. Note that the order in which handlers are added matters, as they will be
   * tried in the order that they were added.
   * @param handler the handler.
   * @return this.
   */
  public RequestHandlerChain add(final Handler handler) {
    chain.add(handler.setup());
    return this;
  }

  @Override
  protected Response handleAcmeChallenge(final String clientIp, final String method, final HttpUrl url,
                                         final Headers requestHeaders, @Nullable final Buffer requestBody) {
    final Response.Builder responseBuilder;
    final String[] params;
    if (acmeHandler == null || (params = acmeHandler.matches(method, url)) == null) {
      responseBuilder = handleNotAccepted(clientIp, method, url, requestHeaders);
    }
    else {
      responseBuilder = acmeHandler.handle(
        new Request(clientIp, false, method, url, requestHeaders, requestBody),
        params
      );
    }
    decorateResponse(responseBuilder, clientIp, false, method, url, requestHeaders);
    return responseBuilder.build();
  }

  @Override
  protected final Response handle(final String clientIp, final boolean http2,
                                  final String method, final HttpUrl url,
                                  final Headers requestHeaders, @Nullable final Buffer requestBody) {
    for (final Handler handler: chain) {
      final String[] params = handler.matches(method, url);
      if (params != null) {
        final Response.Builder responseBuilder =
         handler.handle(new Request(clientIp, http2, method, url, requestHeaders, requestBody), params);
        decorateResponse(responseBuilder, clientIp, http2, method, url, requestHeaders);
        return responseBuilder.build();
      }
    }
    final Response.Builder responseBuilder = handleNotAccepted(clientIp, method, url, requestHeaders);
    decorateResponse(responseBuilder, clientIp, http2, method, url, requestHeaders);
    return responseBuilder.build();
  }

  /**
   * Entry point that can be used to decorate (add headers for instance) the response before sending it.
   * @param responseBuilder the response builder object.
   * @param clientIp the client ip address.
   * @param http2 whether the connection is using http2 (h2) rather than http1.1.
   * @param method the request method.
   * @param url the request url.
   * @param requestHeaders the request headers.
   */
  @SuppressWarnings({ "unused" })
  protected void decorateResponse(final Response.Builder responseBuilder,
                                  final String clientIp, final boolean http2,
                                  final String method, final HttpUrl url,
                                  final Headers requestHeaders) {
    final int code = responseBuilder.code();
    if (code >= 200 && code < 300) {
      if (http2) {
        for (final HttpUrl push: Preload.getPushUrls(responseBuilder)) {
          responseBuilder.push(push);
        }
      }
    }
  }

  /**
   * Handles the requests that were not accepted by any Handler in the chain.
   * The default behaviour is to return an empty 404 NOT FOUND response.
   * @param clientIp the client ip address.
   * @param method the request method.
   * @param url the request url.
   * @param requestHeaders the request headers.
   * @return the response builder object.
   */
  @SuppressWarnings({ "unused" })
  protected Response.Builder handleNotAccepted(final String clientIp, final String method,
                                               final HttpUrl url,final Headers requestHeaders) {
    return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody();
  }

}
