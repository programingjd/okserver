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
