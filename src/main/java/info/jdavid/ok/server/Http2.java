package info.jdavid.ok.server;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocket;

import info.jdavid.ok.server.header.ETag;
import okhttp3.*;
import okhttp3.internal.Util;
import okhttp3.internal.http2.Http2Connection;
import okhttp3.internal.http2.Http2Stream;
import okhttp3.internal.http2.Header;
import okhttp3.internal.http.HttpMethod;
import okhttp3.internal.http.RequestLine;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;
import okio.Timeout;

import static okhttp3.internal.http2.Header.TARGET_AUTHORITY;
import static okhttp3.internal.http2.Header.TARGET_METHOD;
import static okhttp3.internal.http2.Header.TARGET_PATH;
import static okhttp3.internal.http2.Header.TARGET_SCHEME;


@SuppressWarnings({ "WeakerAccess" })
class Http2 {

  static void serve(final SSLSocket socket, final String hostname,
                    final long maxRequestSize,
                    final KeepAliveStrategy keepAliveStrategy,
                    final RequestHandler requestHandler) throws IOException {
    final String hostAddress = socket.getInetAddress().getHostAddress();
    assert(hostAddress != null);
    final Http2ConnectionListener listener =
      new Http2ConnectionListener(requestHandler, maxRequestSize, hostAddress);
    final BufferedSource in = Okio.buffer(Okio.source(socket));
    final BufferedSink out = Okio.buffer(Okio.sink(socket));
    final Http2Connection connection = new Http2Connection.Builder(false).
      socket(socket, hostname, in, out).
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

  private static List<Header> responseHeaders(final Response response) {
    final Headers headers = response.headers();
    final int size = headers.size();
    final List<Header> responseHeaders = new ArrayList<>(size + 1);
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

  private static String restoreHeaderNameCase(final String name) {
    // name should be ascii lowercase
    final int size = name.length();
    final StringBuilder restored = new StringBuilder(size);
    char prev = '-';
    for (int i=0; i<size; ++i) {
      final char c = (prev == '-' ? Character.toUpperCase(name.charAt(i)) : name.charAt(i));
      restored.append(c);
      prev = c;
    }
    return restored.toString();
  }

  private static class Http2ConnectionListener extends Http2Connection.Listener {

    final RequestHandler handler;
    final long max;
    final String clientIp;

    Http2ConnectionListener(final RequestHandler requestHandler,
                            final long maxRequestSize, final String address) {
      handler = requestHandler;
      max = maxRequestSize;
      clientIp = address;
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    @Override public void onStream(final Http2Stream stream) throws IOException {
      final Headers requestHeaderList = stream.takeHeaders(); //  getRequestHeaders();
      final Headers.Builder requestHeaders = new Headers.Builder();
      String method = null;
      String scheme = null;
      String authority = null;
      String path = null;
      for (int i = 0; i<requestHeaderList.size(); ++i) {
        final String name = requestHeaderList.name(i);
        if (name.length() > 0 && name.charAt(0) == ':') {
          if (Header.TARGET_METHOD.equals(name)) {
            method = requestHeaderList.value(i);
          }
          else if (Header.TARGET_SCHEME.equals(name)) {
            scheme = requestHeaderList.value(i);
          }
          else if (Header.TARGET_AUTHORITY.equals(name)) {
            authority = requestHeaderList.value(i);
          }
          else if (Header.TARGET_PATH.equals(name)) {
            path = requestHeaderList.value(i);
          }
        }
        else {
          requestHeaders.add(restoreHeaderNameCase(name), requestHeaderList.value(i));
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
            response = handler.handle(clientIp, true, false,true, method, requestUrl,
                                      requestHeaders.build(), null);
          }
          else if (length < 0) {
            if (useBody) {
              response = new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
            }
            else {
              response = handler.handle(clientIp, true, false,true,
                                        method, requestUrl, requestHeaders.build(), null);
            }
          }
          else {
            if (useBody) {
              final Buffer body = new Buffer();
              if (stream.isOpen()) source.readFully(body, length);
              body.flush();
              response = handler.handle(clientIp, true, false,true,
                                        method, requestUrl, requestHeaders.build(), body);
            }
            else {
              response = handler.handle(clientIp, true, false,true,
                                        method, requestUrl, requestHeaders.build(), null);
            }
          }
        }
      }

      final List<Header> responseHeaders = responseHeaders(response);
      source.close();
      stream.writeHeaders(responseHeaders, true,true);
      final BufferedSink sink = Okio.buffer(stream.getSink());
      try {
        response.writeBody(source, sink);
        requestHeaders.removeAll(ETag.IF_NONE_MATCH);

        final Http2Connection connection = stream.getConnection();
        // TODO, check that Push is enabled by the SETTINGS frame.
        final List<HttpUrl> pushUrls = response.pushUrls();
        if (pushUrls != null) {
          for (final HttpUrl push : pushUrls) {
            final Headers headers = requestHeaders.build();
            final int size = headers.size();
            final List<Header> pushHeaderList = new ArrayList<>(size + 4);
            assert method != null;
            assert scheme != null;
            pushHeaderList.add(new Header(TARGET_METHOD, method));
            pushHeaderList.add(new Header(TARGET_PATH, RequestLine.requestPath(push)));
            pushHeaderList.add(new Header(TARGET_AUTHORITY, Util.hostHeader(push, false)));
            pushHeaderList.add(new Header(TARGET_SCHEME, scheme));
            for (int i = 0; i < size; ++i) {
              ByteString name = ByteString.encodeUtf8(headers.name(i).toLowerCase(Locale.US));
              pushHeaderList.add(new Header(name, headers.value(i)));
            }
            final Response pushResponse =
              handler.handle(clientIp, true, false,true, method, push,
                             requestHeaders.build(), null);
            final Http2Stream pushStream = connection.pushStream(stream.getId(), pushHeaderList, true);
            pushStream.writeHeaders(responseHeaders(pushResponse), true,true);
            final BufferedSink pushSink = Okio.buffer(pushStream.getSink());
            try {
              pushResponse.writeBody(null, pushSink);
            }
            finally {
              pushSink.close();
            }
          }
        }
      }
      finally {
        sink.close();
      }
    }
  }

  private static @Nullable HttpUrl url(@Nullable final String scheme,
                                       @Nullable final String authority,
                                       @Nullable final String path) {
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
