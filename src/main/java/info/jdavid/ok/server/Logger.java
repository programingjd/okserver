package info.jdavid.ok.server;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

class Logger {

  @SuppressWarnings("unused")
  static void log(final String...values) {
    switch (values.length) {
      case 0:
        return;
      case 1:
        System.out.println(values[0]);
        return;
      case 2:
        System.out.println(values[0] + ": " + values[1]);
        return;
      default:
        boolean addSeparator = false;
        for (final String value: values) {
          if (addSeparator) {
            System.out.print(' ');
          }
          else {
            addSeparator = true;
          }
          System.out.print(value);
        }
        System.out.println();
    }
  }

  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd-hh:mm:ss");

  static void log(final Throwable t) {
    System.err.print('[');
    System.err.print(DATE_FORMAT.format(new Date()));
    System.err.print(']');
    System.err.print(' ');
    System.err.println(t.getMessage());
    t.printStackTrace();
  }

}
