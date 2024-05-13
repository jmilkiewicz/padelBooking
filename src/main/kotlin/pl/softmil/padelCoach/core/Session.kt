package pl.softmil.padelCoach.core

import org.javamoney.moneta.FastMoney
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID


sealed class SessionReservationResult

class SessionInvalid(val reason: String) : SessionReservationResult()
class InvalidLevel(val levelRequired: Int) : SessionReservationResult()
object AlreadyRequested : SessionReservationResult()
object SessionFull : SessionReservationResult()


class PendingPayments(val till: ZonedDateTime) : SessionReservationResult()
class ReservationCreated(val reservation: Reservation) : SessionReservationResult()


sealed class ReservationPaidResult

data class Success(
    val events: List<ReservationPaidEvents>
) : ReservationPaidResult()

data class SessionOverflow(
    val event: ReservationPaidEvents.ReservationToBeRepaid,
) : ReservationPaidResult()


sealed interface ReservationPaidEvents {
    data class ReservationPaid(val p: Pair<Reservation, PaidReservation>) :
        ReservationPaidEvents

    data class SessionStatusUpdated(val sessionId: SessionId, val newStatus: SessionStatus) :
        ReservationPaidEvents

    data class ReservationToBeRepaid(val reservation: Reservation) :
        ReservationPaidEvents
}


sealed interface PaidReservationCancelledEvents {
    data class Cancelled(val paidReservation: PaidReservation) : PaidReservationCancelledEvents
    data class SessionStatusUpdated(val sessionId: SessionId, val newStatus: SessionStatus) :
        PaidReservationCancelledEvents
}


sealed interface PaidReservationCancelledResult {
    object Missing : PaidReservationCancelledResult
    data class Success(val events: List<PaidReservationCancelledEvents>) : PaidReservationCancelledResult
    data class TooLate(val deadLine: ZonedDateTime) : PaidReservationCancelledResult
}


interface Session {
    fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult
    fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult
    fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult
}

enum class SessionStatus {
    Open, Cancelled, Ready // we have all paid participants
}

data class SessionData(
    val id: SessionId,
    val scheduledAt: ZonedDateTime,
    val pendingReservations: List<Reservation> = emptyList(),
    val paidReservations: List<PaidReservation> = emptyList(),
    val sessionStatus: SessionStatus,
    val coach: Coach
) {

    fun canAccept(
        user: User,
        sessionSize: Int,
        now: ZonedDateTime,
        cost: FastMoney
    ): SessionReservationResult {
        if (isInThePast()) {
            return SessionInvalid("already took place")
        }

        if (sessionStatus == SessionStatus.Cancelled) {
            return SessionInvalid("session in unavailable")
        }

        if (hasAlreadySignedUp(user, now)) {
            return AlreadyRequested
        }

        val (levelMatches, requiredLevel) = levelMatches(user.level, now)
        if (!levelMatches) {
            return InvalidLevel(requiredLevel)
        }

        if (paidReservations.size == sessionSize) {
            return SessionFull
        }

        val (pendingButNotPaid, till) = pendingButNotPaidReservations(now, sessionSize)
        if (pendingButNotPaid) {
            return PendingPayments(till)
        }

        return ReservationCreated(
            Reservation(
                id = ReservationId(UUID.randomUUID()),
                user = user,
                createdAt = now,
                sessionId = id,
                cost,
                status = ReservationStatus.CREATED
            )
        )
    }

    fun paid(
        reservation: Reservation,
        now: ZonedDateTime,
        sessionSize: Int,
    ): ReservationPaidResult {
        //co jak już opłacona ???
        if (paidReservations.firstOrNull { it.reservationId == reservation.id } != null) {

        }

        //co jak już mamy komplet ?
        if (paidReservations.size == sessionSize) {
            return SessionOverflow(ReservationPaidEvents.ReservationToBeRepaid(reservation.asOverflow()))
        }
        return asSuccessfullyPaid(reservation, now, sessionSize)
    }

    fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        val paidReservation = paidReservations.find { it.user.id == user.id && it.status == PaidReservationStatus.PAID }
        return if (paidReservation == null) {
            PaidReservationCancelledResult.Missing
        } else {
            val deadline = getCancellationDeadline()
            if (now.isAfter(deadline)) {
                PaidReservationCancelledResult.TooLate(deadline)
            } else {
                success(paidReservation, now)
            }
        }
    }


    private fun asSuccessfullyPaid(
        reservation: Reservation,
        now: ZonedDateTime,
        sessionSize: Int
    ): Success {
        val pendingReservation = pendingReservations.first { it.id == reservation.id }
        val reservations = pendingReservation.asPaid("someTransactionId", now)
        val result = listOf(ReservationPaidEvents.ReservationPaid(reservations))

        val events = if (paidReservations.size + 1 == sessionSize) {
            result + listOf(ReservationPaidEvents.SessionStatusUpdated(id, SessionStatus.Ready))
        } else result

        return Success(events)
    }

    private fun getCancellationDeadline(): ZonedDateTime {
        return scheduledAt.minusDays(1)
    }

    private fun success(
        reservationToCancel: PaidReservation,
        now: ZonedDateTime
    ): PaidReservationCancelledResult.Success {
        val events = listOf(
            PaidReservationCancelledEvents.Cancelled(
                reservationToCancel.cancel(
                    now
                )
            ), PaidReservationCancelledEvents.SessionStatusUpdated(id, SessionStatus.Open)
        )
        return PaidReservationCancelledResult.Success(
            events
        )
    }

    private fun pendingButNotPaidReservations(now: ZonedDateTime, sessionSize: Int): Pair<Boolean, ZonedDateTime> {
        val lastHourPendingReservations = lastHourPendingReservations(now)
        val oldestPending = lastHourPendingReservations.minBy { it.createdAt }
        val till = oldestPending.createdAt.plusHours(1)

        return if (paidReservations.size + lastHourPendingReservations.size >= sessionSize) {
            Pair(true, till)
        } else {
            Pair(false, now.plusHours(1))
        }
    }

    private fun hasAlreadySignedUp(user: User, now: ZonedDateTime): Boolean {
        return paidReservations.any { it.user.id == user.id } ||
                lastHourPendingReservations(now).any { it.user.id == user.id }
    }

    private fun levelMatches(level: Int, now: ZonedDateTime): Pair<Boolean, Int> {
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

    private fun levelMatchesAgainstPending(now: ZonedDateTime): Int? {
        return lastHourPendingReservations(now).firstOrNull()?.user?.level

    }

    fun lastHourPendingReservations(now: ZonedDateTime): List<Reservation> {
        val oneHourAgo = now.minus(1, ChronoUnit.HOURS)
        return pendingReservations.filter { it.wasCreatedAfter(oneHourAgo) && it.isCreated() }
    }

    private fun levelMatchesAgainstPaid(): Int? {
        return paidReservations.firstOrNull()?.user?.level
    }

    private fun isInThePast(): Boolean {
        TODO("Not yet implemented")
    }
}

class OneOnOneSession(val sessionData: SessionData, val cost: FastMoney) : Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 1, now, cost)
    }

    override fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult {
        return sessionData.paid(reservation, now, 1)
    }

    override fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        return sessionData.cancelPaidReservation(user, now)
    }
}

class TwoOnOneSession(val sessionData: SessionData, val cost: FastMoney) : Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 2, now, cost)
    }

    override fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult {
        return sessionData.paid(reservation, now, 2)
    }

    override fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        return sessionData.cancelPaidReservation(user, now)
    }
}

class FourToOneSession(val sessionData: SessionData, val cost: FastMoney) :
    Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 4, now, cost)
    }

    override fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult {
        return sessionData.paid(reservation, now, 4)
    }

    override fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        return sessionData.cancelPaidReservation(user, now)
    }
}
