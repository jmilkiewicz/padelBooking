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
    val event: ReservationToBeRepaid,
) : ReservationPaidResult()


sealed interface ReservationPaidEvents

data class ReservationPaid(val p: Pair<Reservation, PaidReservation>) :
    ReservationPaidEvents

data class SessionMembersCompleted(val sessionId: SessionId) :
    ReservationPaidEvents

data class ReservationToBeRepaid(val reservation: Reservation) :
    ReservationPaidEvents


interface Session {
    fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult
    fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult
}

enum class Status {
    Open, Cancelled, Ready // we have all paid participants
}

data class SessionData(
    val id: SessionId,
    val scheduledAt: ZonedDateTime,
    val pendingReservations: List<Reservation> = emptyList(),
    val paidReservations: List<PaidReservation> = emptyList(),
    val status: Status,
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

        if (status != Status.Open) {
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
            return SessionOverflow(ReservationToBeRepaid(reservation.asOverflow()))
        }


        val pendingReservation = pendingReservations.first { it.id == reservation.id }
        val reservations = pendingReservation.asPaid("someTransactionId", now)
        val result = listOf(ReservationPaid(reservations))

        val events = if (paidReservations.size + 1 == sessionSize) {
            result + listOf(SessionMembersCompleted(id))
        } else result

        return Success(events)
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
}

class TwoOnOneSession(val sessionData: SessionData, val cost: FastMoney) : Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 2, now, cost)
    }

    override fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult {
        return sessionData.paid(reservation, now, 2)
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
}
