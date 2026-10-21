package io.github.aedev.flow.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCookieTest {
    @Test
    fun signedInNeedsAnApisidCookie() {
        assertTrue(GoogleCookie.isSignedIn("VISITOR_INFO1_LIVE=a; SAPISID=b; SID=c"))
        assertTrue(GoogleCookie.isSignedIn("__Secure-3PAPISID=x"))
        assertFalse(GoogleCookie.isSignedIn("VISITOR_INFO1_LIVE=a; SID=c"))
        assertFalse(GoogleCookie.isSignedIn(""))
        assertFalse(GoogleCookie.isSignedIn(null))
    }

    @Test
    fun cookieNamesAreNotConfusedWithValues() {
        assertFalse(GoogleCookie.isSignedIn("x=SAPISID; y=__Secure-3PAPISID"))
        assertEquals(listOf("a", "b"), GoogleCookie.names("a=1; b=2="))
    }

    @Test
    fun anyYouTubeSubdomainIsTheSignedInSite() {
        assertTrue(GoogleCookie.isYouTubeHost("www.youtube.com"))
        assertTrue(GoogleCookie.isYouTubeHost("m.youtube.com"))
        assertTrue(GoogleCookie.isYouTubeHost("youtube.com"))
        assertFalse(GoogleCookie.isYouTubeHost("accounts.google.com"))
        assertFalse(GoogleCookie.isYouTubeHost("notyoutube.com"))
        assertFalse(GoogleCookie.isYouTubeHost(null))
    }

    @Test
    fun dataSyncIdKeepsTheActingAccount() {
        assertEquals("123", GoogleCookie.dataSyncId("\"123||\""))
        assertEquals("456", GoogleCookie.dataSyncId("\"123||456\""))
        assertEquals("789", GoogleCookie.dataSyncId("789"))
    }

    @Test
    fun dataSyncIdIsNullWhenThePageHadNone() {
        assertNull(GoogleCookie.dataSyncId("\"\""))
        assertNull(GoogleCookie.dataSyncId("null"))
        assertNull(GoogleCookie.dataSyncId(null))
    }
}
