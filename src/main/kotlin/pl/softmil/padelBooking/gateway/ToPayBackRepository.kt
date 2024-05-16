package pl.softmil.padelBooking.gateway

import pl.softmil.padelBooking.core.PaidReservationCancelledEvents
import pl.softmil.padelBooking.core.ReservationPaidEvents
import pl.softmil.padelBooking.core.SessionCancelledEvents


interface ToPayBackRepository {
    fun handlePaidReservationCancelledEvents(events: List<PaidReservationCancelledEvents>)
    fun handleReservationToBeRepaid(event: ReservationPaidEvents.ReservationToBeRepaid)
    fun handleSessionCancelledEvents(events: List<SessionCancelledEvents>)
}