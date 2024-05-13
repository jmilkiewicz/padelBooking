package pl.softmil.padelCoach.core

import org.javamoney.moneta.FastMoney
import java.time.ZonedDateTime
import java.util.UUID

enum class ReservationStatus {
    CREATED,
    CANCELLED,
    PAID,
    TIMED_OUT,
    TERMINATED
}

data class Reservation(
    val id: ReservationId,
    val user: User,
    val createdAt: ZonedDateTime,
    val sessionId: SessionId,
    val cost: FastMoney,
    val status: ReservationStatus
) {
    fun wasCreatedAfter(ts: ZonedDateTime): Boolean = ts.isBefore(createdAt)
    fun asPaid(transactionId: String, paidAt: ZonedDateTime): PaidReservation {
        return PaidReservation(
            id = PaidReservationId(UUID.randomUUID()),
            user = user,
            transactionId = transactionId,
            sessionId = sessionId,
            createdAt = createdAt,
            paidAt = paidAt,
            reservationId = id,
            //do we get it from PayPal?
            paid = this.cost
        )
    }
}

data class PaidReservation(
    val id: PaidReservationId,
    val reservationId: ReservationId,
    val user: User,
    val transactionId: String,
    val sessionId: SessionId,
    val createdAt: ZonedDateTime,
    val paidAt: ZonedDateTime,
    val paid: FastMoney,
)