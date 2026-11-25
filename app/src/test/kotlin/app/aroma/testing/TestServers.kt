package app.aroma.testing

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ServerSocketFactory

/**
 * A stand-in for Mealie, started, that answers every request with [answer].
 *
 * Its answers go out at once. MockWebServer writes the headers of an answer and its body apart, and
 * Nagle's algorithm holds the body back until the client acknowledges the headers, which the client
 * delays by 40 ms: every request but the first on a connection would wait that long.
 */
internal fun mealieServer(answer: (RecordedRequest) -> MockResponse): MockWebServer =
    MockWebServer().apply {
        serverSocketFactory = SendingAtOnce
        dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = answer(request)
            }
        start()
    }

/** The server's address as a session keeps it. */
internal val MockWebServer.address: String get() = url("/").toString().trimEnd('/')

internal fun json(body: String): MockResponse =
    MockResponse
        .Builder()
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

/** Server-sent events, as Mealie streams the progress of an import. */
internal fun eventStream(body: String): MockResponse =
    MockResponse
        .Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body(body)
        .build()

/** Server sockets whose connections send every write without waiting for Nagle's algorithm. */
internal object SendingAtOnce : ServerSocketFactory() {
    override fun createServerSocket(): ServerSocket =
        object : ServerSocket() {
            override fun accept(): Socket = super.accept().apply { tcpNoDelay = true }
        }

    override fun createServerSocket(port: Int): ServerSocket = createServerSocket(port, 0)

    override fun createServerSocket(port: Int, backlog: Int): ServerSocket = createServerSocket(port, backlog, null)

    override fun createServerSocket(port: Int, backlog: Int, address: InetAddress?): ServerSocket =
        createServerSocket().apply { bind(InetSocketAddress(address, port), backlog) }
}
