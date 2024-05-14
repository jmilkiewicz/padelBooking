package pl.softmil.padelCoach.core

import org.javamoney.moneta.FastMoney
import java.time.ZonedDateTime
import java.util.UUID


sealed class SessionReservationResult

class SessionInvalid(val reason: String) : SessionReservationResult()
class InvalidLevel(val levelRequired: Int) : SessionReservationResult()
object AlreadyRequested : SessionReservationResult()
object SessionFull : SessionReservationResult()


class TemporaryFull(val till: ZonedDateTime) : SessionReservationResult()
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
    data class PendingRegistrationCancelled(val reservation: Reservation) : PaidReservationCancelledEvents
    data class SessionStatusUpdated(val sessionId: SessionId, val newStatus: SessionStatus) :
        PaidReservationCancelledEvents
}


sealed interface PaidReservationCancelledResult {
    object Missing : PaidReservationCancelledResult
    data class Success(val events: List<PaidReservationCancelledEvents>) : PaidReservationCancelledResult
    data class TooLate(val deadLine: ZonedDateTime) : PaidReservationCancelledResult
}


sealed interface PendingReservationCancelledEvent {
    data class Cancelled(val pendingReservation: Reservation) : PendingReservationCancelledEvent
}


sealed interface PendingReservationCancelledResult {
    object Missing : PendingReservationCancelledResult
    data class InvalidStatus(val state: ReservationStatus) : PendingReservationCancelledResult
    data class Success(val event: PendingReservationCancelledEvent) : PendingReservationCancelledResult
}

sealed class GetPendingReservationResult {
    class PendingReservation(val reservation: Reservation) : GetPendingReservationResult()
    object Missing : GetPendingReservationResult()
}


interface Session {
    fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult
    fun getPendingReservation(user: User): GetPendingReservationResult
    fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult
    fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult
    fun cancelPendingReservation(user: User, now: ZonedDateTime): PendingReservationCancelledResult
}

enum class SessionStatus {
    Open, Cancelled, Ready // we have all paid participants
}

data class SessionData(
    val id: SessionId,
    val scheduledAt: ZonedDateTime,
    val reservations: Reservations,
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

        if (sessionStatus == SessionStatus.Ready) {
            return SessionFull
        }

        if (reservations.hasAlreadySignedUp(user, now)) {
            return AlreadyRequested
        }

        val (levelMatches, requiredLevel) = reservations.levelMatches(user.level, now)
        if (!levelMatches) {
            return InvalidLevel(requiredLevel)
        }

        val (isTemporaryFull, till) = reservations.isTemporaryFull(now, sessionSize)
        if (isTemporaryFull) {
            return TemporaryFull(till)
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
        if (reservations.hasPaidReservationFor(reservation.id)) {

        }

        //co jak już mamy komplet ?
        if (sessionStatus == SessionStatus.Ready) {
            return SessionOverflow(ReservationPaidEvents.ReservationToBeRepaid(reservation.asOverflow()))
        }
        //Co bedzie jak sessionStatus == Cancelled - bo coach zcancelował sesje...
        return success(reservation, now, sessionSize)
    }

    fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        //co będzie jak session jest cancelled bo coach zcancellował sesje?

        val deadline = getCancellationDeadline()
        if (now.isAfter(deadline)) {
            return PaidReservationCancelledResult.TooLate(deadline)
        }

        val cancellations = reservations.cancelPaidFor(user, now)
        return if (cancellations == null) {
            PaidReservationCancelledResult.Missing
        } else {
            val events = listOf(
                PaidReservationCancelledEvents.Cancelled(cancellations.second),
                PaidReservationCancelledEvents.PendingRegistrationCancelled(cancellations.first),
                PaidReservationCancelledEvents.SessionStatusUpdated(id, SessionStatus.Open)
            )
            PaidReservationCancelledResult.Success(
                events
            )
        }
    }


    private fun success(
        reservation: Reservation,
        now: ZonedDateTime,
        sessionSize: Int
    ): Success {
        val reservationsUpdated = reservations.paidReservationFor(reservation.id, now)
        val result = listOf(ReservationPaidEvents.ReservationPaid(reservationsUpdated))

        val events = if (reservations.getNumberOfPaidReservations() + 1 == sessionSize) {
            result + listOf(ReservationPaidEvents.SessionStatusUpdated(id, SessionStatus.Ready))
        } else result

        return Success(events)
    }

    private fun getCancellationDeadline(): ZonedDateTime {
        return scheduledAt.minusDays(1)
    }

    private fun isInThePast(): Boolean {
        TODO("Not yet implemented")
    }

    fun cancelPendingReservation(user: User, now: ZonedDateTime): PendingReservationCancelledResult {
        val reservationToCancel =
            reservations.cancelPendingFor(user)

        return if (reservationToCancel == null) {
            PendingReservationCancelledResult.Missing
        } else {
            if (reservationToCancel.status != ReservationStatus.CREATED) {
                PendingReservationCancelledResult.InvalidStatus(reservationToCancel.status)
            } else {
                PendingReservationCancelledResult.Success(
                    event = PendingReservationCancelledEvent.Cancelled(
                        reservationToCancel.copy(status = ReservationStatus.USER_CANCELLED)
                    )
                )
            }
        }
    }

    fun getPendingReservationFor(user: User): GetPendingReservationResult {
        val pendingReservation = reservations.getPendingReservationFor(user)
        return if (pendingReservation == null) {
            GetPendingReservationResult.Missing
        } else {
            GetPendingReservationResult.PendingReservation(pendingReservation)
        }
    }
}

class OneOnOneSession(val sessionData: SessionData, val cost: FastMoney) : Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 1, now, cost)
    }

    override fun getPendingReservation(user: User): GetPendingReservationResult {
        TODO("Not yet implemented")
    }

    override fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult {
        return sessionData.paid(reservation, now, 1)
    }

    override fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        return sessionData.cancelPaidReservation(user, now)
    }

    override fun cancelPendingReservation(user: User, now: ZonedDateTime): PendingReservationCancelledResult {
        return sessionData.cancelPendingReservation(user, now)
    }
}

class TwoOnOneSession(val sessionData: SessionData, val cost: FastMoney) : Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 2, now, cost)
    }

    override fun getPendingReservation(user: User): GetPendingReservationResult {
        TODO("Not yet implemented")
    }

    override fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult {
        return sessionData.paid(reservation, now, 2)
    }

    override fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        return sessionData.cancelPaidReservation(user, now)
    }

    override fun cancelPendingReservation(user: User, now: ZonedDateTime): PendingReservationCancelledResult {
        return sessionData.cancelPendingReservation(user, now)
    }
}

class FourToOneSession(val sessionData: SessionData, val cost: FastMoney) :
    Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 4, now, cost)
    }

    override fun getPendingReservation(user: User): GetPendingReservationResult {
        return sessionData.getPendingReservationFor(user)
    }

    override fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult {
        return sessionData.paid(reservation, now, 4)
    }

    override fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        return sessionData.cancelPaidReservation(user, now)
    }

    override fun cancelPendingReservation(user: User, now: ZonedDateTime): PendingReservationCancelledResult {
        return sessionData.cancelPendingReservation(user, now)
    }

}
