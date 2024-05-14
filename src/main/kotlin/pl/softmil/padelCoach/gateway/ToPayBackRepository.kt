package pl.softmil.padelCoach.gateway

import pl.softmil.padelCoach.core.PaidReservation


interface ToPayBackRepository {
    fun payBack(reservation: PaidReservation)
}