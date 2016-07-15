package info.jdavid.ok.server;

import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

import okhttp3.Headers;
import okhttp3.internal.http.HttpMethod;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

class Http11 {

  private static String trim(final String s) {
    return s == null ? null : s.trim();
  }

  static void serve(final HttpServer server, final Socket socket,
                    final boolean secure, final long maxRequestSize) throws IOException {
    final BufferedSource in = Okio.buffer(Okio.source(socket));
    final BufferedSink out = Okio.buffer(Okio.sink(socket));
    try {
      int reuseCounter = 0;
      while (server.use(in, reuseCounter++)) {
        final String request = trim(in.readUtf8LineStrict());
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
                response = server.handle(secure, method, path, headersBuilder.build(), null);
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
                    response = server.handle(secure, method, path, headersBuilder.build(), body);
                  }
                }
                else {
                  response = server.handle(secure, method, path, headersBuilder.build(), null);
                }
              }
              else { // length > 0
                if (useBody) {
                  final Buffer body = new Buffer();
                  if (!socket.isClosed()) in.readFully(body, length);
                  body.flush();
                  response = server.handle(secure, method, path, headersBuilder.build(), body);
                }
                else {
                  response = server.handle(secure, method, path, headersBuilder.build(), null);
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

        r.writeBody(in, out, socket);
      }
    }
    catch (final Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      try { in.close(); } catch (final IOException ignore) {}
      try { out.close(); } catch (final IOException ignore) {}
      try { socket.close(); } catch (final IOException ignore) {}
    }
  }

}
