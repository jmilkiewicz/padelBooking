package pl.softmil.padelCoach.gateway

import pl.softmil.padelCoach.core.User
import pl.softmil.padelCoach.core.UserId

interface UserRepository {
    fun getUserById(userId: UserId): User
}
