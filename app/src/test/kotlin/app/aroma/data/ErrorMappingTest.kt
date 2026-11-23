package app.aroma.data

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
}
