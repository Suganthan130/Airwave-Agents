package lk.sugaapps.airwave.service

/**
 * Decides whether the broadcast foreground service should keep running or tear
 * itself down, based purely on the engine's connection state + message. Ported
 * from the core app so both clients share the exact same lifecycle rules.
 *
 * The key rule: the disconnected state (2) is also the *initial* and the
 * *reconnecting* state, so we only stop on a genuine, non-recoverable error —
 * never on a timeout/network drop (which should keep retrying) and never on the
 * plain "Disconnected"/empty message present before the stream connects.
 */
class ServiceLogicMonitor {

    enum class ServiceAction {
        STOP_SERVICE,
        UPDATE_NOTIFICATION,
        NONE
    }

    fun determineAction(connectionState: Int, connectionMessage: String): ServiceAction {
        // connectionState: 0=Connecting, 1=Connected, 2=Disconnected
        if (connectionState == 2) {
            // Timeouts / network / unreachable are recoverable — keep retrying.
            val isTimeout = connectionMessage.contains("timed out", ignoreCase = true) ||
                connectionMessage.contains("unreachable", ignoreCase = true) ||
                connectionMessage.contains("network", ignoreCase = true)

            if ((connectionMessage.contains("Error") ||
                    connectionMessage.contains("Failed") ||
                    connectionMessage.contains("Unauthorized")) && !isTimeout
            ) {
                return ServiceAction.STOP_SERVICE
            }
        }

        return ServiceAction.UPDATE_NOTIFICATION
    }
}
