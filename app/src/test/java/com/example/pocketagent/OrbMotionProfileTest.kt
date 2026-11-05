package com.example.pocketagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbMotionProfileTest {
    @Test fun idleIsSlowAndSubtle() {
        val profile = OrbMotionProfile.forState(LiquidOrbView.State.IDLE)
        assertTrue(profile.durationMs >= 7000L)
        assertTrue(profile.amplitude < 0.06f)
    }

    @Test fun automationMovesFasterThanIdle() {
        val idle = OrbMotionProfile.forState(LiquidOrbView.State.IDLE)
        val active = OrbMotionProfile.forState(LiquidOrbView.State.AUTOMATING)
        assertTrue(active.durationMs < idle.durationMs)
        assertTrue(active.amplitude > idle.amplitude)
    }

    @Test fun pausedOrbIsTheMostSubtle() {
        val paused = OrbMotionProfile.forState(LiquidOrbView.State.PAUSED)
        val idle = OrbMotionProfile.forState(LiquidOrbView.State.IDLE)
        assertTrue(paused.amplitude < idle.amplitude)
    }

    @Test fun errorUsesWarningAccent() {
        assertEquals(OrbMotionProfile.Accent.WARNING,
            OrbMotionProfile.forState(LiquidOrbView.State.ERROR).accent)
    }
}
