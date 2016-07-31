package info.jdavid.ok.server;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;

import okhttp3.*;
import okhttp3.internal.Util;
import okhttp3.internal.framed.FramedConnection;
import okhttp3.internal.framed.FramedStream;
import okhttp3.internal.framed.Header;
import okhttp3.internal.http.HttpMethod;
import okhttp3.internal.http.RequestLine;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;
import okio.Timeout;

import static okhttp3.internal.framed.Header.TARGET_AUTHORITY;
import static okhttp3.internal.framed.Header.TARGET_METHOD;
import static okhttp3.internal.framed.Header.TARGET_PATH;
import static okhttp3.internal.framed.Header.TARGET_SCHEME;


class Http2 {

  static void serve(final SSLSocket socket, final String hostname,
                    final long maxRequestSize,
                    final KeepAliveStrategy keepAliveStrategy,
                    final RequestHandler requestHandler) throws IOException {
    final FramedConnectionListener listener =
      new FramedConnectionListener(requestHandler, maxRequestSize);
    final BufferedSource in = Okio.buffer(Okio.source(socket));
    final BufferedSink out = Okio.buffer(Okio.sink(socket));
    final FramedConnection connection = new FramedConnection.Builder(false).
      socket(socket, hostname, in, out).
      protocol(Protocol.HTTP_2).
      listener(listener).
      build();
    try {
      connection.start();
      Timeout timeout = null;
      int counter = 0;
      while (!socket.isClosed()) {
        if (connection.openStreamCount() == 0) {
          if (timeout == null) {
            timeout = in.timeout().timeout(keepAliveStrategy.timeout(counter++), TimeUnit.SECONDS);
          }
        }
        else {
          if (timeout != null) timeout.clearTimeout();
        }
        Thread.sleep(1000L);
      }
    }
    catch (final SocketTimeoutException ignore) {}
    catch (final SocketException ignored) {}
    catch (final Exception e) {
      try { connection.close(); } catch (final Exception ignore) {}
      try { in.close(); } catch (final IOException ignore) {}
      try { out.close(); } catch (final IOException ignore) {}
      try { socket.close(); } catch (final IOException ignore) {}
      throw new IOException(e);
    }
  }

  private static final byte PSEUDO_HEADER_PREFIX = ByteString.encodeUtf8(":").getByte(0);
  private static final String IF_NONE_MATCH = "If-None-Match";

  private static List<Header> responseHeaders(final Response response) {
    final Headers headers = response.headers();
    final int size = headers.size();
    final List<Header> responseHeaders = new ArrayList<Header>(size + 1);
    responseHeaders.add(
      new Header(Header.RESPONSE_STATUS, ByteString.encodeUtf8(String.valueOf(response.code())))
    );
    for (int i=0; i<size; ++i) {
      final ByteString name = ByteString.encodeUtf8(headers.name(i));
      final ByteString value = ByteString.encodeUtf8(headers.value(i));
      responseHeaders.add(new Header(name.toAsciiLowercase(), value));
    }
    return responseHeaders;
  }

  private static String restoreHeaderNameCase(final ByteString name) {
    // name should be ascii lowercase
    final int size = name.size();
    final StringBuilder restored = new StringBuilder(size);
    char prev = '-';
    for (int i=0; i<size; ++i) {
      final char c = (char)(prev == '-' ? Character.toUpperCase((int)name.getByte(i)) : name.getByte(i));
      restored.append(c);
      prev = c;
    }
    return restored.toString();
  }

  private static class FramedConnectionListener extends FramedConnection.Listener {

    private final RequestHandler handler;
    private final long max;

    FramedConnectionListener(final RequestHandler requestHandler, final long maxRequestSize) {
      handler = requestHandler;
      max = maxRequestSize;
    }

    @Override public void onStream(final FramedStream stream) throws IOException {
      final List<Header> requestHeaderList = stream.getRequestHeaders();
      final Headers.Builder requestHeaders = new Headers.Builder();
      String method = null;
      String scheme = null;
      String authority = null;
      String path = null;
      for (final Header header: requestHeaderList) {
        final ByteString name = header.name;
        if (name.size() > 0 && name.getByte(0) == PSEUDO_HEADER_PREFIX) {
          if (Header.TARGET_METHOD.equals(name)) {
            method = header.value.utf8();
          }
          else if (Header.TARGET_SCHEME.equals(name)) {
            scheme = header.value.utf8();
          }
          else if (Header.TARGET_AUTHORITY.equals(name)) {
            authority = header.value.utf8();
          }
          else if (Header.TARGET_PATH.equals(name)) {
            path = header.value.utf8();
          }
        }
        else {
          requestHeaders.add(restoreHeaderNameCase(name), header.value.utf8());
        }
      }

      final HttpUrl requestUrl = url(scheme, authority, path);
      final BufferedSource source = Okio.buffer(stream.getSource());

      final Response response;
      if (method == null || requestUrl == null) {
        response = new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
      }
      else if ("100-continue".equals(requestHeaders.get("Expect"))) {
        response = new Response.Builder().statusLine(StatusLines.CONTINUE).noBody().build();
      }
      else {
        final boolean useBody = HttpMethod.permitsRequestBody(method);
        final String contentLength = requestHeaders.get("Content-Length");
        final long length = contentLength == null ? -1 : Long.parseLong(contentLength);
        if (length > max) {
          response = new Response.Builder().
            statusLine(StatusLines.PAYLOAD_TOO_LARGE).noBody().build();
        }
        else {
          if (length == 0) {
            response = handler.handle(true, method, requestUrl, requestHeaders.build(), null);
          }
          else if (length < 0) {
            if (useBody) {
              response = new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
            }
            else {
              response = handler.handle(true, method, requestUrl, requestHeaders.build(), null);
            }
          }
          else {
            if (useBody) {
              final Buffer body = new Buffer();
              if (stream.isOpen()) source.readFully(body, length);
              body.flush();
              response = handler.handle(true, method, requestUrl, requestHeaders.build(), body);
            }
            else {
              response = handler.handle(true, method, requestUrl, requestHeaders.build(), null);
            }
          }
        }
      }

      final List<Header> responseHeaders = responseHeaders(response);
      source.close();
      stream.reply(responseHeaders, true);
      final BufferedSink sink = Okio.buffer(stream.getSink());
      try {
        response.writeBody(source, sink);
        requestHeaders.removeAll(IF_NONE_MATCH);

        for (final HttpUrl push: response.pushUrls()) {
          final Headers headers = requestHeaders.build();
          final int size = responseHeaders.size();
          final List<Header> pushHeaderList = new ArrayList<Header>(size + 4);
          assert method != null;
          assert scheme != null;
          pushHeaderList.add(new Header(TARGET_METHOD, method));
          pushHeaderList.add(new Header(TARGET_PATH, RequestLine.requestPath(push)));
          pushHeaderList.add(new Header(TARGET_AUTHORITY, Util.hostHeader(push, false)));
          pushHeaderList.add(new Header(TARGET_SCHEME, scheme));
          for (int i=0; i<size; ++i) {
            ByteString name = ByteString.encodeUtf8(headers.name(i).toLowerCase(Locale.US));
            pushHeaderList.add(new Header(name, headers.value(i)));
          }
          final Response pushResponse = handler.handle(true, method, push, requestHeaders.build(), null);
          final FramedConnection connection = stream.getConnection();
          final FramedStream pushStream = connection.pushStream(stream.getId(), pushHeaderList, true);
          pushStream.reply(responseHeaders, true);
          final BufferedSink pushSink = Okio.buffer(pushStream.getSink());
          try {
            pushResponse.writeBody(null, pushSink);
          }
          finally {
            pushSink.close();
          }
        }
      }
      finally {
        sink.close();
      }
    }
  }

  private static HttpUrl url(final String scheme, final String authority, final String path) {
    if (scheme == null || authority == null || path == null) return null;
    final int i = authority.indexOf(':');
    final String host = i == -1 ? authority : authority.substring(0, i);
    final int port = i == -1 ? 0 : Integer.valueOf(authority.substring(i+1));

    final HttpUrl.Builder url = new HttpUrl.Builder().
      scheme(scheme).host(host).
      addEncodedPathSegments(path.indexOf('/') == 0 ? path.substring(1) : path);
    if (port > 0) url.port(port);
    return url.build();
  }

}
