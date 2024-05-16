package pl.softmil.padelBooking.gateway

import pl.softmil.padelBooking.core.Reservation
import pl.softmil.padelBooking.core.ReservationId


interface ResevationRepository {
    fun getReservationById(reservationId: ReservationId): Reservation
}