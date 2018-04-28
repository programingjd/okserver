package info.jdavid.ok.server

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AlreadyBoundException
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import kotlin.concurrent.thread
import info.jdavid.ok.server.Logger.logger
import kotlinx.coroutines.experimental.nio.aRead
import okio.BufferedSource
import okio.ByteString
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit


class CoroutineDispatcher : Dispatcher<AsynchronousServerSocketChannel>() {

  override fun initSockets(insecurePort: Int, securePort: Int, https: Https?, address: InetAddress?) {
    if (insecurePort > 0) {
      val insecureSocket = AsynchronousServerSocketChannel.open()
      insecureSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true)
      try {
        insecureSocket.bind(InetSocketAddress(address, insecurePort))
      }
      catch (e: AlreadyBoundException) {
        logger.warn("Could not bind to port ${insecurePort}.", e)
      }
      this.insecureSocket = insecureSocket
    }
    if (securePort > 0 && https != null) {
      val secureSocket = AsynchronousServerSocketChannel.open()
      secureSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true)
      try {
        secureSocket.bind(InetSocketAddress(address, securePort))
      }
      catch (e: AlreadyBoundException) {
        logger.warn("Could not bind to port ${securePort}.", e)
      }
      this.secureSocket = secureSocket
    }
  }

  override fun start() {}

  override fun shutdown() {}

  override fun loop(socket: AsynchronousServerSocketChannel?,
                    secure: Boolean,
                    insecureOnly: Boolean,
                    https: Https?,
                    hostname: String?,
                    maxRequestSize: Long,
                    keepAliveStrategy: KeepAliveStrategy,
                    requestHandler: RequestHandler) {
    thread {
      if (socket != null) {
        runBlocking {
          socket.use {
            while (true) {
              try {
                val channel = socket.aAccept()
                launch(context) {
                  channel.use {
                    Request(channel, secure, insecureOnly, https, hostname,
                            maxRequestSize, keepAliveStrategy, requestHandler).serve()
                  }
                }
              }
              catch (e: IOException) {
                if (!socket.isOpen()) break
                logger.warn(secure.ifElse("HTTPS", "HTTP"), e)
              }
            }
          }
        }
      }
    }.start()

  }

  private fun <T> Boolean.ifElse(ifValue: T, elseValue: T): T {
    return if (this) ifValue else elseValue
  }

  private class Request(private val channel: AsynchronousSocketChannel,
                        private val secure: Boolean,
                        private val insecureOnly: Boolean,
                        private val https: Https?,
                        private val hostname: String?,
                        private val maxRequestSize: Long,
                        private val keepAliveStrategy: KeepAliveStrategy,
                        private val requestHandler: RequestHandler) {


    suspend fun serve() {
      if (secure) {
        assert(https != null)
      }
      else {
        serveHttp1(channel, false, insecureOnly)
      }
    }


    private fun useSocket(reuse: Int, strategy: KeepAliveStrategy): Boolean {
      val timeout = strategy.timeout(reuse)
      if (timeout <= 0) {
        return reuse == 0
      }
      else {
        return true
      }
    }

    suspend fun serveHttp1(channel: AsynchronousSocketChannel,
                           secure: Boolean,
                           insecureOnly: Boolean) {
      try {
        val clientIp = (channel.remoteAddress as InetSocketAddress).address.hostAddress
        var reuseCounter = 0
        while (useSocket(reuseCounter++, keepAliveStrategy)) {
          var availableRequestSize = maxRequestSize
          // todo
        }
      }
      catch (ignore: SocketTimeoutException) {}
      catch (e: Exception) {
        logger.warn(e.message, e)
      }
    }

  }

  private val ASCII = Charset.forName("ASCII")

  private fun crlf(input: BufferedSource, limit: Long): Long {
    var index: Long = 0L
    while (true) {
      index = input.indexOf('\r'.toByte(), index, limit)
      if (index == -1L) return -1L
      if (input.indexOf('\n'.toByte(), index + 1L, index + 2L) != -1L) return index
      ++index
    }
  }

  @Throws(IOException::class)
  private fun readRequestLine(input: BufferedSource): ByteString? {
    val index = crlf(input, 4096)
    if (index == -1L) return null
    val requestLine = input.readByteString(index)
    input.skip(2L)
    return requestLine
  }

}
