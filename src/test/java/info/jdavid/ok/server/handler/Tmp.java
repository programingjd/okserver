package info.jdavid.ok.server.handler;

import java.io.File;

import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.RequestHandlerChain;

public class Tmp {

  public static void main(final String[] args) {
    final File testReportRoot =
      new File("i:/tactyl/fairguest-scraping-java/build/reports/tests/test");
    new HttpServer().
      requestHandler(
        new RequestHandlerChain().
          add(new FileRequestHandler("/test(.*)", testReportRoot))
      ).
      port(8080).
      start();
  }

}
