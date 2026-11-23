package app.aroma.data

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class ErrorMappingTest {
    @Test
    fun `only a failed handshake is a certificate problem, a reset during a response is a dropped connection`() {
        assertTrue(SSLHandshakeException("Trust anchor for certification path not found").toAromaError() is AromaError.Certificate)
        assertTrue(SSLException("bad certificate", CertificateException()).toAromaError() is AromaError.Certificate)
        assertTrue(SSLException("Read error: I/O error during system call, Connection reset by peer").toAromaError() is AromaError.Offline)
    }

    @Test
    fun `json that is not what Mealie sends means the server is not Mealie`() {
        assertTrue(SerializationException("Unexpected JSON token").toAromaError() is AromaError.NotMealie)
    }

    @Test
    fun `a failure of the runtime itself is not turned into a message`() {
        assertThrows(OutOfMemoryError::class.java) { OutOfMemoryError().toAromaError() }
    }
}
