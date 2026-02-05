package com.openclaw.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RegistrationStateMachineTest {

  @Test
  fun transitions_toRegistered_onAck() {
    val machine = RegistrationStateMachine()
    machine.transition(RegistrationEvent.ConnectRequested)
    machine.transition(RegistrationEvent.SocketOpened)
    machine.transition(RegistrationEvent.RegisterSent)
    val result = machine.transition(RegistrationEvent.RegisterAckOk)

    assertEquals(RegistrationState.REGISTERED, result.state)
  }

  @Test
  fun transitions_toError_onAckError() {
    val machine = RegistrationStateMachine()
    machine.transition(RegistrationEvent.ConnectRequested)
    machine.transition(RegistrationEvent.SocketOpened)
    machine.transition(RegistrationEvent.RegisterSent)
    val result = machine.transition(RegistrationEvent.RegisterAckError(GatewayError("REG_UNAUTHORIZED", "nope")))

    assertEquals(RegistrationState.ERROR, result.state)
    assertNotNull(result.error)
    assertEquals("REG_UNAUTHORIZED", result.error?.code)
  }
}
