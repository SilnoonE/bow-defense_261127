package com.example.bowdefense

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playDate: Long,
    val survivalTimeSeconds: Int,
    val score: Int,
    val killCount: Int,
    val arrowsShot: Int,
    val hitCount: Int,
    val accuracy: Float,
    val goldEarned: Int,
    val goldSpent: Int,
    val shopPurchaseCount: Int,
    val bowChangeCount: Int,
    val maxStage: Int,
    val maxWave: Int,
    val resultType: String, // e.g., "DEAD", "EXIT"
    val isLowHpSurvivalAchieved: Boolean = false
)

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: Int,
    val name: String,
    val description: String,
    val category: String, // 생존, 처치, 명중, 상점, 점수, 누적, 도전
    val targetValue: Int,
    var currentValue: Int = 0,
    val rewardGold: Int,
    var isUnlocked: Boolean = false,
    var isRewardClaimed: Boolean = false
)

@Entity(tableName = "player_stats")
data class PlayerStats(
    @PrimaryKey val id: Int = 1,
    var totalPlayCount: Int = 0,
    var totalKills: Int = 0,
    var totalArrowsShot: Int = 0,
    var totalHitCount: Int = 0,
    var totalGoldEarned: Int = 0,
    var totalGoldSpent: Int = 0,
    var totalSurvivalTime: Int = 0,
    var maxScore: Int = 0,
    var maxStage: Int = 1,
    var maxWave: Int = 1,
    var totalShopVisits: Int = 0,
    var totalShopPurchases: Int = 0,
    var ownedBowsCount: Int = 1
)

@Dao
interface GameDao {
    @Insert
    suspend fun insertRecord(record: GameRecord)

    @Query("SELECT * FROM game_records ORDER BY playDate DESC LIMIT 10")
    fun getRecentRecords(): Flow<List<GameRecord>>

    @Query("SELECT * FROM game_records")
    suspend fun getAllRecords(): List<GameRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: Achievement)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievements(achievements: List<Achievement>)

    @Query("SELECT * FROM achievements")
    fun getAllAchievements(): Flow<List<Achievement>>

    @Update
    suspend fun updateAchievement(achievement: Achievement)

    @Query("SELECT * FROM player_stats WHERE id = 1")
    suspend fun getPlayerStats(): PlayerStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePlayerStats(stats: PlayerStats)

    @Query("SELECT COUNT(*) FROM achievements WHERE isUnlocked = 1")
    fun getUnlockedCount(): Flow<Int>
}

@Database(entities = [GameRecord::class, Achievement::class, PlayerStats::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "BowDefense_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
