package pl.softmil.padelBooking.core


data class User(val id: UserId, val name: String, val surname: String, val emailAddress: String, val level: Int)
