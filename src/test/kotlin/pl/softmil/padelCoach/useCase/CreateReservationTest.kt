package pl.softmil.padelCoach.useCase

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.iterableWithSize
import org.junit.jupiter.api.Test
import pl.softmil.padelCoach.core.ReservationStatus
import pl.softmil.padelCoach.ports.InMemoryDB
import java.time.ZonedDateTime

class CreateReservationTest {
    private val fakeDb = InMemoryDB()
    private val sut = CreateReservation(sessionRepository = fakeDb, userRepository = fakeDb)

    @Test
    fun createReservation() {
        val now = ZonedDateTime.now()
        val result = sut.create(fakeDb.userOne.id, fakeDb.sessionId, now)

        assertThat(result, Matchers.instanceOf(CreateReservationResult.Success::class.java))
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
        sut.create(fakeDb.userOne.id, fakeDb.sessionId, now)
        sut.create(fakeDb.userTwo.id, fakeDb.sessionId, now)

        val result = sut.create(fakeDb.userThree.id, fakeDb.sessionId, now)

        assertThat(result, `is`(CreateReservationResult.OtherPaymentsInProgress(now.plus(fakeDb.duration))))

        assertThat(fakeDb.reservations, iterableWithSize(2))
    }

    @Test
    fun createReservationWhenAlreadyRequestedForDifferentLevel() {
        val now = ZonedDateTime.now()
        sut.create(fakeDb.userOne.id, fakeDb.sessionId, now)

        val result = sut.create(fakeDb.userFour.id, fakeDb.sessionId, now)

        assertThat(result, `is`(CreateReservationResult.Failure(Reason.InvalidLevel)))

        assertThat(fakeDb.reservations, iterableWithSize(1))
    }

    //TODO reservation FULL , sessionInvalid

}