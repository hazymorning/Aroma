package app.aroma.core.model

data class User(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
)

data class ServerInfo(val url: String, val version: String, val allowPasswordLogin: Boolean)
