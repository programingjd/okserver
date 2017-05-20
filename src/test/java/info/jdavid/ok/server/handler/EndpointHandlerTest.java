package info.jdavid.ok.server.handler;

import java.io.IOException;
import java.util.Map;

import info.jdavid.ok.json.Parser;
import info.jdavid.ok.server.MediaTypes;
import okhttp3.HttpUrl;
import okhttp3.Request;

import info.jdavid.ok.server.samples.ApiServer;

import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static info.jdavid.ok.server.handler.DigestAuthHandlerTest.client;


public class EndpointHandlerTest {

  private static final ApiServer SERVER = new ApiServer(); //.dispatcher(new Dispatcher.Logged());

  @Before
  public void startServer() {
    SERVER.start();
  }

  @After
  public void stopServer() {
    SERVER.stop();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testResources() throws IOException {
    final HttpUrl baseUrl = HttpUrl.parse("http://localhost:8080/");
    assertNotNull(baseUrl);
    final Response response = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources").build()).
        build()
    ).execute();
    assertTrue(response.isSuccessful());
    assertEquals(200, response.code());
    assertTrue(response.header("Content-Type").startsWith("application/json"));
    final Object json = Parser.parse(response.body().source());
    assertTrue(json instanceof Map);
    //noinspection unchecked
    final Map<String, ?> resources = (Map<String, ?>)json;
    assertEquals(2, resources.size());
    assertTrue(resources.get("item1") instanceof Map);
    assertTrue(resources.get("item2") instanceof Map);
    //noinspection unchecked
    final Map<String, ?> item1 = (Map<String, ?>)resources.get("item1");
    assertEquals("test1", item1.get("name"));
    //noinspection unchecked
    final Map<String, ?> item2 = (Map<String, ?>)resources.get("item2");
    assertEquals("test2", item2.get("name"));
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testHead() throws IOException {
    final HttpUrl baseUrl = HttpUrl.parse("http://localhost:8080/");
    assertNotNull(baseUrl);
    final Response response1 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item").build()).
        head().
        build()
    ).execute();
    assertFalse(response1.isSuccessful());
    assertEquals(404, response1.code());
    response1.close();
    final Response response2 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item1").build()).
        build()
    ).execute();
    assertTrue(response2.isSuccessful());
    assertEquals(200, response2.code());
    assertEquals("0", response2.header("Content-Length"));
    response2.close();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testGet() throws IOException {
    final HttpUrl baseUrl = HttpUrl.parse("http://localhost:8080/");
    assertNotNull(baseUrl);
    final Response response1 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item").build()).
        build()
    ).execute();
    assertFalse(response1.isSuccessful());
    assertEquals(404, response1.code());
    response1.close();
    final Response response2 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item1").build()).
        build()
    ).execute();
    assertTrue(response2.isSuccessful());
    assertEquals(200, response2.code());
    assertTrue(response2.header("Content-Type").startsWith("application/json"));
    final Object json = Parser.parse(response2.body().source());
    assertTrue(json instanceof Map);
    //noinspection unchecked
    final Map<String, ?> item1 = (Map<String, ?>)json;
    assertEquals("test1", item1.get("name"));
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testDelete() throws IOException {
    final HttpUrl baseUrl = HttpUrl.parse("http://localhost:8080/");
    assertNotNull(baseUrl);
    final Response response1 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item").build()).
        delete().
        build()
    ).execute();
    assertFalse(response1.isSuccessful());
    assertEquals(404, response1.code());
    response1.close();
    final Response response2 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item1").build()).
        delete().
        build()
    ).execute();
    assertTrue(response2.isSuccessful());
    assertEquals(204, response2.code());
    assertEquals("0", response2.header("Content-Length"));
    response2.close();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testPost() throws IOException {
    final HttpUrl baseUrl = HttpUrl.parse("http://localhost:8080/");
    assertNotNull(baseUrl);
    final String invalidData = "{\"test\":true}";
    final Response response1 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources").build()).
        post(RequestBody.create(MediaTypes.JSON, invalidData)).
        build()
    ).execute();
    assertFalse(response1.isSuccessful());
    assertEquals(400, response1.code());
    response1.close();
    final String data = "{\"name\":\"test\"}";
    final Response response2 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/").build()).
        post(RequestBody.create(MediaTypes.JSON, data)).
        build()
    ).execute();
    assertTrue(response2.isSuccessful());
    assertEquals(200, response2.code());
    assertTrue(response2.header("Content-Type").startsWith("application/json"));
    final Object json = Parser.parse(response2.body().source());
    assertTrue(json instanceof Map);
    //noinspection unchecked
    final Map<String, ?> item = (Map<String, ?>)json;
    assertEquals("test", item.get("name"));
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testPut() throws IOException {
    final HttpUrl baseUrl = HttpUrl.parse("http://localhost:8080/");
    assertNotNull(baseUrl);
    final String invalidData = "{\"test\":true}";
    final Response response1 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item").build()).
        put(RequestBody.create(MediaTypes.JSON, invalidData)).
        build()
    ).execute();
    assertFalse(response1.isSuccessful());
    assertEquals(404, response1.code());
    response1.close();
    final Response response2 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item1").build()).
        put(RequestBody.create(MediaTypes.JSON, invalidData)).
        build()
    ).execute();
    assertFalse(response2.isSuccessful());
    assertEquals(400, response2.code());
    response2.close();
    final String data = "{\"name\":\"test\"}";
    final Response response3 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item1").build()).
        put(RequestBody.create(MediaTypes.JSON, data)).
        build()
    ).execute();
    assertTrue(response3.isSuccessful());
    assertEquals(204, response3.code());
    response3.close();
    final Response response4 = client().newCall(
      new Request.Builder().
        url(baseUrl.newBuilder("/resources/item1").build()).
        build()
    ).execute();
    assertTrue(response4.isSuccessful());
    assertEquals(200, response4.code());
    assertTrue(response4.header("Content-Type").startsWith("application/json"));
    final Object json = Parser.parse(response4.body().source());
    assertTrue(json instanceof Map);
    //noinspection unchecked
    final Map<String, ?> item = (Map<String, ?>)json;
    assertEquals("test", item.get("name"));
  }

}
