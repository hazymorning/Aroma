package app.aroma.data

/** Data plus how fresh it is: [error] is set when the newest copy could not be fetched. */
data class Loadable<T>(val data: T, val isRefreshing: Boolean = false, val error: AromaError? = null) {
    val isOffline: Boolean get() = error?.isConnectivity == true
}
