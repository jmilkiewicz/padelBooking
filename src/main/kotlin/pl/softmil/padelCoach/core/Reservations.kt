package pl.softmil.padelCoach.core

import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID


class Reservations(
    private val pendingReservations: List<Reservation> = emptyList(),
    private val paidReservations: List<PaidReservation> = emptyList(),
    private val pendingReservationTTL: Duration
) {

    fun isTemporaryFull(now: ZonedDateTime, sessionSize: Int): Pair<Boolean, ZonedDateTime> {
        val canBePaidReservations = findCanBePaidReservations(now)
        val oldestPending = canBePaidReservations.minBy { it.createdAt }
        val till = oldestPending.createdAt.plus(pendingReservationTTL)

        return if (paidReservations.size + canBePaidReservations.size >= sessionSize) {
            Pair(true, till)
        } else {
            Pair(false, now.plus(pendingReservationTTL))
        }
    }

    private fun findCanBePaidReservations(now: ZonedDateTime): List<Reservation> {
        val reservationsDeadline = reservationsDeadline(now)
        return pendingReservations.filter { it.canBePaid(reservationsDeadline) }
    }

    private fun reservationsDeadline(now: ZonedDateTime): ZonedDateTime = now.minus(pendingReservationTTL)


    private fun levelMatchesAgainstPending(now: ZonedDateTime): Int? {
        return findCanBePaidReservations(now).firstOrNull()?.user?.level

    }

    private fun levelMatchesAgainstPaid(): Int? {
        return paidReservations.firstOrNull()?.user?.level
    }

    fun hasAlreadySignedUp(user: User, now: ZonedDateTime): Boolean {
        return paidReservations.any { it.user.id == user.id } ||
                findCanBePaidReservations(now).any { it.user.id == user.id }
    }

    fun levelMatches(level: Int, now: ZonedDateTime): Pair<Boolean, Int> {
        val currentLevel = getCurrentLevel(now)
        return if (currentLevel != null) {
            Pair(currentLevel == level, currentLevel)
        } else {
            Pair(true, level)
        }
    }

    private fun getCurrentLevel(now: ZonedDateTime): Int? {
        return levelMatchesAgainstPaid() ?: return levelMatchesAgainstPending(now)
    }

    fun hasPaidReservationFor(id: ReservationId): Boolean = paidReservations.any { it.reservationId == id }

    private fun getPendingReservation(id: ReservationId): Reservation = pendingReservations.first { it.id == id }

    fun paidReservationFor(id: ReservationId, now: ZonedDateTime): Pair<Reservation, PaidReservation> {
        return getPendingReservation(id).let {
            Pair(
                it.copy(status = ReservationStatus.PAID),
                PaidReservation(
                    id = PaidReservationId(UUID.randomUUID()),
                    user = it.user,
                    transactionId = "someTransactionId",
                    sessionId = it.sessionId,
                    createdAt = it.createdAt,
                    paidAt = now,
                    reservationId = id,
                    //do we get it from PayPal?
                    paid = it.cost,
                    status = PaidReservationStatus.PAID
                )
            )

        }
    }

    fun getNumberOfPaidReservations(): Int = paidReservations.filter { it.status == PaidReservationStatus.PAID }.count()

    fun cancelPaidFor(user: User, now: ZonedDateTime): Pair<Reservation, PaidReservation>? {
        return paidReservations.firstOrNull { it.user.id == user.id && it.status == PaidReservationStatus.PAID }
            ?.let { paidReservation ->
                val pendingReservation = pendingReservations.first { it.id == paidReservation.reservationId }
                Pair(
                    pendingReservation.paidReservationCancelled(),
                    paidReservation.cancel(now)
                )
            }
    }

    fun getFreshestPendingReservationFor(user: User): Reservation? {
        return pendingReservations.filter { it.user.id == user.id }.maxByOrNull { it.createdAt }
    }

    fun cancelPendingReservationFor(user: User, now: ZonedDateTime): Reservation? {
        val reservationToCancel = getFreshestPendingReservationFor(user)

        return reservationToCancel?.let {
            if (reservationToCancel.canBeCancelled(reservationsDeadline(now))) {
                reservationToCancel.copy(status = ReservationStatus.USER_CANCELLED)
            } else null
        }
    }

    fun findCanBaPaidReservationFor(user: User, now: ZonedDateTime): Reservation? {
        val freshestPendingReservationFor = getFreshestPendingReservationFor(user)
        return freshestPendingReservationFor?.let {
            if (it.canBePaid(reservationsDeadline(now))) {
                it
            } else null
        }
    }

}