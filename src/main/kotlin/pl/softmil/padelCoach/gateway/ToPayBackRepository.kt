package pl.softmil.padelCoach.gateway

import pl.softmil.padelCoach.core.PaidReservationCancelledEvents
import pl.softmil.padelCoach.core.ReservationPaidEvents
import pl.softmil.padelCoach.core.SessionCancelledEvents


interface ToPayBackRepository {
    fun handlePaidReservationCancelledEvents(events: List<PaidReservationCancelledEvents>)
    fun handleReservationToBeRepaid(event: ReservationPaidEvents.ReservationToBeRepaid)
    fun handleSessionCancelledEvents(events: List<SessionCancelledEvents>)
}