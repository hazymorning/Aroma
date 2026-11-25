package app.aroma.testing

import kotlinx.coroutines.CoroutineExceptionHandler
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Hears every exception a coroutine leaves uncaught, in a view model, the app's scope or anywhere
 * else: on a phone, Android ends the app for it. Kotlin hands it to every handler named in
 * META-INF/services, as this one is; [ScreenTest] fails the test on any.
 */
class UncaughtExceptions :
    AbstractCoroutineContextElement(CoroutineExceptionHandler),
    CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        if (caught.size < KEPT) caught += exception
    }

    companion object {
        val caught = CopyOnWriteArrayList<Throwable>()
        private const val KEPT = 20
    }
}
