package info.jdavid.ok.server;

import okhttp3.internal.http.StatusLine;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;


//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ResponseBuilderTest {

  private Response.Builder response(final int code) {
    return new Response.Builder().statusLine(StatusLines.get(code));
  }

  @Test
  public void testMalformed() throws IOException {
    try {
      new Response.Builder().build();
      fail("Should have failed because no status line has been set.");
      new Response.Builder().statusLine(StatusLine.parse("HTTP1.1/200")).build();
      fail("Should have failed because no status message has been set.");
      new Response.Builder().statusLine(StatusLine.parse("HTTP1.1/-999")).build();
      fail("Should have failed because invalid status code has been set.");
    }
    catch (IllegalStateException ignore) {}
  }

  @Test
  public void testCode() throws IOException {
    assertNull(StatusLines.get(999));
    assertNull(StatusLines.get(0));
    assertNull(StatusLines.get(-200));
    int[] codes = new int[] { 200, 404, 500 };
    for (int code: codes) {
      final Response response = response(code).build();
      assertEquals(code, response.code());
      //noinspection ConstantConditions
      assertEquals(StatusLines.get(code).message, response.message());
    }
  }

  @Test
  public void testCacheControl() throws IOException {
    final String etag = "e-tag";
    assertEquals(etag, response(200).noCache("e-tag").build().header("ETag"));
    assertEquals("no-cache", response(200).noCache(null).build().header("Cache-Control"));
    assertEquals(etag, response(200).etag(etag).noCache(null).build().header("ETag"));
    assertNull(response(200).noCache(etag).etag(null).build().header("ETag"));
    assertEquals("no-store", response(200).noStore().build().header("Cache-Control"));
    assertNull(response(200).etag(etag).noStore().build().header("ETag"));
    assertTrue(response(200).maxAge(30).priv().build().headers("Cache-Control").contains("private"));
    assertEquals("max-age=60", response(200).maxAge(60).build().header("Cache-Control"));
    assertTrue(response(200).maxAge(60).priv().build().headers("Cache-Control").contains("max-age=60"));
  }

  @Test
  public void testHeaders() throws IOException {
    assertEquals("v1", response(200).header("h1", "v1").build().header("h1"));
    assertEquals("v1", response(200).addHeader("h1", "v1").build().header("h1"));
    assertEquals("v2", response(200).header("h1", "v1").header("h1", "v2").build().header("h1"));
    final List<String> values = response(200).header("h1", "v1").addHeader("h1", "v2").build().headers("h1");
    assertEquals(2, values.size());
    assertTrue(values.contains("v1"));
    assertTrue(values.contains("v2"));
    assertNull(response(200).header("h1","v1").addHeader("h1","v2").removeHeader("h1").build().header("h1"));
  }

  @Test
  public void testBody() throws IOException {
    assertNull(response(404).noBody().build().body());
    assertEquals("test", response(200).body("test").build().body().string());
    assertEquals(4, response(200).body("1234").build().body().bytes().length);
  }

}
