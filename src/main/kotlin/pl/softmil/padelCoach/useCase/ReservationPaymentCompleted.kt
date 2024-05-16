package pl.softmil.padelCoach.useCase

import pl.softmil.padelCoach.core.ReservationId
import pl.softmil.padelCoach.core.ReservationPaidEvents
import pl.softmil.padelCoach.core.ReservationPaidResult
import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.gateway.ResevationRepository
import pl.softmil.padelCoach.gateway.SessionRepository
import pl.softmil.padelCoach.gateway.ToPayBackRepository
import pl.softmil.padelCoach.useCase.PaymentCompletedResult.PaymentCompletedFailure
import pl.softmil.padelCoach.useCase.PaymentCompletedResult.PaymentCompletedSuccess
import pl.softmil.padelCoach.useCase.PaymentCompletedResult.SessionCancelled
import java.time.ZonedDateTime

sealed class PaymentCompletedResult {
    data class PaymentCompletedSuccess(val session: Session) : PaymentCompletedResult()
    data class SessionCancelled(val session: Session) : PaymentCompletedResult()

    //jakie parametry
    data object PaymentCompletedFailure : PaymentCompletedResult()
}

class ReservationPaymentCompleted(
    private val reservationRepository: ResevationRepository, val sessionRepository: SessionRepository,
    private val toPayBackRepository: ToPayBackRepository
) {

    fun paymentCompleted(reservationId: ReservationId, now: ZonedDateTime): PaymentCompletedResult {
        val reservation = reservationRepository.getReservationById(reservationId)
        val session = sessionRepository.getSessionById(reservation.sessionId)

        return when (val reservationPaidResult = session.reservationPaid(reservation, now)) {
            is ReservationPaidResult.SessionCancelled -> {
                handleReservationToBeRepaid(
                    reservationPaidResult.event
                )
                SessionCancelled(session)
            }

            is ReservationPaidResult.SessionOverflow -> {
                handleReservationToBeRepaid(reservationPaidResult.event)
                PaymentCompletedFailure
            }

            is ReservationPaidResult.Success -> {
                handleEvents(reservationPaidResult.events)
                PaymentCompletedSuccess(session)
            }
        }
    }

    private fun handleReservationToBeRepaid(event: ReservationPaidEvents.ReservationToBeRepaid) {
        sessionRepository.persistReservationPaidEvents(listOf(event))
        toPayBackRepository.handleReservationToBeRepaid(event)
    }


    private fun handleEvents(events: List<ReservationPaidEvents>) {
        sessionRepository.persistReservationPaidEvents(events)
    }

}