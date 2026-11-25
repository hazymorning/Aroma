package app.aroma.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class ErrorMappingTest {
    @Test
    fun `failures read as what went wrong, while cancellation goes through`() {
        listOf(
            // Only a failed handshake is a certificate problem; a reset during a response is a dropped connection.
            SSLHandshakeException("Trust anchor for certification path not found") to AromaError.Certificate::class,
            SSLException("bad certificate", CertificateException()) to AromaError.Certificate::class,
            SSLException("Read error: I/O error during system call, Connection reset by peer") to AromaError.Offline::class,
            SerializationException("Unexpected JSON token") to AromaError.NotMealie::class,
        ).forEach { (failure, error) -> assertEquals("${failure.message}", error, failure.toAromaError()::class) }
        // Every Mealie call maps its failures; a screen that went away must still stop its work.
        assertThrows(CancellationException::class.java) { CancellationException("left the screen").toAromaError() }
    }
}
