package pl.softmil.padelCoach.gateway

import pl.softmil.padelCoach.core.Reservation
import pl.softmil.padelCoach.core.ReservationId


interface ResevationRepository {
    fun getReservationById(reservationId: ReservationId): Reservation
}