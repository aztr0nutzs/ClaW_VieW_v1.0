package com.openclaw.companion

sealed class RegistrationEvent {
  data object ConnectRequested : RegistrationEvent()
  data object SocketOpened : RegistrationEvent()
  data object RegisterSent : RegistrationEvent()
  data object RegisterAckOk : RegistrationEvent()
  data class RegisterAckError(val error: GatewayError) : RegistrationEvent()
  data object RegisterTimeout : RegistrationEvent()
  data class SocketClosed(val code: Int, val reason: String) : RegistrationEvent()
  data class SocketFailure(val message: String) : RegistrationEvent()
  data object DisconnectRequested : RegistrationEvent()
}

data class RegistrationTransition(
  val state: RegistrationState,
  val error: GatewayError? = null,
)

class RegistrationStateMachine(initial: RegistrationState = RegistrationState.DISCONNECTED) {
  var state: RegistrationState = initial
    private set

  fun transition(event: RegistrationEvent): RegistrationTransition {
    val next = when (event) {
      is RegistrationEvent.ConnectRequested -> RegistrationTransition(RegistrationState.CONNECTING)
      is RegistrationEvent.SocketOpened -> RegistrationTransition(RegistrationState.CONNECTED_UNREGISTERED)
      is RegistrationEvent.RegisterSent -> RegistrationTransition(RegistrationState.REGISTERING)
      is RegistrationEvent.RegisterAckOk -> RegistrationTransition(RegistrationState.REGISTERED)
      is RegistrationEvent.RegisterAckError -> RegistrationTransition(RegistrationState.ERROR, event.error)
      is RegistrationEvent.RegisterTimeout -> RegistrationTransition(RegistrationState.ERROR, GatewayError("REG_TIMEOUT", "Registration timed out"))
      is RegistrationEvent.SocketClosed -> RegistrationTransition(RegistrationState.DISCONNECTED, GatewayError("WS_CLOSED", "Socket closed"))
      is RegistrationEvent.SocketFailure -> RegistrationTransition(RegistrationState.ERROR, GatewayError("WS_PROTOCOL_ERROR", event.message))
      is RegistrationEvent.DisconnectRequested -> RegistrationTransition(RegistrationState.DISCONNECTED)
    }
    state = next.state
    return next
  }
}
