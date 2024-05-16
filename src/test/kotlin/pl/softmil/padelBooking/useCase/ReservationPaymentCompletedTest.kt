package pl.softmil.padelBooking.useCase

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.iterableWithSize
import org.javamoney.moneta.FastMoney
import org.junit.jupiter.api.Test
import pl.softmil.padelBooking.core.PaidReservationStatus
import pl.softmil.padelBooking.core.Reservation
import pl.softmil.padelBooking.core.ReservationId
import pl.softmil.padelBooking.core.ReservationStatus
import pl.softmil.padelBooking.core.SessionStatus
import pl.softmil.padelBooking.ports.InMemoryDB
import pl.softmil.padelBooking.useCase.PaymentCompletedResult.PaymentCompletedSuccess
import java.time.ZonedDateTime
import java.util.*

class ReservationPaymentCompletedTest {
    private val fakeDb = InMemoryDB()
    private val sut = ReservationPaymentCompleted(fakeDb, fakeDb, fakeDb)
    private val now = ZonedDateTime.now()
    private val reservation = Reservation(
        ReservationId(UUID.randomUUID()),
        fakeDb.userOne,
        now.minusMinutes(1),
        fakeDb.sessionId,
        FastMoney.of(20, "EUR"),
        ReservationStatus.CREATED
    )

    @Test
    fun paymentCompleted() {
        fakeDb.addReservation(reservation)

        val paymentCompletedResult = sut.paymentCompleted(reservation.id, now)
        assertThat(paymentCompletedResult, instanceOf(PaymentCompletedSuccess::class.java))

        assertThat(fakeDb.reservations, contains(reservation.copy(status = ReservationStatus.PAID)))
        assertThat(fakeDb.paidReservations, iterableWithSize(1))

        val paidReservation = fakeDb.paidReservations.first()

        assertThat(paidReservation.reservationId, equalTo(reservation.id))
        assertThat(paidReservation.paid, equalTo(reservation.cost))
        assertThat(paidReservation.status, equalTo(PaidReservationStatus.PAID))
    }

    @Test
    fun paymentsCompletedAndSessionReady() {
        val newReservation = reservation.copy(
            id = ReservationId(UUID.randomUUID()),
            user = fakeDb.userTwo
        )
        fakeDb.addReservation(reservation)
        fakeDb.addReservation(newReservation)
        sut.paymentCompleted(reservation.id, now)

        val paymentCompletedResult = sut.paymentCompleted(newReservation.id, now)

        assertThat(paymentCompletedResult, instanceOf(PaymentCompletedSuccess::class.java))
        assertThat(
            fakeDb.reservations,
            containsInAnyOrder(
                reservation.copy(status = ReservationStatus.PAID),
                newReservation.copy(status = ReservationStatus.PAID)
            )
        )
        assertThat(fakeDb.paidReservations, iterableWithSize(2))

        assertThat(fakeDb.sessionData.sessionStatus, `is`(SessionStatus.Ready))
    }

    @Test
    fun paymentsCompletedButSessionOverflow() {
        val reservation2 = reservation.copy(
            id = ReservationId(UUID.randomUUID()),
            user = fakeDb.userTwo
        )
        val reservation3 = reservation.copy(
            id = ReservationId(UUID.randomUUID()),
            user = fakeDb.userThree
        )
        fakeDb.addReservation(reservation)
        fakeDb.addReservation(reservation2)
        fakeDb.addReservation(reservation3)
        sut.paymentCompleted(reservation.id, now)
        sut.paymentCompleted(reservation2.id, now)

        val paymentCompletedResult = sut.paymentCompleted(reservation3.id, now)

        assertThat(
            paymentCompletedResult,
            instanceOf(PaymentCompletedResult.PaymentCompletedFailure::class.java)
        )
        assertThat(
            fakeDb.reservations,
            containsInAnyOrder(
                reservation.copy(status = ReservationStatus.PAID),
                reservation2.copy(status = ReservationStatus.PAID),
                reservation3.copy(status = ReservationStatus.OVERFLOW)
            )
        )
        assertThat(fakeDb.sessionData.sessionStatus, `is`(SessionStatus.Ready))

        assertThat(fakeDb.paidReservations, iterableWithSize(3))

        val overflowPaidReservation = fakeDb.paidReservations.first { it.reservationId == reservation3.id }
        assertThat(overflowPaidReservation.status, `is`(PaidReservationStatus.SESSION_OVERFLOW))

        assertThat(fakeDb.toPayBackReservations, contains(overflowPaidReservation))
    }

    @Test
    fun paymentsCompletedButSessionCancelled() {
        fakeDb.addReservation(reservation)
        fakeDb.sessionData = fakeDb.sessionData.copy(sessionStatus = SessionStatus.Cancelled)

        val paymentCompletedResult = sut.paymentCompleted(reservation.id, now)

        assertThat(
            paymentCompletedResult,
            instanceOf(PaymentCompletedResult.SessionCancelled::class.java)
        )
        assertThat(
            fakeDb.reservations,
            containsInAnyOrder(
                reservation.copy(status = ReservationStatus.SESSION_CANCELLED),
            )
        )
        val paidButSessionCancelled = fakeDb.paidReservations.first { it.reservationId == reservation.id }
        assertThat(paidButSessionCancelled.status, `is`(PaidReservationStatus.SESSION_CANCELLED))

        assertThat(fakeDb.toPayBackReservations, contains(paidButSessionCancelled))
    }


}
