package pl.softmil.padelCoach.gateway

import pl.softmil.padelCoach.core.Reservation
import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.core.SessionId

interface SessionRepository {
    fun getSessionById(sessionId: SessionId): Session
    fun saveReservation(reservation: Reservation)

}
