package pl.softmil.padelCoach.core

import org.javamoney.moneta.FastMoney
import java.time.ZonedDateTime
import java.util.UUID

enum class ReservationStatus {
    CREATED,
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
    fun asPaid(transactionId: String, paidAt: ZonedDateTime): Pair<Reservation, PaidReservation> {
        return Pair(
            this.copy(status = ReservationStatus.PAID),
            PaidReservation(
                id = PaidReservationId(UUID.randomUUID()),
                user = user,
                transactionId = transactionId,
                sessionId = sessionId,
                createdAt = createdAt,
                paidAt = paidAt,
                reservationId = id,
                //do we get it from PayPal?
                paid = this.cost,
                status = PaidReservationStatus.PAID
            )
        )
    }

    fun isCreated(): Boolean = this.status == ReservationStatus.CREATED
    fun asOverflow(): Reservation {
        return this.copy(status = ReservationStatus.TERMINATED)
    }
}

data class PaidReservation(
    val id: PaidReservationId,
    val reservationId: ReservationId,
    val user: User,
    val transactionId: String? = null,
    val sessionId: SessionId,
    val createdAt: ZonedDateTime,
    val paidAt: ZonedDateTime,
    val paid: FastMoney,
    val status: PaidReservationStatus,
    val cancelledAt: ZonedDateTime? = null
) {
    fun cancel(now: ZonedDateTime): PaidReservation {
        return this.copy(status = PaidReservationStatus.USER_CANCELLED, cancelledAt = now)
    }
}


enum class PaidReservationStatus {
    PAID,
    USER_CANCELLED
}