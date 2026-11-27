package com.example.bowdefense

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DataManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.gameDao()
    private val purchaseStore = PurchaseStore(context)

    suspend fun initAchievements() {
        val achievements = listOf(
            Achievement(1, "첫 전투", "게임 1회 플레이", "누적", 1, rewardGold = 50),
            Achievement(2, "첫 처치", "적 1마리 처치", "처치", 1, rewardGold = 50),
            Achievement(3, "첫 명중", "화살 1회 명중", "명중", 1, rewardGold = 50),
            Achievement(4, "1분 생존자", "한 판 60초 생존", "생존", 60, rewardGold = 100),
            Achievement(5, "3분 생존자", "한 판 180초 생존", "생존", 180, rewardGold = 200),
            Achievement(6, "5분 생존자", "한 판 300초 생존", "생존", 300, rewardGold = 300),
            Achievement(7, "마지막 성벽", "한 판 600초 생존", "생존", 600, rewardGold = 700),
            Achievement(8, "초보 사냥꾼", "누적 50킬", "누적", 50, rewardGold = 100),
            Achievement(9, "전장의 궁수", "누적 200킬", "누적", 200, rewardGold = 250),
            Achievement(10, "몰려와도 소용없다", "누적 500킬", "누적", 500, rewardGold = 500),
            Achievement(11, "천 명의 침입자", "누적 1000킬", "누적", 1000, rewardGold = 1000),
            Achievement(12, "한 판 집중 처치", "한 판 50킬", "처치", 50, rewardGold = 200),
            Achievement(13, "폭풍 처치", "한 판 100킬", "처치", 100, rewardGold = 400),
            Achievement(14, "압도적인 방어", "한 판 200킬", "처치", 200, rewardGold = 800),
            Achievement(15, "안정적인 조준", "한 판 명중률 50% 이상", "명중", 50, rewardGold = 100),
            Achievement(16, "정확한 궁수", "한 판 명중률 70% 이상", "명중", 70, rewardGold = 250),
            Achievement(17, "명궁의 손끝", "한 판 명중률 85% 이상", "명중", 85, rewardGold = 500),
            Achievement(18, "누적 명중 100회", "누적 100회 명중", "누적", 100, rewardGold = 150),
            Achievement(19, "누적 명중 500회", "누적 500회 명중", "누적", 500, rewardGold = 400),
            Achievement(20, "누적 명중 1000회", "누적 1000회 명중", "누적", 1000, rewardGold = 800),
            Achievement(21, "첫 상점 방문", "상점 1회 진입", "상점", 1, rewardGold = 50),
            Achievement(22, "첫 구매", "상점 구매 1회", "상점", 1, rewardGold = 100),
            Achievement(23, "장비 준비", "누적 구매 5회", "상점", 5, rewardGold = 200),
            Achievement(24, "전투형 소비자", "누적 구매 20회", "상점", 20, rewardGold = 500),
            Achievement(25, "첫 활 교체", "기본 보우 외 다른 활 장착", "상점", 1, rewardGold = 200),
            Achievement(26, "유니크 보우 보유", "어비스 블러드 보우 또는 세라픽 라이트 보우 구매", "상점", 1, rewardGold = 500),
            Achievement(27, "수집가의 시작", "활 2개 이상 보유", "상점", 2, rewardGold = 300),
            Achievement(28, "모든 활 수집", "현재 상점에 있는 모든 활 보유", "상점", 7, rewardGold = 1000),
            Achievement(29, "골드 수집가", "누적 골드 1000G 획득", "누적", 1000, rewardGold = 150),
            Achievement(30, "전장의 부자", "누적 골드 5000G 획득", "누적", 5000, rewardGold = 500),
            Achievement(31, "황금 궁수", "누적 골드 10000G 획득", "누적", 10000, rewardGold = 1000),
            Achievement(32, "첫 100점", "한 판 100점 달성", "점수", 100, rewardGold = 50),
            Achievement(33, "성장하는 궁수", "한 판 1000점 달성", "점수", 1000, rewardGold = 200),
            Achievement(34, "실력 증명", "한 판 3000점 달성", "점수", 3000, rewardGold = 400),
            Achievement(35, "고득점 사냥꾼", "한 판 5000점 달성", "점수", 5000, rewardGold = 800),
            Achievement(36, "전설의 기록", "한 판 10000점 달성", "점수", 10000, rewardGold = 1500),
            Achievement(37, "다시 도전", "게임 5회 플레이", "누적", 5, rewardGold = 100),
            Achievement(38, "꾸준한 수비수", "게임 20회 플레이", "누적", 20, rewardGold = 300),
            Achievement(39, "매일 성장", "게임 50회 플레이", "누적", 50, rewardGold = 700),
            Achievement(40, "활 디펜스의 수호자", "게임 100회 플레이", "누적", 100, rewardGold = 1500),
            Achievement(41, "Stage 2 도달", "Stage 2 도달", "도전", 2, rewardGold = 200),
            Achievement(42, "Stage 3 도달", "Stage 3 도달", "도전", 3, rewardGold = 400),
            Achievement(43, "Wave 5 돌파", "Wave 5 도달", "도전", 5, rewardGold = 250),
            Achievement(44, "Wave 10 돌파", "Wave 10 도달", "도전", 10, rewardGold = 700),
            Achievement(45, "상점 없이 버티기", "상점 구매 없이 120초 생존", "도전", 120, rewardGold = 300),
            Achievement(46, "절약형 궁수", "한 판에서 골드 사용 없이 180초 생존", "도전", 180, rewardGold = 500),
            Achievement(47, "공격 집중", "한 판에서 100킬 이상, 명중률 60% 이상", "도전", 1, rewardGold = 600),
            Achievement(48, "위기 극복", "체력이 30% 이하인 상태에서 30초 이상 추가 생존", "도전", 30, rewardGold = 500),
            Achievement(49, "균형 잡힌 전투", "한 판에서 180초 생존, 50킬 이상, 명중률 60% 이상", "도전", 1, rewardGold = 700),
            Achievement(50, "BowDefense", "한 판에서 600초 생존, 300킬 이상, 명중률 70% 이상", "도전", 1, rewardGold = 3000),
            Achievement(51, "전장의 생존가", "회복 물약 10회 사용", "아이템", 10, rewardGold = 300),
            Achievement(52, "폭발적인 화력", "강화 화살 5회 사용", "아이템", 5, rewardGold = 500),
            Achievement(53, "시간의 지배자", "시간 지연석 5회 사용", "아이템", 5, rewardGold = 700),
            Achievement(54, "위기 탈출", "체력 1 상태에서 회복 물약 사용", "아이템", 1, rewardGold = 500)
        )
        dao.insertAchievements(achievements)
    }

    suspend fun saveGameRecord(record: GameRecord): List<Achievement> {
        dao.insertRecord(record)
        val stats = dao.getPlayerStats() ?: PlayerStats()
        
        stats.totalPlayCount++
        stats.totalKills += record.killCount
        stats.totalArrowsShot += record.arrowsShot
        stats.totalHitCount += record.hitCount
        stats.totalGoldEarned += record.goldEarned
        stats.totalGoldSpent += record.goldSpent
        stats.totalSurvivalTime += record.survivalTimeSeconds
        if (record.score > stats.maxScore) stats.maxScore = record.score
        if (record.maxStage > stats.maxStage) stats.maxStage = record.maxStage
        if (record.maxWave > stats.maxWave) stats.maxWave = record.maxWave
        stats.totalShopPurchases += record.shopPurchaseCount
        
        // ownedBowsCount update
        var ownedCount = 0
        val bows = listOf("bow", "basic_bow_5", "basic_bow_4", "basic_bow_3", "basic_bow_2", "basic_bow", "high_bow")
        bows.forEach { if (purchaseStore.isOwned(it)) ownedCount++ }
        stats.ownedBowsCount = ownedCount

        dao.updatePlayerStats(stats)

        return checkAchievements(record, stats)
    }

    private suspend fun checkAchievements(record: GameRecord, stats: PlayerStats): List<Achievement> {
        val allAchievements = dao.getAllAchievements().first()
        val newlyUnlocked = mutableListOf<Achievement>()

        allAchievements.forEach { achievement ->
            if (achievement.isUnlocked) return@forEach

            var isUnlockedNow = false
            when (achievement.id) {
                1 -> if (stats.totalPlayCount >= 1) isUnlockedNow = true
                2 -> if (stats.totalKills >= 1) isUnlockedNow = true
                3 -> if (stats.totalHitCount >= 1) isUnlockedNow = true
                4 -> if (record.survivalTimeSeconds >= 60) isUnlockedNow = true
                5 -> if (record.survivalTimeSeconds >= 180) isUnlockedNow = true
                6 -> if (record.survivalTimeSeconds >= 300) isUnlockedNow = true
                7 -> if (record.survivalTimeSeconds >= 600) isUnlockedNow = true
                8 -> if (stats.totalKills >= 50) isUnlockedNow = true
                9 -> if (stats.totalKills >= 200) isUnlockedNow = true
                10 -> if (stats.totalKills >= 500) isUnlockedNow = true
                11 -> if (stats.totalKills >= 1000) isUnlockedNow = true
                12 -> if (record.killCount >= 50) isUnlockedNow = true
                13 -> if (record.killCount >= 100) isUnlockedNow = true
                14 -> if (record.killCount >= 200) isUnlockedNow = true
                15 -> if (record.accuracy >= 50f) isUnlockedNow = true
                16 -> if (record.accuracy >= 70f) isUnlockedNow = true
                17 -> if (record.accuracy >= 85f) isUnlockedNow = true
                18 -> if (stats.totalHitCount >= 100) isUnlockedNow = true
                19 -> if (stats.totalHitCount >= 500) isUnlockedNow = true
                20 -> if (stats.totalHitCount >= 1000) isUnlockedNow = true
                21 -> if (stats.totalShopVisits >= 1) isUnlockedNow = true
                22 -> if (stats.totalShopPurchases >= 1) isUnlockedNow = true
                23 -> if (stats.totalShopPurchases >= 5) isUnlockedNow = true
                24 -> if (stats.totalShopPurchases >= 20) isUnlockedNow = true
                25 -> if (purchaseStore.getEquippedBow() != "bow") isUnlockedNow = true
                26 -> if (purchaseStore.isOwned("basic_bow") || purchaseStore.isOwned("basic_bow_2")) isUnlockedNow = true
                27 -> if (stats.ownedBowsCount >= 2) isUnlockedNow = true
                28 -> if (stats.ownedBowsCount >= 7) isUnlockedNow = true
                29 -> if (stats.totalGoldEarned >= 1000) isUnlockedNow = true
                30 -> if (stats.totalGoldEarned >= 5000) isUnlockedNow = true
                31 -> if (stats.totalGoldEarned >= 10000) isUnlockedNow = true
                32 -> if (record.score >= 100) isUnlockedNow = true
                33 -> if (record.score >= 1000) isUnlockedNow = true
                34 -> if (record.score >= 3000) isUnlockedNow = true
                35 -> if (record.score >= 5000) isUnlockedNow = true
                36 -> if (record.score >= 10000) isUnlockedNow = true
                37 -> if (stats.totalPlayCount >= 5) isUnlockedNow = true
                38 -> if (stats.totalPlayCount >= 20) isUnlockedNow = true
                39 -> if (stats.totalPlayCount >= 50) isUnlockedNow = true
                40 -> if (stats.totalPlayCount >= 100) isUnlockedNow = true
                41 -> if (record.maxStage >= 2) isUnlockedNow = true
                42 -> if (record.maxStage >= 3) isUnlockedNow = true
                43 -> {
                    val totalWaves = (record.maxStage - 1) * 3 + record.maxWave
                    if (totalWaves >= 5) isUnlockedNow = true
                }
                44 -> {
                    val totalWaves = (record.maxStage - 1) * 3 + record.maxWave
                    if (totalWaves >= 10) isUnlockedNow = true
                }
                45 -> if (record.shopPurchaseCount == 0 && record.survivalTimeSeconds >= 120) isUnlockedNow = true
                46 -> if (record.goldSpent == 0 && record.survivalTimeSeconds >= 180) isUnlockedNow = true
                47 -> if (record.killCount >= 100 && record.accuracy >= 60f) isUnlockedNow = true
                48 -> if (record.isLowHpSurvivalAchieved) isUnlockedNow = true
                49 -> if (record.survivalTimeSeconds >= 180 && record.killCount >= 50 && record.accuracy >= 60f) isUnlockedNow = true
                50 -> if (record.survivalTimeSeconds >= 600 && record.killCount >= 300 && record.accuracy >= 70f) isUnlockedNow = true
                51 -> if (purchaseStore.getItemStat("stat_use_item_potion") >= 10) isUnlockedNow = true
                52 -> if (purchaseStore.getItemStat("stat_use_item_power") >= 5) isUnlockedNow = true
                53 -> if (purchaseStore.getItemStat("stat_use_item_slow") >= 5) isUnlockedNow = true
                54 -> if (purchaseStore.getItemStat("stat_use_item_potion_at_hp1") >= 1) isUnlockedNow = true
            }

            if (isUnlockedNow) {
                achievement.isUnlocked = true
                if (!achievement.isRewardClaimed) {
                    purchaseStore.addGold(achievement.rewardGold)
                    achievement.isRewardClaimed = true
                }
                withContext(Dispatchers.IO) {
                    dao.updateAchievement(achievement)
                }
                newlyUnlocked.add(achievement)
            }
        }
        return newlyUnlocked
    }
    
    suspend fun incrementShopVisit() {
        val stats = dao.getPlayerStats() ?: PlayerStats()
        stats.totalShopVisits++
        dao.updatePlayerStats(stats)
    }
}
