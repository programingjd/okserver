![jcenter](https://img.shields.io/badge/_jcenter_-_3.7.0.1-6688ff.png?style=flat) &#x2003; ![jcenter](https://img.shields.io/badge/_Tests_-_76/76-green.png?style=flat)
# okserver
A simple http server for the jvm and android, built on top of [okhttp](https://github.com/square/okhttp).

## Download ##

The maven artifacts are on [Bintray](https://bintray.com/programingjd/maven/info.jdavid.ok.server/view)
and [jcenter](https://bintray.com/search?query=info.jdavid.ok.server).

[Download](https://bintray.com/artifact/download/programingjd/maven/info/jdavid/ok/server/okserver/3.7.0.1/okserver-3.7.0.1.jar) the latest jar.

__Maven__

Include [those settings](https://bintray.com/repo/downloadMavenRepoSettingsFile/downloadSettings?repoPath=%2Fbintray%2Fjcenter)
 to be able to resolve jcenter artifacts.
```
<dependency>
  <groupId>info.jdavid.ok.server</groupId>
  <artifactId>okserver</artifactId>
  <version>3.7.0.1</version>
</dependency>
```
__Gradle__

Add jcenter to the list of maven repositories.
```
repositories {
  jcenter()
}
```
```
dependencies {
  compile 'info.jdavid.ok.server:okserver:3.7.0.1'
}
```

## Usage ##

You specify what your server does by overriding the `handle` method.

Here's a very simple example:
  - For a **GET** request to **/ok**, we return a **200 OK** with the content `"ok"`.
  - For any other request, we return a **404 Not Found** with no content.

```java
    new HttpServer().requestHandler(
      new RequestHandler() {
        @Override
        public Response handle(final String clientIp, final boolean secure,
                               final boolean insecureOnly, final boolean http2,
                               final String method, final HttpUrl url,
                               final Headers requestHeaders,
                               final Buffer requestBody) {
          final String path = url.encodedPath();
          final Response.Builder builder = new Response.Builder();
          if ("GET".equals(method) && "/ok".equals(path)) {
            builder.statusLine(StatusLines.OK).body("ok");
          }
          else {
            builder.statusLine(StatusLines.NOT_FOUND).noBody();
          }
          return builder.build();
        }
      }
    );
```

To start the server, you simply call `start()`. It defaults to port 8080, but you can change that easily
with the `port(int)` method. It also defaults to all the ip addresses on the local machine, but you can also
change that easily with the `hostname(String)` method.


```java
new HttpServer().hostname("localhost").port(80).start();
```

If you want to use https, you can specify another port and the certificate to use for tls.

```java
new HttpServer().
  hostname("example.com").
  ports(8080, 8181).
  https(new Https.Builder().certificate(p12, useHttp2).build()).
  start();
```

Requests are handled by a dispatcher. The default implementation uses a cached thread pool.
You can change the dispatcher with the `dispatcher(Dispatcher)` method. The Dispatcher class includes
various implementations that are ready to use. You can also provide your own implementation.

Here's an example that sets a dispatcher with a single thread executor rather than a cached thread pool.

```java
final HttpServer server = new HttpServer().dispatcher(
  new Dispatcher() {
    private ExecutorService mExecutors = null;
    @Override
    public void start() {
      mExecutors = Executors.newSingleThreadExecutor();
    }
    @Override
    public void dispatch(final HttpServer.Request request) {
      mExecutors.execute(new Runnable() {
        @Override public void run() { request.serve(); }
      });
    }
    @Override
    public void shutdown() {
      try {
        mExecutors.awaitTermination(5, TimeUnit.SECONDS);
      }
      catch (final InterruptedException ignore) {}
      mExecutors.shutdownNow();
    }
  }
);
```

You can find more examples in the ***samples*** directory.
These include examples for implementing Server Side Events (SSE).
