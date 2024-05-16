package pl.softmil.padelBooking.gateway

import pl.softmil.padelBooking.core.User
import pl.softmil.padelBooking.core.UserId

interface UserRepository {
    fun getUserById(userId: UserId): User
}
