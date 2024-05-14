package pl.softmil.padelCoach.useCase

import pl.softmil.padelCoach.core.PaymentInitialisationResult
import pl.softmil.padelCoach.core.Reservation
import pl.softmil.padelCoach.core.Session
import pl.softmil.padelCoach.core.SessionId
import pl.softmil.padelCoach.core.User
import pl.softmil.padelCoach.core.UserId
import pl.softmil.padelCoach.gateway.SessionRepository
import pl.softmil.padelCoach.gateway.UserRepository
import java.time.ZonedDateTime


sealed interface InitiatePaymentResult {
    data class Success(val reservation: Reservation) : InitiatePaymentResult
    data object Invalid : InitiatePaymentResult
}

class InitiatePayment(
    private val sessionRepository: SessionRepository, private val userRepository: UserRepository
) {
    fun execute(userId: UserId, sessionId: SessionId, now: ZonedDateTime): InitiatePaymentResult {
        val user = getUserById(userId)
        val session = getSessionById(sessionId)

        return when (val result = session.initiatePayment(user, now)) {
            is PaymentInitialisationResult.Missing -> InitiatePaymentResult.Invalid
            is PaymentInitialisationResult.PendingReservation -> InitiatePaymentResult.Success(result.reservation)
        }
    }

    private fun getSessionById(sessionId: SessionId): Session = sessionRepository.getSessionById(sessionId)


    private fun getUserById(userId: UserId): User = userRepository.getUserById(userId)
}