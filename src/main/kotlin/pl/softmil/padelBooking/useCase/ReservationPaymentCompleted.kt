package pl.softmil.padelBooking.useCase

import pl.softmil.padelBooking.core.ReservationId
import pl.softmil.padelBooking.core.ReservationPaidEvents
import pl.softmil.padelBooking.core.ReservationPaidResult
import pl.softmil.padelBooking.core.Session
import pl.softmil.padelBooking.gateway.ResevationRepository
import pl.softmil.padelBooking.gateway.SessionRepository
import pl.softmil.padelBooking.gateway.ToPayBackRepository
import pl.softmil.padelBooking.useCase.PaymentCompletedResult.PaymentCompletedFailure
import pl.softmil.padelBooking.useCase.PaymentCompletedResult.PaymentCompletedSuccess
import pl.softmil.padelBooking.useCase.PaymentCompletedResult.SessionCancelled
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