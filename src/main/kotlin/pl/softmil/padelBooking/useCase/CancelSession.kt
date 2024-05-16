package pl.softmil.padelBooking.useCase

import pl.softmil.padelBooking.core.Session
import pl.softmil.padelBooking.core.SessionCancellationResult
import pl.softmil.padelBooking.core.SessionCancelledEvents
import pl.softmil.padelBooking.core.SessionId
import pl.softmil.padelBooking.gateway.SessionRepository
import pl.softmil.padelBooking.gateway.ToPayBackRepository
import pl.softmil.padelBooking.useCase.CancelSessionResult.SessionAlreadyCancelled
import java.time.ZonedDateTime


sealed interface CancelSessionResult {
    data object SessionAlreadyCancelled : CancelSessionResult
    data object Success : CancelSessionResult
}

class CancelSession(
    private val sessionRepository: SessionRepository, private val toPayBackRepository: ToPayBackRepository
) {
    fun cancel(sessionId: SessionId, now: ZonedDateTime): CancelSessionResult {
        val session = getSessionById(sessionId)

        return when (val result = session.cancelSession(now)) {
            is SessionCancellationResult.SessionAlreadyCancelled -> SessionAlreadyCancelled
            is SessionCancellationResult.Success -> {
                handleEvents(result.events)
                CancelSessionResult.Success
            }
        }
    }

    private fun handleEvents(events: List<SessionCancelledEvents>) {
        sessionRepository.persistSessionCancelledEvents(events)
        toPayBackRepository.handleSessionCancelledEvents(events)
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)
}