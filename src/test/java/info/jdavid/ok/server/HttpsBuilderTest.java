package info.jdavid.ok.server;

import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.*;


//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HttpsBuilderTest {

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testMalformed() throws IOException {
    try {
      new Https.Builder().build();
      fail("Should have failed because no certificate has been set.");
    }
    catch (final IllegalStateException ignore) {}
    try {
      new Https.Builder().certificate(null).build();
      fail("Should have failed because the certificate was null.");
    }
    catch (final NullPointerException ignore) {}
    try {
      new Https.Builder().addCertificate(null, HttpsTest.cert).build();
      fail("Should have failed because no hostname has been set.");
    }
    catch (final NullPointerException ignore) {}
    try {
      new Https.Builder().addCertificate("example.com", null).build();
      fail("Should have failed because the certificate was null.");
    }
    catch (final NullPointerException ignore) {}
  }

  @Test
  public void testCertificate() {
    final Https.Builder builder = new Https.Builder().certificate(HttpsTest.cert);
    Https https = builder.build();
    assertNotNull(https.protocols);
    assertNotNull(https.cipherSuites);
    assertTrue(https.http2);
    assertNotNull(https.getContext("localhost"));
  }

}
