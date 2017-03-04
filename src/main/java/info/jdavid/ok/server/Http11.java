package info.jdavid.ok.server;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.internal.http.HttpMethod;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;

import static info.jdavid.ok.server.Logger.logger;


class Http11 {

  private static String readRequest(final BufferedSource in) throws IOException {
    final long index = in.indexOf((byte)'\n');
    if (index == -1) {
      final ByteString byteString = in.readByteString();
      if (byteString.size() > 0) {
        logger.warn("Invalid HTTP1.1 request:\n" + byteString.utf8());
      }
      return null;
    }
    else {
      final String line = in.readUtf8Line();
      return line == null ? null : line.trim();
    }
  }

  private static boolean useSocket(final BufferedSource in, final int reuse,
                                   final KeepAliveStrategy strategy) {
    final int timeout = strategy.timeout(reuse);
    if (timeout <= 0) {
      return reuse == 0;
    }
    else {
      in.timeout().timeout(timeout, TimeUnit.SECONDS);
      return true;
    }
  }

  static void serve(final Socket socket, final boolean secure,
                    final long maxRequestSize,
                    final KeepAliveStrategy keepAliveStrategy,
                    final RequestHandler requestHandler) throws IOException {
    final BufferedSource in = Okio.buffer(Okio.source(socket));
    final BufferedSink out = Okio.buffer(Okio.sink(socket));
    try {
      final String clientIp = socket.getInetAddress().getHostAddress();
      int reuseCounter = 0;
      while (useSocket(in, reuseCounter++, keepAliveStrategy)) {
        final String request = readRequest(in);
        if (request == null || request.length() == 0) return;
        final int index1 = request.indexOf(' ');
        final String method = index1 == -1 ? null : request.substring(0, index1);
        final int index2 = method == null ? -1 : request.indexOf(' ', index1 + 1);
        final String path = index2 == -1 ? null : request.substring(index1 + 1, index2);
        final Response response;
        if (method == null || path == null) {
          response = new Response.Builder().statusLine(StatusLines.BAD_REQUEST).noBody().build();
        }
        else {
          final boolean useBody = HttpMethod.permitsRequestBody(method);
          final Headers.Builder headersBuilder = new Headers.Builder();
          String header;
          while ((header = in.readUtf8LineStrict()).length() != 0) {
            headersBuilder.add(header);
          }
          if ("100-continue".equals(headersBuilder.get("Expect"))) {
            response = new Response.Builder().
              statusLine(StatusLines.CONTINUE).noBody().build();
          }
          else {
            final String contentLength = headersBuilder.get("Content-Length");
            final long length = contentLength == null ? -1 : Long.parseLong(contentLength);
            if (length > maxRequestSize) {
              response = new Response.Builder().
                statusLine(StatusLines.PAYLOAD_TOO_LARGE).noBody().build();
            }
            else {
              if (length == 0) {
                response = RequestHandler.Helper.handle(requestHandler, clientIp, secure,
                                                        method, path, headersBuilder.build(), null);
              }
              else if (length < 0 || "chunked".equals(headersBuilder.get("Transfer-Encoding"))) {
                if (useBody) {
                  final Buffer body = new Buffer();
                  long total = 0L;
                  boolean invalid = false;
                  while (true) {
                    final long chunkSize = Long.parseLong(in.readUtf8LineStrict().trim(), 16);
                    total += chunkSize;
                    if (chunkSize == 0) {
                      if (in.readUtf8LineStrict().length() != 0) invalid = true;
                      break;
                    }
                    if (total > maxRequestSize) {
                      break;
                    }
                    if (!socket.isClosed()) in.read(body, chunkSize);
                    body.flush();
                    if (in.readUtf8LineStrict().length() != 0) {
                      invalid = true;
                      break;
                    }
                  }
                  if (invalid) {
                    response = new Response.Builder().
                      statusLine(StatusLines.BAD_REQUEST).noBody().build();
                  }
                  else if (total > maxRequestSize) {
                    response = new Response.Builder().
                      statusLine(StatusLines.PAYLOAD_TOO_LARGE).noBody().build();
                  }
                  else {
                    response = RequestHandler.Helper.handle(requestHandler, clientIp, secure,
                                                            method, path, headersBuilder.build(), body);
                  }
                }
                else {
                  response = RequestHandler.Helper.handle(requestHandler, clientIp, secure,
                                                          method, path, headersBuilder.build(), null);
                }
              }
              else { // length > 0
                if (useBody) {
                  final Buffer body = new Buffer();
                  if (!socket.isClosed()) in.readFully(body, length);
                  body.flush();
                  response = RequestHandler.Helper.handle(requestHandler, clientIp, secure,
                                                          method, path, headersBuilder.build(), body);
                }
                else {
                  response = RequestHandler.Helper.handle(requestHandler, clientIp, secure,
                                                          method, path, headersBuilder.build(), null);
                }
              }
            }
          }
        }
        final Response r =
          response == null ?
          new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build() :
          response;

        out.writeUtf8(r.protocol().toString().toUpperCase(Locale.US));
        out.writeUtf8(" ");
        out.writeUtf8(String.valueOf(r.code()));
        out.writeUtf8(" ");
        out.writeUtf8(r.message());
        out.writeUtf8("\r\n");
        final Headers headers = r.headers();
        final int headersSize = headers.size();
        for (int i=0; i<headersSize; ++i) {
          out.writeUtf8(headers.name(i));
          out.writeUtf8(": ");
          out.writeUtf8(headers.value(i));
          out.writeUtf8("\r\n");
        }
        out.writeUtf8("\r\n");
        out.flush();

        r.writeBody(in, out);
      }
    }
    catch (final SocketTimeoutException ignore) {}
    catch (final SocketException ignored) {}
    catch (final Exception e) {
      throw new IOException(e);
    }
    finally {
      try { in.close(); } catch (final IOException ignore) {}
      try { out.close(); } catch (final IOException ignore) {}
      try { socket.close(); } catch (final IOException ignore) {}
    }
  }

}
