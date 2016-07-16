package info.jdavid.ok.server;

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

  static void log(final Throwable t) {
    t.printStackTrace();
  }

}
