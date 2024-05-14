package pl.softmil.padelCoach.useCase

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.`is`
import org.javamoney.moneta.FastMoney
import org.junit.jupiter.api.Test
import pl.softmil.padelCoach.core.PaidReservation
import pl.softmil.padelCoach.core.PaidReservationId
import pl.softmil.padelCoach.core.PaidReservationStatus
import pl.softmil.padelCoach.core.Reservation
import pl.softmil.padelCoach.core.ReservationId
import pl.softmil.padelCoach.core.ReservationStatus
import pl.softmil.padelCoach.core.SessionStatus
import pl.softmil.padelCoach.ports.InMemoryDB
import java.time.ZonedDateTime
import java.util.UUID

class CancelSessionTest {
    private val fakeDb = InMemoryDB()
    private val sut = CancelSession(sessionRepository = fakeDb, toPayBackRepository = fakeDb)
    val now = ZonedDateTime.now()

    private val reservation = Reservation(
        ReservationId(UUID.randomUUID()),
        fakeDb.userOne,
        now.minusHours(1),
        fakeDb.sessionId,
        FastMoney.of(20, "EUR"),
        ReservationStatus.PAID
    )

    private val reservation2 = reservation.copy(
        id = ReservationId(UUID.randomUUID()),
        fakeDb.userTwo,
        status = ReservationStatus.CREATED
    )

    private val reservation3 = reservation.copy(
        id = ReservationId(UUID.randomUUID()),
        fakeDb.userTwo,
        status = ReservationStatus.EXPIRED
    )

    private val paidReservation = PaidReservation(
        id = PaidReservationId(UUID.randomUUID()),
        reservationId = reservation.id,
        user = reservation.user,
        transactionId = "sasdda",
        sessionId = reservation.sessionId,
        paidAt = now.minusHours(1),
        createdAt = now.minusHours(1),
        paid = reservation.cost,
        status = PaidReservationStatus.PAID
    )

    @Test
    fun cancelSessionSuccessfully() {
        fakeDb.paidReservations.add(paidReservation)
        fakeDb.reservations.add(reservation)
        fakeDb.reservations.add(reservation2)
        fakeDb.reservations.add(reservation3)

        val cancelSessionResult = sut.cancel(reservation.sessionId, now)

        assertThat(cancelSessionResult, Matchers.instanceOf(CancelSessionResult.Success::class.java))
        assertThat(
            fakeDb.reservations,
            containsInAnyOrder(
                reservation.copy(status = ReservationStatus.SESSION_CANCELLED),
                reservation2.copy(status = ReservationStatus.SESSION_CANCELLED),
                reservation3
            )
        )
        assertThat(
            fakeDb.paidReservations,
            containsInAnyOrder(
                paidReservation.copy(status = PaidReservationStatus.SESSION_CANCELLED, cancelledAt = now)
            )
        )
        assertThat(
            fakeDb.sessionData.sessionStatus,
            `is`(SessionStatus.Cancelled)
        )
        assertThat(
            fakeDb.toPayBackReservations,
            containsInAnyOrder(
                paidReservation.copy(
                    status = PaidReservationStatus.SESSION_CANCELLED,
                    cancelledAt = now
                )
            )
        )

    }
}