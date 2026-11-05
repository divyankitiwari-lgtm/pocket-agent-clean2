package com.example.pocketagent

/** State-driven motion tuning for the reference-matched hollow JARVIS orb. */
internal object OrbMotionProfile {
    enum class Accent { COOL, WARNING }
    data class Profile(val durationMs: Long, val amplitude: Float, val accent: Accent)

    fun forState(state: LiquidOrbView.State): Profile = when (state) {
        LiquidOrbView.State.IDLE -> Profile(8200L, 0.035f, Accent.COOL)
        LiquidOrbView.State.LISTENING -> Profile(2800L, 0.095f, Accent.COOL)
        LiquidOrbView.State.THINKING -> Profile(2100L, 0.12f, Accent.COOL)
        LiquidOrbView.State.SPEAKING -> Profile(1900L, 0.14f, Accent.COOL)
        LiquidOrbView.State.AUTOMATING -> Profile(2400L, 0.105f, Accent.COOL)
        LiquidOrbView.State.PAUSED -> Profile(10000L, 0.012f, Accent.COOL)
        LiquidOrbView.State.ERROR -> Profile(4800L, 0.055f, Accent.WARNING)
    }
}
