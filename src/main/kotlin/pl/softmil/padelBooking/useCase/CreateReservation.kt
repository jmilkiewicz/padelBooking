package pl.softmil.padelBooking.useCase

import pl.softmil.padelBooking.core.*
import pl.softmil.padelBooking.gateway.SessionRepository
import pl.softmil.padelBooking.gateway.UserRepository
import pl.softmil.padelBooking.useCase.CreateReservationResult.Failure
import pl.softmil.padelBooking.useCase.CreateReservationResult.OtherPaymentsInProgress
import pl.softmil.padelBooking.useCase.CreateReservationResult.Success
import java.time.ZonedDateTime


sealed interface CreateReservationResult {
    data class Success(val reservation: Reservation) : CreateReservationResult
    data class Failure(val reason: Reason) : CreateReservationResult
    data class OtherPaymentsInProgress(val checkAfter: ZonedDateTime) : CreateReservationResult
}

enum class Reason {
    InvalidLevel, Invalid, Full, AlreadySignedUp
}

class CreateReservation(
    private val sessionRepository: SessionRepository, private val userRepository: UserRepository
) {
    fun create(userId: UserId, sessionId: SessionId, now: ZonedDateTime): CreateReservationResult {
        val user = getUserById(userId)
        val session = getSessionById(sessionId)

        return when (val result = session.createReservation(user, now)) {
            is ReservationCreated -> {
                saveReservation(result.reservation)
                Success(reservation = result.reservation)

            }

            is SessionInvalid -> Failure(Reason.Invalid)
            is SessionFull -> Failure(Reason.Full)
            is InvalidLevel -> Failure(Reason.InvalidLevel)
            is AlreadyRequested -> Failure(Reason.AlreadySignedUp)
            is TemporaryFull -> OtherPaymentsInProgress(result.till)
        }

    }

    private fun saveReservation(reservation: Reservation) {
        sessionRepository.saveReservation(reservation)
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)


    private fun getUserById(userId: UserId): User = userRepository.getUserById(userId)
}