package pl.softmil.padelBooking.useCase

import pl.softmil.padelBooking.core.PendingReservationCancelledEvent
import pl.softmil.padelBooking.core.PendingReservationCancelledResult
import pl.softmil.padelBooking.core.ReservationStatus
import pl.softmil.padelBooking.core.Session
import pl.softmil.padelBooking.core.SessionId
import pl.softmil.padelBooking.core.User
import pl.softmil.padelBooking.core.UserId
import pl.softmil.padelBooking.gateway.SessionRepository
import pl.softmil.padelBooking.gateway.UserRepository
import java.time.ZonedDateTime


sealed interface CancelPendingReservationResult {
    data object Success : CancelPendingReservationResult
    data object Missing : CancelPendingReservationResult
    data class InvalidStatus(val status: ReservationStatus) : CancelPendingReservationResult
}

class CancelPendingReservation(
    private val sessionRepository: SessionRepository, private val userRepository: UserRepository
) {
    fun cancel(userId: UserId, sessionId: SessionId, now: ZonedDateTime): CancelPendingReservationResult {
        val user = getUserById(userId)
        val session = getSessionById(sessionId)

        return when (val result = session.cancelPendingReservation(user, now)) {
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