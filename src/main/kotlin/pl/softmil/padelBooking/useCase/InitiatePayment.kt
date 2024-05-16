package pl.softmil.padelBooking.useCase

import pl.softmil.padelBooking.core.PaymentInitialisationResult
import pl.softmil.padelBooking.core.Reservation
import pl.softmil.padelBooking.core.Session
import pl.softmil.padelBooking.core.SessionId
import pl.softmil.padelBooking.core.User
import pl.softmil.padelBooking.core.UserId
import pl.softmil.padelBooking.gateway.SessionRepository
import pl.softmil.padelBooking.gateway.UserRepository
import pl.softmil.padelBooking.useCase.InitiatePaymentResult.Invalid
import pl.softmil.padelBooking.useCase.InitiatePaymentResult.SessionUnavailable
import pl.softmil.padelBooking.useCase.InitiatePaymentResult.Success
import java.time.ZonedDateTime


sealed interface InitiatePaymentResult {
    data class Success(val reservation: Reservation) : InitiatePaymentResult
    data object Invalid : InitiatePaymentResult
    data object SessionUnavailable : InitiatePaymentResult
}

class InitiatePayment(
    private val sessionRepository: SessionRepository, private val userRepository: UserRepository
) {
    fun initiate(userId: UserId, sessionId: SessionId, now: ZonedDateTime): InitiatePaymentResult {
        val user = getUserById(userId)
        val session = getSessionById(sessionId)

        return when (val result = session.initiatePayment(user, now)) {
            is PaymentInitialisationResult.Missing -> Invalid
            is PaymentInitialisationResult.SessionUnavailable -> SessionUnavailable
            is PaymentInitialisationResult.PendingReservation -> Success(result.reservation)
        }
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)


    private fun getUserById(userId: UserId): User = userRepository.getUserById(userId)
}