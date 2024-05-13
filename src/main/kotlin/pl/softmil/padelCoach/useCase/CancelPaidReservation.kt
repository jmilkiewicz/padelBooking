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


sealed interface CancelPaidReservationResult {
    object Success : CancelPaidReservationResult
    object Invalid : CancelPaidReservationResult
    data class TooLate(val deadline: ZonedDateTime) : CancelPaidReservationResult
}

class CancelPaidReservation(
    private val sessionRepository: SessionRepository, private val userRepository: UserRepository
) {
    fun cancel(userId: UserId, sessionId: SessionId, now: ZonedDateTime): CancelPaidReservationResult {
        val user = getUserById(userId)
        val session = getSessionById(sessionId)

        val result = session.cancelPaidReservation(user, now)
        return when (result) {
            is PaidReservationCancelledResult.Missing -> CancelPaidReservationResult.Invalid
            is PaidReservationCancelledResult.TooLate -> CancelPaidReservationResult.TooLate(result.deadLine)
            is PaidReservationCancelledResult.Success -> {
                handleEvents(result.events)
                CancelPaidReservationResult.Success
            }
        }

    }

    private fun handleEvents(events: List<PaidReservationCancelledEvents>): CancelPaidReservationResult {
        TODO("Not yet implemented")
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)


    private fun getUserById(userId: UserId): User = userRepository.getUserById(userId)
}