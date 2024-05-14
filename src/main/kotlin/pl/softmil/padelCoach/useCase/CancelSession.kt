package pl.softmil.padelCoach.useCase

import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.core.SessionCancellationResult
import pl.softmil.padelCoach.core.SessionCancelledEvents
import pl.softmil.padelCoach.core.SessionId
import pl.softmil.padelCoach.gateway.SessionRepository
import pl.softmil.padelCoach.useCase.CancelSessionResult.SessionAlreadyCancelled
import java.time.ZonedDateTime


sealed interface CancelSessionResult {
    data object SessionAlreadyCancelled : CancelSessionResult
    data object Success : CancelSessionResult
}

class CancelSession(
    private val sessionRepository: SessionRepository
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
        //store to DB and trigger repay
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)
}