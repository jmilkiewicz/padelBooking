package pl.softmil.padelCoach.useCase

import pl.softmil.padelCoach.core.PaidReservationCancelledEvents
import pl.softmil.padelCoach.core.PaidReservationCancelledResult
import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.core.SessionId
import pl.softmil.padelCoach.core.User
import pl.softmil.padelCoach.core.UserId
import pl.softmil.padelCoach.gateway.SessionRepository
import pl.softmil.padelCoach.gateway.UserRepository
import java.time.ZonedDateTime


sealed interface CancelReservationResult {
    object Success : CancelReservationResult
    object Invalid : CancelReservationResult
    data class TooLate(val deadline: ZonedDateTime) : CancelReservationResult
}

class CancelReservation(
    private val sessionRepository: SessionRepository, private val userRepository: UserRepository
) {
    fun cancel(userId: UserId, sessionId: SessionId, now: ZonedDateTime): CancelReservationResult {
        val user = getUserById(userId)
        val session = getSessionById(sessionId)

        val result = session.cancelPaidReservation(user, now)
        return when (result) {
            is PaidReservationCancelledResult.Missing -> CancelReservationResult.Invalid
            is PaidReservationCancelledResult.TooLate -> CancelReservationResult.TooLate(result.deadLine)
            is PaidReservationCancelledResult.Success -> {
                handleEvents(result.events)
                CancelReservationResult.Success
            }
        }

    }

    private fun handleEvents(events: List<PaidReservationCancelledEvents>): CancelReservationResult {
        TODO("Not yet implemented")
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)


    private fun getUserById(userId: UserId): User = userRepository.getUserById(userId)
}