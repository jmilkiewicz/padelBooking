package pl.softmil.padelCoach.useCase

import pl.softmil.padelCoach.core.PendingReservationCancelledEvent
import pl.softmil.padelCoach.core.PendingReservationCancelledResult
import pl.softmil.padelCoach.core.ReservationStatus
import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.core.SessionId
import pl.softmil.padelCoach.core.User
import pl.softmil.padelCoach.core.UserId
import pl.softmil.padelCoach.gateway.SessionRepository
import pl.softmil.padelCoach.gateway.UserRepository
import java.time.ZonedDateTime


sealed interface CancelPendingReservationResult {
    object Success : CancelPendingReservationResult
    object Missing : CancelPendingReservationResult
    data class InvalidStatus(val status: ReservationStatus) : CancelPendingReservationResult
}

class CancelPendingReservation(
    private val sessionRepository: SessionRepository, private val userRepository: UserRepository
) {
    fun cancel(userId: UserId, sessionId: SessionId, now: ZonedDateTime): CancelPendingReservationResult {
        val user = getUserById(userId)
        val session = getSessionById(sessionId)

        val result = session.cancelPendingReservation(user, now)
        return when (result) {
            is PendingReservationCancelledResult.Missing -> CancelPendingReservationResult.Missing
            is PendingReservationCancelledResult.Success -> {
                handleEvent(result.event)
                CancelPendingReservationResult.Success
            }
        }

    }

    private fun handleEvent(events: PendingReservationCancelledEvent) {
        TODO("Not yet implemented")
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)


    private fun getUserById(userId: UserId): User = userRepository.getUserById(userId)
}