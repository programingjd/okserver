import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

final String body = new OkHttpClient().newCall(new Request.Builder().url('http://www.iana.org/assignments/tls-parameters/tls-parameters.xml').build()).execute().body().string()

final Iterable list = new XmlSlurper().parseText(body);

final Buffer buffer = new Buffer();

list.registry.find {
  it.@id == 'tls-parameters-4'
}.record.collectEntries {
  String description = it.description.text()
  if (description.startsWith('TLS')) {
    String[] values = it.value.text().split(',')
    assert values.length == 2
    assert values[0].length() == 4
    assert values[1].length() == 4
    buffer.writeByte(Integer.parseInt(values[0].substring(2), 16))
    buffer.writeByte(Integer.parseInt(values[1].substring(2), 16))
    return [ (buffer.readShort()): description ]
  }
  else {
    return [:]
  }
}.each {
  println "map.put((short)${it.key}, \"${it.value}\");"
}
