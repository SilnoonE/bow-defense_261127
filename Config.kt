package com.example.bowdefense

import android.graphics.Color

object Config {
    const val PLAYER_MAX_HP = 10
    const val BASE_ARROW_DAMAGE = 1
    const val BASE_FIRE_COOLDOWN = 500L
    const val BASE_ARROW_SPEED = 30f
    const val BOW_SIZE = 672f 

    // 몬스터 크기 (최종 10% 축소 반영)
    const val MONSTER_RENDER_SIZE = 283.5f  
    const val MONSTER_HITBOX_SIZE = 135f  

    const val TIME_STEP_1 = 20
    const val TIME_STEP_2 = 40
    const val TIME_STEP_3 = 60
    const val TIME_STEP_4 = 90
    const val TIME_STEP_5 = 120

    enum class MonsterType(val baseHp: Int, val speed: Float, val score: Int, val damage: Int, val color: Int) {
        NORMAL(4, 4.5f, 10, 1, Color.GREEN),
        FAST(2, 9f, 20, 1, Color.YELLOW),
        TANK(12, 2.5f, 50, 2, Color.RED)
    }
}
