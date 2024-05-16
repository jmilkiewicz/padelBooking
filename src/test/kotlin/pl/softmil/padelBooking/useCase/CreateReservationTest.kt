package pl.softmil.padelBooking.useCase

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.iterableWithSize
import org.javamoney.moneta.FastMoney
import org.junit.jupiter.api.Test
import pl.softmil.padelBooking.core.PaidReservation
import pl.softmil.padelBooking.core.PaidReservationId
import pl.softmil.padelBooking.core.PaidReservationStatus
import pl.softmil.padelBooking.core.Reservation
import pl.softmil.padelBooking.core.ReservationId
import pl.softmil.padelBooking.core.ReservationStatus
import pl.softmil.padelBooking.ports.InMemoryDB
import java.time.ZonedDateTime
import java.util.*

class CreateReservationTest {
    private val fakeDb = InMemoryDB()
    private val sut = CreateReservation(sessionRepository = fakeDb, userRepository = fakeDb)

    @Test
    fun createReservation() {
        val now = ZonedDateTime.now()
        val result = sut.create(fakeDb.userOne.id, fakeDb.sessionId, now)

        assertThat(result, instanceOf(CreateReservationResult.Success::class.java))
        val success = result as CreateReservationResult.Success
        assertThat(success.reservation.createdAt, `is`(now))
        assertThat(success.reservation.status, `is`(ReservationStatus.CREATED))

        assertThat(fakeDb.reservations, contains(success.reservation))
    }

    @Test
    fun createReservationTwice() {
        val now = ZonedDateTime.now()
        sut.create(fakeDb.userOne.id, fakeDb.sessionId, now)

        val result = sut.create(fakeDb.userOne.id, fakeDb.sessionId, now)

        assertThat(result, `is`(CreateReservationResult.Failure(Reason.AlreadySignedUp)))

        assertThat(fakeDb.reservations, iterableWithSize(1))
    }


    @Test
    fun createReservationOnFullyRequestedSession() {
        val now = ZonedDateTime.now()
        val twoMinutesAgo = now.minusMinutes(2)
        sut.create(fakeDb.userOne.id, fakeDb.sessionId, twoMinutesAgo)
        sut.create(fakeDb.userTwo.id, fakeDb.sessionId, now.minusMinutes(1))

        val result = sut.create(fakeDb.userThree.id, fakeDb.sessionId, now)

        assertThat(
            result,
            `is`(CreateReservationResult.OtherPaymentsInProgress(twoMinutesAgo.plus(fakeDb.duration)))
        )
    }

    @Test
    fun createReservationWhenAlreadyRequestedForDifferentLevel() {
        val now = ZonedDateTime.now()
        sut.create(fakeDb.userOne.id, fakeDb.sessionId, now)

        val result = sut.create(fakeDb.userFour.id, fakeDb.sessionId, now)

        assertThat(result, `is`(CreateReservationResult.Failure(Reason.InvalidLevel)))

        assertThat(fakeDb.reservations, iterableWithSize(1))
    }


    @Test
    fun createReservationAgainAfterCancellation() {
        val now = ZonedDateTime.now()
        val reservation = Reservation(
            ReservationId(UUID.randomUUID()),
            fakeDb.userOne,
            now.minusMinutes(1),
            fakeDb.sessionId,
            FastMoney.of(20, "EUR"),
            ReservationStatus.PAID_CANCELLED
        )

        val paidReservation = PaidReservation(
            id = PaidReservationId(UUID.randomUUID()),
            reservationId = reservation.id,
            user = reservation.user,
            transactionId = "sasdda",
            sessionId = reservation.sessionId,
            paidAt = now.minusHours(1),
            createdAt = now.minusHours(1),
            paid = reservation.cost,
            status = PaidReservationStatus.USER_CANCELLED
        )

        fakeDb.reservations.add(reservation)
        fakeDb.paidReservations.add(paidReservation)

        val result = sut.create(fakeDb.userFour.id, fakeDb.sessionId, now)

        assertThat(result, instanceOf(CreateReservationResult.Success::class.java))
    }

    //TODO reservation FULL , sessionInvalid

}