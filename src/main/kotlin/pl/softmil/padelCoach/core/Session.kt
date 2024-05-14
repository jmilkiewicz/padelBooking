package pl.softmil.padelCoach.core

import org.javamoney.moneta.FastMoney
import java.time.ZonedDateTime
import java.util.UUID


sealed class SessionReservationResult

class SessionInvalid(val reason: String) : SessionReservationResult()
class InvalidLevel(val levelRequired: Int) : SessionReservationResult()
data object AlreadyRequested : SessionReservationResult()
data object SessionFull : SessionReservationResult()


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
    data object Missing : PaidReservationCancelledResult
    data object SessionCancelled : PaidReservationCancelledResult
    data class Success(val events: List<PaidReservationCancelledEvents>) : PaidReservationCancelledResult
    data class TooLate(val deadLine: ZonedDateTime) : PaidReservationCancelledResult
}


sealed interface PendingReservationCancelledEvent {
    data class Cancelled(val pendingReservation: Reservation) : PendingReservationCancelledEvent
}


sealed interface PendingReservationCancelledResult {
    data object Missing : PendingReservationCancelledResult
    data class Success(val event: PendingReservationCancelledEvent) : PendingReservationCancelledResult
}

sealed class PaymentInitialisationResult {
    class PendingReservation(val reservation: Reservation) : PaymentInitialisationResult()
    data object Missing : PaymentInitialisationResult()
    data object SessionUnavailable : PaymentInitialisationResult()
}


interface Session {
    fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult
    fun reservationPaid(reservation: Reservation, now: ZonedDateTime): ReservationPaidResult
    fun initiatePayment(user: User, now: ZonedDateTime): PaymentInitialisationResult
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
        if (isInThePast(now)) {
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
        //TODO  co jak już opłacona ???
        if (reservations.hasPaidReservationFor(reservation.id)) {

        }

        if (sessionStatus == SessionStatus.Ready) {
            //już mamy komplet a nowa opłacona rezerwacja przyszła!
            //TODO czy powinienem także zapisać PaidReservation z jakimś specjalnym stanem?
            return SessionOverflow(ReservationPaidEvents.ReservationToBeRepaid(reservation.asOverflow()))
        }
        //TODO Co bedzie jak sessionStatus == Cancelled - bo coach zcancelował sesje...


        return success(reservation, now, sessionSize)
    }

    fun cancelPaidReservation(user: User, now: ZonedDateTime): PaidReservationCancelledResult {
        if (sessionStatus == SessionStatus.Cancelled) {
            return PaidReservationCancelledResult.SessionCancelled
        }
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
        val reservationsUpdated = reservations.paidReservationsFor(reservation.id, now)
        val result = listOf(ReservationPaidEvents.ReservationPaid(reservationsUpdated))

        val events = if (reservations.getNumberOfPaidReservations() + 1 == sessionSize) {
            result + listOf(ReservationPaidEvents.SessionStatusUpdated(id, SessionStatus.Ready))
        } else result

        return Success(events)
    }

    private fun getCancellationDeadline(): ZonedDateTime {
        return scheduledAt.minusDays(1)
    }

    private fun isInThePast(now: ZonedDateTime): Boolean = scheduledAt.isBefore(now)

    fun cancelPendingReservation(user: User, now: ZonedDateTime): PendingReservationCancelledResult {
        val cancelPendingReservationFor = reservations.cancelPendingReservationFor(user, now)

        return if (cancelPendingReservationFor == null) {
            PendingReservationCancelledResult.Missing
        } else {
            PendingReservationCancelledResult.Success(
                event = PendingReservationCancelledEvent.Cancelled(
                    cancelPendingReservationFor
                )
            )
        }
    }

    fun initiatePayment(user: User, now: ZonedDateTime): PaymentInitialisationResult {
        if (sessionStatus != SessionStatus.Open) {
            return PaymentInitialisationResult.SessionUnavailable
        }
        val pendingReservation = reservations.findCanBePaidReservationFor(user, now)
        return if (pendingReservation == null) {
            PaymentInitialisationResult.Missing
        } else {
            PaymentInitialisationResult.PendingReservation(pendingReservation)
        }
    }
}

class OneOnOneSession(val sessionData: SessionData, val cost: FastMoney) : Session {
    override fun createReservation(user: User, now: ZonedDateTime): SessionReservationResult {
        return sessionData.canAccept(user, 1, now, cost)
    }

    override fun initiatePayment(user: User, now: ZonedDateTime): PaymentInitialisationResult {
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

    override fun initiatePayment(user: User, now: ZonedDateTime): PaymentInitialisationResult {
        return sessionData.initiatePayment(user, now)
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

    override fun initiatePayment(user: User, now: ZonedDateTime): PaymentInitialisationResult {
        return sessionData.initiatePayment(user, now)
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
