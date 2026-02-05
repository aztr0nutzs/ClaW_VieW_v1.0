package com.openclaw.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayProtocolTest {

  @Test
  fun registerMessage_serializesAndParses() {
    val msg = GatewayMessage.Register(
      version = 1,
      nodeId = "node-123",
      capabilities = listOf(Capability("camsnap", 1)),
    )

    val json = msg.toJson()
    val parsed = GatewayMessage.fromJson(json) as GatewayMessage.Register

    assertEquals(msg.version, parsed.version)
    assertEquals(msg.nodeId, parsed.nodeId)
    assertEquals(1, parsed.capabilities.size)
    assertEquals("camsnap", parsed.capabilities.first().name)
  }

  @Test
  fun registerAck_error_parses() {
    val error = GatewayError("REG_BAD_SCHEMA", "bad")
    val ack = GatewayMessage.RegisterAck(ok = false, error = error)
    val parsed = GatewayMessage.fromJson(ack.toJson()) as GatewayMessage.RegisterAck

    assertTrue(!parsed.ok)
    assertEquals("REG_BAD_SCHEMA", parsed.error?.code)
  }
}
