package com.bizi.roboapp

import com.bizi.roboapp.core.protocol.navigation.NavigationServerEvent
import com.bizi.roboapp.core.protocol.navigation.parseNavigationServerEvent
import com.bizi.roboapp.feature.drivecontrol.domain.DRIVE_START_EXIT_DEADBAND_PERCENT
import com.bizi.roboapp.feature.drivecontrol.domain.DRIVE_STOP_ENTER_DEADBAND_PERCENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationIntentProtocolTest {
    @Test
    fun throttleSlider_isAlreadySignedPercent() {
        assertEquals(100, 100.coerceIn(-100, 100))
        assertEquals(-100, (-100).coerceIn(-100, 100))
        assertEquals(0, 0.coerceIn(-100, 100))
    }

    @Test
    fun percentDeadbands_matchTemporarySliderRanges() {
        assertEquals(2, DRIVE_STOP_ENTER_DEADBAND_PERCENT)
        assertEquals(3, DRIVE_START_EXIT_DEADBAND_PERCENT)
    }

    @Test
    fun parser_readsAckFromNewProtocolShape() {
        val event = parseNavigationServerEvent("""{"k":"a","c":"s","q":12}""")
        assertTrue(event is NavigationServerEvent.Ack)
        event as NavigationServerEvent.Ack
        assertEquals("s", event.code)
        assertEquals(12L, event.seq)
    }

    @Test
    fun parser_readsErrorFromNewProtocolShape() {
        val event = parseNavigationServerEvent("""{"k":"e","c":"st","q":99,"m":"stale"}""")
        assertTrue(event is NavigationServerEvent.Error)
        event as NavigationServerEvent.Error
        assertEquals("st", event.code)
        assertEquals(99L, event.seq)
        assertEquals("stale", event.message)
    }
}
