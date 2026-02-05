package com.openclaw.companion

import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Minimal unit test verifying that a register_ack failure does not incorrectly mark
 * the client as disconnected while the underlying WebSocket remains open.
 */
class GatewayClientTest {
  @Test
  fun registerAckFailureKeepsConnectedWhenSocketOpen() {
    var lastConnected: Boolean? = null
    var lastRegistered: Boolean? = null
    var lastError: String? = null
    val client = GatewayClient(
      onState = { connected, registered, error ->
        lastConnected = connected
        lastRegistered = registered
        lastError = error
      },
      onLog = {},
      getNodeId = { "node-1" },
    )
    val webSocket = FakeWebSocket()
    client.setSocketForTest(webSocket)

    client.handleIncomingMessage("""{"type":"register_ack","ok":false}""", webSocket)

    assertNotNull(lastConnected)
    assertTrue(lastConnected == true)
    assertFalse(lastRegistered == true)
    assertEquals("REGISTER_ACK_FAILED", lastError)
  }

  private class FakeWebSocket : WebSocket {
    override fun request(): Request {
      return Request.Builder().url("ws://localhost/").build()
    }

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean = true

    override fun send(bytes: ByteString): Boolean = true

    override fun close(code: Int, reason: String?): Boolean = true

    override fun cancel() = Unit
  }
}