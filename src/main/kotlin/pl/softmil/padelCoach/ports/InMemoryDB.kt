package pl.softmil.padelCoach.ports

import org.javamoney.moneta.FastMoney
import pl.softmil.padelCoach.core.Coach
import pl.softmil.padelCoach.core.PaidReservation
import pl.softmil.padelCoach.core.PaidReservationCancelledEvents
import pl.softmil.padelCoach.core.Reservation
import pl.softmil.padelCoach.core.ReservationId
import pl.softmil.padelCoach.core.ReservationPaidEvents
import pl.softmil.padelCoach.core.Reservations
import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.core.SessionCancelledEvents
import pl.softmil.padelCoach.core.SessionData
import pl.softmil.padelCoach.core.SessionId
import pl.softmil.padelCoach.core.SessionStatus
import pl.softmil.padelCoach.core.TwoOnOneSession
import pl.softmil.padelCoach.core.User
import pl.softmil.padelCoach.core.UserId
import pl.softmil.padelCoach.gateway.ResevationRepository
import pl.softmil.padelCoach.gateway.SessionRepository
import pl.softmil.padelCoach.gateway.ToPayBackRepository
import pl.softmil.padelCoach.gateway.UserRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID


class InMemoryDB : SessionRepository, ResevationRepository, UserRepository, ToPayBackRepository {
    val userOne = User(UserId(UUID.randomUUID()), "user1", "surname1", "email@user1.one", 3)
    val userTwo = User(UserId(UUID.randomUUID()), "user2", "surname2", "email@user2.one", 3)
    val userThree = User(UserId(UUID.randomUUID()), "user3", "surname3", "email@user3.one", 3)
    val userFour = User(UserId(UUID.randomUUID()), "user4", "surname4", "email@user4.one", 2)

    val users = mapOf(userOne.id to userOne, userTwo.id to userTwo, userThree.id to userThree, userFour.id to userFour)

    val reservations: MutableList<Reservation> = mutableListOf()
    val paidReservations: MutableList<PaidReservation> = mutableListOf()
    val sessionId = SessionId(UUID.randomUUID())
    val duration = Duration.ofMinutes(10)
    val coach = Coach("Sam", "Chile", "sam@chile.com")
    var sessionData =
        SessionData(
            id = sessionId,
            scheduledAt = ZonedDateTime.now().plusWeeks(1),
            reservations = Reservations(
                paidReservations = paidReservations,
                pendingReservations = reservations,
                pendingReservationTTL = duration
            ),
            coach = coach,
            sessionStatus = SessionStatus.Open,
        )

    val toPayBackReservations = mutableListOf<PaidReservation>()


    override fun getReservationById(reservationId: ReservationId): Reservation {
        return reservations.first { it.id == reservationId }
    }

    override fun getSessionById(sessionId: SessionId): Session {
        return TwoOnOneSession(sessionData, FastMoney.of(20, "EUR"))
    }

    override fun saveReservation(reservation: Reservation) {
        reservations.add(reservation)
    }

    override fun persistReservationPaidEvents(events: List<ReservationPaidEvents>) {
        events.forEach { event ->
            when (event) {
                is ReservationPaidEvents.ReservationPaid -> {
                    updateReservation(event.reservation)

                    paidReservations.add(event.paidReservation)
                }

                is ReservationPaidEvents.ReservationToBeRepaid -> {
                    updateReservation(event.reservation)

                    paidReservations.add(event.paidReservation)
                }

                is ReservationPaidEvents.SessionStatusUpdated -> {
                    updateSessionStatus(event.newStatus)
                }
            }
        }
    }

    override fun persistPaidReservationCancelledEvents(events: List<PaidReservationCancelledEvents>) {
        events.forEach { event ->
            when (event) {
                is PaidReservationCancelledEvents.Cancelled -> {
                    updatePaidReservation(event.paidReservation)
                }

                is PaidReservationCancelledEvents.PendingRegistrationCancelled -> {
                    updateReservation(event.reservation)
                }

                is PaidReservationCancelledEvents.SessionStatusUpdated -> {
                    updateSessionStatus(event.newStatus)
                }
            }
        }
    }

    private fun updateSessionStatus(sessionStatus: SessionStatus) {
        sessionData = sessionData.copy(sessionStatus = sessionStatus)
    }

    private fun updatePaidReservation(paidReservation: PaidReservation) {
        paidReservations.removeIf { it.id == paidReservation.id }
        paidReservations.add(paidReservation)
    }

    private fun updateReservation(reservation: Reservation) {
        reservations.removeIf { it.id == reservation.id }
        reservations.add(reservation)
    }

    override fun persistSessionCancelledEvents(events: List<SessionCancelledEvents>) {
        events.forEach { event ->
            when (event) {
                is SessionCancelledEvents.PaidReservationsToCancel -> {
                    event.reservations.forEach { updatePaidReservation(it) }
                }

                is SessionCancelledEvents.PendingReservationsToCancel -> {
                    event.reservations.forEach { updateReservation(it) }
                }

                is SessionCancelledEvents.SessionUpdateEvent -> {
                    updateSessionStatus(event.status)
                }
            }
        }
    }

    override fun getUserById(userId: UserId): User = users.get(userId)!!

    fun addReservation(res: Reservation) {
        reservations.add(res)
    }

    override fun payBack(reservation: PaidReservation) {
        toPayBackReservations.add(reservation)
    }

    override fun payBack(reservations: List<PaidReservation>) {
        toPayBackReservations.addAll(reservations)
    }


}