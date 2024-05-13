package pl.softmil.padelCoach.useCase

import pl.softmil.padelCoach.core.ReservationId
import pl.softmil.padelCoach.core.ReservationPaidEvents
import pl.softmil.padelCoach.core.ReservationToBeRepaid
import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.core.SessionOverflow
import pl.softmil.padelCoach.core.Success
import pl.softmil.padelCoach.gateway.ResevationRepository
import pl.softmil.padelCoach.gateway.SessionRepository
import java.time.ZonedDateTime

sealed class PaymentCompletedResult

data class PaymentCompletedSuccess(val session: Session) : PaymentCompletedResult()

//jakie parametry
object PaymentCompletedFailure : PaymentCompletedResult()

class ReservationPaymentCompleted(
    private val reservationRepository: ResevationRepository, val sessionRepository: SessionRepository
) {

    fun paymentCompleted(reservationId: ReservationId, now: ZonedDateTime): PaymentCompletedResult {
        val reservation = reservationRepository.getReservationById(reservationId)
        val session = sessionRepository.getSessionById(reservation.sessionId)

        val reservationPaidResult = session.reservationPaid(reservation, now)

        return when (reservationPaidResult) {
            is Success -> {
                handleEvents(reservationPaidResult.events)
                PaymentCompletedSuccess(session)
            }

            is SessionOverflow -> {
                returnPayment(reservationPaidResult.event)
                PaymentCompletedFailure
            }
        }

    }

    private fun returnPayment(event: ReservationToBeRepaid) {
        handleEvents(listOf(event))

        //initiate returnPayment for user:
        TODO("Not yet implemented")
    }


    private fun handleEvents(events: List<ReservationPaidEvents>) {
        TODO("Not yet implemented")
    }

}