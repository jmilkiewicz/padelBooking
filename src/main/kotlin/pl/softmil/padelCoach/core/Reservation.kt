package pl.softmil.padelCoach.core

import org.javamoney.moneta.FastMoney
import java.time.ZonedDateTime

enum class ReservationStatus {
    CREATED,
    USER_CANCELLED,
    PAID,
    EXPIRED,
    OVERFLOW,
    PAID_CANCELLED
}

data class Reservation(
    val id: ReservationId,
    val user: User,
    val createdAt: ZonedDateTime,
    val sessionId: SessionId,
    val cost: FastMoney,
    val status: ReservationStatus
) {
    fun asOverflow(): Reservation {
        return this.copy(status = ReservationStatus.OVERFLOW)
    }

    fun canBeCancelled(deadline: ZonedDateTime): Boolean {
        return canBePaid(deadline)
    }

    fun canBePaid(deadline: ZonedDateTime): Boolean {
        return (isCreated() && !isExpired(deadline))
    }

    private fun isExpired(deadline: ZonedDateTime): Boolean = createdAt.isBefore(deadline)

    private fun isCreated(): Boolean = this.status == ReservationStatus.CREATED

    fun paidReservationCancelled(): Reservation {
        return this.copy(status = ReservationStatus.PAID_CANCELLED)
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