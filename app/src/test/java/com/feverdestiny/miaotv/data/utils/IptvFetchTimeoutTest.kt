package com.feverdestiny.miaotv.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvFetchTimeoutTest {

    @Test
    fun playlistCallTimeoutIsEightToTwelveSeconds() {
        assertTrue(Constants.IPTV_FETCH_CALL_TIMEOUT_SECONDS in 8L..12L)
        assertTrue(Constants.IPTV_FETCH_CONNECT_TIMEOUT_SECONDS in 1L..12L)
        assertTrue(Constants.IPTV_FETCH_READ_TIMEOUT_SECONDS in 1L..12L)
        assertTrue(
            Constants.IPTV_FETCH_CALL_TIMEOUT_SECONDS >=
                Constants.IPTV_FETCH_CONNECT_TIMEOUT_SECONDS,
        )
    }

    @Test
    fun outerIptvRetryIsAtMostOneExtraPass() {
        assertTrue(Constants.IPTV_FETCH_RETRY_COUNT in 0L..1L)
        assertEquals(1000L, Constants.IPTV_FETCH_RETRY_INTERVAL)
    }

    @Test
    fun mediaHttpRetryDefaultsAreUnchanged() {
        assertEquals(10L, Constants.HTTP_RETRY_COUNT)
        assertEquals(3000L, Constants.HTTP_RETRY_INTERVAL)
    }
}
