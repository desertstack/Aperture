package io.aperture.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aperture answers on a port bound to every interface. Without this check, any page a
 * developer opens can point a domain it controls at the phone's address and then read the
 * reply, which now includes the app's preferences and databases. An attacker can put a name
 * in DNS; they cannot put an IP literal in a name they own.
 */
class HostValidationTest {

    @Test
    fun `an address reaches the api`() {
        assertTrue(ApertureServer.isAllowedHost("127.0.0.1"))
        assertTrue(ApertureServer.isAllowedHost("127.0.0.1:8080"))
        assertTrue(ApertureServer.isAllowedHost("192.168.1.100:8080"))
        assertTrue(ApertureServer.isAllowedHost("10.0.2.2"))
    }

    @Test
    fun `localhost reaches the api`() {
        assertTrue(ApertureServer.isAllowedHost("localhost"))
        assertTrue(ApertureServer.isAllowedHost("localhost:8080"))
        assertTrue(ApertureServer.isAllowedHost("LOCALHOST:8080"))
        assertTrue(ApertureServer.isAllowedHost("[::1]:8080"))
    }

    @Test
    fun `a domain name does not`() {
        assertFalse(ApertureServer.isAllowedHost("attacker.example"))
        assertFalse(ApertureServer.isAllowedHost("rebind.attacker.example:8080"))
        assertFalse(ApertureServer.isAllowedHost("phone.local"))
        assertFalse(ApertureServer.isAllowedHost("localhost.attacker.example"))
        assertFalse(ApertureServer.isAllowedHost(""))
    }

    @Test
    fun `a name that only looks like an address does not`() {
        assertFalse(ApertureServer.isAllowedHost("1.2.3"))
        assertFalse(ApertureServer.isAllowedHost("1.2.3.4.5"))
        assertFalse(ApertureServer.isAllowedHost("256.1.1.1"))
        assertFalse(ApertureServer.isAllowedHost("1.2.3.x"))
    }

    @Test
    fun `cross-origin calls come from the developer's own machine or not at all`() {
        assertTrue(ApertureServer.isLoopbackOrigin("http://localhost:3000"))
        assertTrue(ApertureServer.isLoopbackOrigin("http://127.0.0.1:5173"))
        assertFalse(ApertureServer.isLoopbackOrigin("https://attacker.example"))
        assertFalse(ApertureServer.isLoopbackOrigin("null"))
    }
}
