package pl.softmil.padelBooking.gateway

import pl.softmil.padelBooking.core.PaidReservationCancelledEvents
import pl.softmil.padelBooking.core.Reservation
import pl.softmil.padelBooking.core.ReservationPaidEvents
import pl.softmil.padelBooking.core.Session
import pl.softmil.padelBooking.core.SessionCancelledEvents
import pl.softmil.padelBooking.core.SessionId

interface SessionRepository {
    fun getSessionById(sessionId: SessionId): Session
    fun saveReservation(reservation: Reservation)
    fun persistReservationPaidEvents(events: List<ReservationPaidEvents>)
    fun persistPaidReservationCancelledEvents(events: List<PaidReservationCancelledEvents>)
    fun persistSessionCancelledEvents(events: List<SessionCancelledEvents>)
}
