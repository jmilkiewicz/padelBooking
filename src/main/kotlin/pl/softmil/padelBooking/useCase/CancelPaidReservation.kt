package pl.softmil.padelBooking.useCase

import pl.softmil.padelBooking.core.PaidReservationCancelledEvents
import pl.softmil.padelBooking.core.PaidReservationCancelledResult
import pl.softmil.padelBooking.core.SessionId
import pl.softmil.padelBooking.core.User
import pl.softmil.padelBooking.core.UserId
import pl.softmil.padelBooking.gateway.SessionRepository
import pl.softmil.padelBooking.gateway.ToPayBackRepository
import pl.softmil.padelBooking.gateway.UserRepository
import pl.softmil.padelBooking.useCase.CancelPaidReservationResult.Invalid
import pl.softmil.padelBooking.useCase.CancelPaidReservationResult.SessionCancelled
import pl.softmil.padelBooking.useCase.CancelPaidReservationResult.Success
import pl.softmil.padelBooking.useCase.CancelPaidReservationResult.TooLate
import java.time.ZonedDateTime


sealed interface CancelPaidReservationResult {
    data object Success : CancelPaidReservationResult
    data object Invalid : CancelPaidReservationResult
    data object SessionCancelled : CancelPaidReservationResult
    data class TooLate(val deadline: ZonedDateTime) : CancelPaidReservationResult
}

class CancelPaidReservation(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val toPayBackRepository: ToPayBackRepository
) {
    fun cancel(userId: UserId, sessionId: SessionId, now: ZonedDateTime): CancelPaidReservationResult {
        val user = getUserById(userId)


        val session = sessionRepository.getSessionById(sessionId)

        return when (val result = session.cancelPaidReservation(user, now)) {
            is PaidReservationCancelledResult.Missing -> Invalid
            is PaidReservationCancelledResult.TooLate -> TooLate(result.deadLine)
            is PaidReservationCancelledResult.SessionCancelled -> SessionCancelled
            is PaidReservationCancelledResult.Success -> {
                handleEvents(result.events)
                Success
            }

        }

    }

    private fun handleEvents(events: List<PaidReservationCancelledEvents>) {
        sessionRepository.persistPaidReservationCancelledEvents(events)
        toPayBackRepository.handlePaidReservationCancelledEvents(events)
    }

    private fun getUserById(userId: UserId): User = userRepository.getUserById(userId)
}