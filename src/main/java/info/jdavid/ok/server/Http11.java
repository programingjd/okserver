package info.jdavid.ok.server;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import info.jdavid.ok.server.header.Connection;
import info.jdavid.ok.server.header.Expect;
import okhttp3.Headers;
import okhttp3.internal.http.HttpMethod;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;


@SuppressWarnings({ "WeakerAccess" })
class Http11 {

  private static final Charset ASCII = Charset.forName("ASCII");

  private static long crlf(final BufferedSource in, final long limit) throws IOException {
    long index = 0;
    while (true) {
      index = in.indexOf((byte)'\r', index, limit);
      if (index == -1) return -1;
      if (in.indexOf((byte)'\n', index + 1, index + 2) != -1) return index;
      ++index;
    }
  }

  private static ByteString readRequestLine(final BufferedSource in) throws IOException {
    final long index = crlf(in, 4096);
    if (index == -1) return null;
    final ByteString requestLine = in.readByteString(index);
    in.skip(2L);
    return requestLine;
  }

  private static long skipWhitespace(final BufferedSource in) throws IOException {
    long count = 0;
    while (in.indexOf((byte)' ', 0, 1) == 0 ||
           in.indexOf((byte)'\t', 0, 1) == 0) {
      in.readByte();
      ++count;
    }
    return count;
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

  static void serve(final Socket socket, final boolean secure, final boolean insecureOnly,
                    final long maxRequestSize,
                    final KeepAliveStrategy keepAliveStrategy,
                    final RequestHandler requestHandler) throws IOException {
    final BufferedSource in = Okio.buffer(Okio.source(socket));
    final BufferedSink out = Okio.buffer(Okio.sink(socket));
    try {
      final String clientIp = socket.getInetAddress().getHostAddress();
      int reuseCounter = 0;
      while (useSocket(in, reuseCounter++, keepAliveStrategy)) {
        long availableRequestSize = maxRequestSize;
        final ByteString requestByteString = readRequestLine(in);
        final Response response;
        if (requestByteString == null || requestByteString.size() < 3) {
          response = new Response.Builder().
            statusLine(StatusLines.BAD_REQUEST).
            header(Connection.HEADER, Connection.CLOSE).
            noBody().
            build();        }
        else {
          availableRequestSize -= (requestByteString.size() + 2);
          final String request = requestByteString.string(ASCII);
          final int index1 = request.indexOf(' ');
          final String method = index1 == -1 ? null : request.substring(0, index1);
          final int index2 = method == null ? -1 : request.indexOf(' ', index1 + 1);
          final String path = index2 == -1 ? null : request.substring(index1 + 1, index2);
          if (method == null || path == null) {
            response = new Response.Builder().
              statusLine(StatusLines.BAD_REQUEST).
              header(Connection.HEADER, Connection.CLOSE).
              noBody().
              build();
          }
          else {
            final Headers.Builder headersBuilder = new Headers.Builder();
            boolean noHeaders = true;
            while (true) {
              final long index = crlf(in, availableRequestSize);
              if (index == -1) {
                break;
              }
              if (noHeaders) noHeaders = false;
              final ByteString headerByteString = in.readByteString(index);
              in.skip(2L);
              availableRequestSize -= (index + 2);
              if (index == 0) break;
              headersBuilder.add(headerByteString.string(ASCII));
            }
            if (noHeaders) {
              response = new Response.Builder().
                statusLine(StatusLines.BAD_REQUEST).
                header(Connection.HEADER, Connection.CLOSE).
                noBody().
                build();
            }
            else {
              if (Expect.CONTINUE.equalsIgnoreCase(headersBuilder.get(Expect.HEADER))) {
                final Buffer buffer = new Buffer();
                boolean tooLarge = false;
                while (true) {
                  final long read = in.read(buffer, Math.min(availableRequestSize, 8192L));
                  if (read == -1) break;
                  buffer.skip(read);
                  availableRequestSize -= read;
                  if (availableRequestSize == 0) {
                    tooLarge = true;
                    break;
                  }
                }
                if (tooLarge) {
                  response = new Response.Builder().
                    statusLine(StatusLines.PAYLOAD_TOO_LARGE).
                    noBody().
                    header(Connection.HEADER, Connection.CLOSE).
                    build();
                }
                else {
                  final boolean keepAlive =
                    !Connection.CLOSE.equalsIgnoreCase(headersBuilder.get(Connection.HEADER));
                  final Response.Builder responseBuilder = new Response.Builder().
                    statusLine(StatusLines.CONTINUE).
                    noBody();
                  if (!keepAlive) responseBuilder.header(Connection.HEADER, Connection.CLOSE);
                  response = responseBuilder.build();
                }
              }
              else {
                final String contentLength = headersBuilder.get("Content-Length");
                final long length = contentLength == null ? -1 : Long.parseLong(contentLength);
                if (length > availableRequestSize) {
                  response = new Response.Builder().
                    statusLine(StatusLines.PAYLOAD_TOO_LARGE).
                    header(Connection.HEADER, Connection.CLOSE).
                    noBody().
                    build();
                }
                else {
                  final boolean useBody = HttpMethod.permitsRequestBody(method);
                  if (length == 0) {
                    response = RequestHandler.Helper.handle(requestHandler, clientIp,
                                                            secure, insecureOnly, false,
                                                            method, path, headersBuilder.build(), null);
                  }
                  else if (length < 0 || "chunked".equals(headersBuilder.get("Transfer-Encoding"))) {
                    if (useBody) {
                      final Buffer body = new Buffer();
                      boolean tooLarge = false;
                      boolean invalid = false;
                      while (true) {
                        availableRequestSize -= skipWhitespace(in);
                        final long index = crlf(in, 16);
                        if (index == -1) {
                          invalid = true;
                          break;
                        }
                        final ByteString chunkSizeByteString = in.readByteString(index);
                        in.skip(2L);
                        availableRequestSize -= (index + 2);
                        final long chunkSize = Long.parseLong(chunkSizeByteString.string(ASCII).trim(), 16);
                        if (chunkSize == 0) {
                          if (crlf(in, 2) != 0) {
                            invalid = true;
                          }
                          else {
                            in.skip(2L);
                          }
                          break;
                        }
                        if (chunkSize > availableRequestSize) {
                          tooLarge = true;
                          break;
                        }
                        if (!socket.isClosed()) {
                          long remaining = chunkSize;
                          while (true) {
                            final long read = in.read(body, remaining);
                            if (read == -1) {
                              invalid = true;
                              break;
                            }
                            remaining -= read;
                            if (remaining < 1) break;
                          }
                          if (invalid) break;
                          availableRequestSize -= chunkSize;
                        }
                        body.flush();
                        if (crlf(in, 2) != 0) {
                          invalid = true;
                          break;
                        }
                        else {
                          in.skip(2L);
                        }
                        availableRequestSize -= 2;
                      }
                      if (invalid) {
                        response = new Response.Builder().
                          statusLine(StatusLines.BAD_REQUEST).
                          header(Connection.HEADER, Connection.CLOSE).
                          noBody().
                          build();
                      }
                      else if (tooLarge) {
                        response = new Response.Builder().
                          statusLine(StatusLines.PAYLOAD_TOO_LARGE).
                          header(Connection.HEADER, Connection.CLOSE).
                          noBody().
                          build();
                      }
                      else {
                        response = RequestHandler.Helper.handle(requestHandler, clientIp,
                                                                secure, insecureOnly, false,
                                                                method, path, headersBuilder.build(), body);
                      }
                    }
                    else {
                      response = RequestHandler.Helper.handle(requestHandler, clientIp,
                                                              secure, insecureOnly, false,
                                                              method, path, headersBuilder.build(), null);
                    }
                  }
                  else { // length > 0
                    if (useBody) {
                      final Buffer body = new Buffer();
                      if (!socket.isClosed()) in.readFully(body, length);
                      body.flush();
                      response = RequestHandler.Helper.handle(requestHandler, clientIp,
                                                              secure, insecureOnly, false,
                                                              method, path, headersBuilder.build(), body);
                    }
                    else {
                      response = RequestHandler.Helper.handle(requestHandler, clientIp,
                                                              secure, insecureOnly, false,
                                                              method, path, headersBuilder.build(), null);
                    }
                  }
                }
              }
            }
          }
        }

        out.writeUtf8(response.protocol().toString().toUpperCase(Locale.US));
        out.writeUtf8(" ");
        out.writeUtf8(String.valueOf(response.code()));
        out.writeUtf8(" ");
        out.writeUtf8(response.message());
        out.writeUtf8("\r\n");
        final Headers headers = response.headers();
        final int headersSize = headers.size();
        for (int i=0; i<headersSize; ++i) {
          out.writeUtf8(headers.name(i));
          out.writeUtf8(": ");
          out.writeUtf8(headers.value(i));
          out.writeUtf8("\r\n");
        }
        out.writeUtf8("\r\n");
        out.flush();

        response.writeBody(in, out);

        if (Connection.CLOSE.equalsIgnoreCase(response.header(Connection.HEADER))) break;
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
