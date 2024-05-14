package pl.softmil.padelCoach.useCase

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.javamoney.moneta.FastMoney
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.softmil.padelCoach.core.PaidReservation
import pl.softmil.padelCoach.core.PaidReservationId
import pl.softmil.padelCoach.core.PaidReservationStatus
import pl.softmil.padelCoach.core.Reservation
import pl.softmil.padelCoach.core.ReservationId
import pl.softmil.padelCoach.core.ReservationStatus
import pl.softmil.padelCoach.core.SessionStatus
import pl.softmil.padelCoach.ports.InMemoryDB
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

class CancelPaidReservationTest {
    private val fakeDb = InMemoryDB()
    private val sut = CancelPaidReservation(fakeDb, fakeDb, fakeDb)
    val now = ZonedDateTime.now()

    private val reservation = Reservation(
        ReservationId(UUID.randomUUID()),
        fakeDb.userOne,
        now.minusHours(1),
        fakeDb.sessionId,
        FastMoney.of(20, "EUR"),
        ReservationStatus.PAID
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

    @BeforeEach
    fun setUp() {
        fakeDb.reservations.add(reservation)
        fakeDb.paidReservations.add(paidReservation)
    }


    @Test
    fun successfullyCancelPaidReservation() {
        fakeDb.sessionData =
            fakeDb.sessionData.copy(
                sessionStatus = SessionStatus.Ready
            )

        val cancelPaidReservationResult = sut.cancel(reservation.user.id, reservation.sessionId, now)

        assertThat(cancelPaidReservationResult, Matchers.`is`(CancelPaidReservationResult.Success))
        assertThat(fakeDb.reservations, Matchers.contains(reservation.copy(status = ReservationStatus.PAID_CANCELLED)))
        val expectedCancelledPaidReservation =
            paidReservation.copy(status = PaidReservationStatus.USER_CANCELLED, cancelledAt = now)
        assertThat(
            fakeDb.paidReservations,
            Matchers.contains(expectedCancelledPaidReservation)
        )
        assertThat(
            fakeDb.toPayBackReservations,
            Matchers.contains(expectedCancelledPaidReservation)
        )

        assertThat(
            fakeDb.sessionData.sessionStatus,
            Matchers.`is`(SessionStatus.Open)
        )

        assertThat(
            fakeDb.toPayBackReservations,
            Matchers.contains(expectedCancelledPaidReservation)
        )
    }

    @Test
    fun cancelPaidReservationTooLate() {
        val scheduledAt = now.plusMinutes(30)
        val cancelBeforeScheduled = Duration.ofHours(1)
        fakeDb.sessionData =
            fakeDb.sessionData.copy(
                scheduledAt = scheduledAt,
                cancelBeforeScheduled = cancelBeforeScheduled,
                sessionStatus = SessionStatus.Ready
            )

        val cancelPaidReservationResult = sut.cancel(reservation.user.id, reservation.sessionId, now)

        assertThat(
            cancelPaidReservationResult,
            Matchers.`is`(CancelPaidReservationResult.TooLate(scheduledAt.minus(cancelBeforeScheduled)))
        )
        assertThat(
            fakeDb.paidReservations,
            Matchers.contains(paidReservation)
        )
        assertThat(
            fakeDb.reservations,
            Matchers.contains(reservation)
        )
        assertThat(
            fakeDb.toPayBackReservations,
            Matchers.empty()
        )
    }

    @Test
    fun cancelPaidReservationOfCancelledSession() {
        fakeDb.sessionData =
            fakeDb.sessionData.copy(
                sessionStatus = SessionStatus.Cancelled
            )

        val cancelPaidReservationResult = sut.cancel(reservation.user.id, reservation.sessionId, now)

        assertThat(
            cancelPaidReservationResult,
            Matchers.`is`(CancelPaidReservationResult.SessionCancelled)
        )
        assertThat(
            fakeDb.paidReservations,
            Matchers.contains(paidReservation)
        )
        assertThat(
            fakeDb.reservations,
            Matchers.contains(reservation)
        )
        assertThat(
            fakeDb.toPayBackReservations,
            Matchers.empty()
        )
    }


    @Test
    fun cancelPaidReservationWhichWasntPaid() {
        fakeDb.sessionData =
            fakeDb.sessionData.copy(
                sessionStatus = SessionStatus.Open
            )
        fakeDb.paidReservations.clear()

        val cancelPaidReservationResult = sut.cancel(reservation.user.id, reservation.sessionId, now)

        assertThat(
            cancelPaidReservationResult,
            Matchers.`is`(CancelPaidReservationResult.Invalid)
        )
        assertThat(
            fakeDb.paidReservations,
            Matchers.empty()
        )
        assertThat(
            fakeDb.reservations,
            Matchers.contains(reservation)
        )
        assertThat(
            fakeDb.toPayBackReservations,
            Matchers.empty()
        )
    }


}