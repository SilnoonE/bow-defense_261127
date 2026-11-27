package com.example.bowdefense

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.launch

class GameActivity : BaseActivity() {

    private lateinit var gameView: GameView
    private lateinit var stageTextView: TextView
    private lateinit var scoreTextView: TextView
    private lateinit var hpProgressBar: ProgressBar
    private lateinit var upgradeMessage: TextView
    
    private lateinit var pauseButton: Button
    private lateinit var pauseScreen: LinearLayout
    private lateinit var resumeButton: Button
    private lateinit var settingsButtonInGame: Button
    private lateinit var exitButton: Button
    
    private lateinit var soundManager: SoundManager
    private lateinit var saveManager: SaveManager
    private lateinit var purchaseStore: PurchaseStore
    private lateinit var dataManager: DataManager
    private var isGamePaused = false
    private var isGameOverProcessed = false

    // 아이템 UI
    private lateinit var slotPotion: View
    private lateinit var slotPower: View
    private lateinit var slotSlow: View
    private lateinit var countPotion: TextView
    private lateinit var countPower: TextView
    private lateinit var countSlow: TextView
    private lateinit var powerArrowTimer: TextView
    private lateinit var slowStoneTimer: TextView

    // 전면 광고
    private var mInterstitialAd: InterstitialAd? = null
    private val TAG = "GameActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        soundManager = SoundManager(this)
        saveManager = SaveManager(this)
        purchaseStore = PurchaseStore(this)
        dataManager = DataManager(this)

        gameView = findViewById(R.id.gameView)
        stageTextView = findViewById(R.id.stageTextView)
        scoreTextView = findViewById(R.id.scoreTextView)
        hpProgressBar = findViewById(R.id.hpProgressBar)
        upgradeMessage = findViewById(R.id.upgradeMessage)
        
        pauseButton = findViewById(R.id.pauseButton)
        pauseScreen = findViewById(R.id.pauseScreen)
        resumeButton = findViewById(R.id.resumeButton)
        settingsButtonInGame = findViewById(R.id.settingsButtonInGame)
        exitButton = findViewById(R.id.exitButton)

        // 아이템 슬롯 초기화
        slotPotion = findViewById(R.id.slotPotion)
        slotPower = findViewById(R.id.slotPower)
        slotSlow = findViewById(R.id.slotSlow)
        countPotion = findViewById(R.id.countPotion)
        countPower = findViewById(R.id.countPower)
        countSlow = findViewById(R.id.countSlow)
        powerArrowTimer = findViewById(R.id.powerArrowTimer)
        slowStoneTimer = findViewById(R.id.slowStoneTimer)

        initItemListeners()

        // 초기 광고 로드
        loadInterstitialAd()

        pauseButton.setOnClickListener { pauseGame() }
        resumeButton.setOnClickListener { resumeGame() }
        settingsButtonInGame.setOnClickListener { showSettingsDialog() }
        
        exitButton.setOnClickListener {
            saveManager.saveHighScore(gameView.score)
            showInterstitialAd { finish() }
        }

        gameView.setSoundManager(soundManager)

        // 이어하기 체크
        val isContinue = intent.getBooleanExtra("CONTINUE_GAME", false)
        if (isContinue) {
            val lastScore = intent.getIntExtra("LAST_SCORE", 0)
            val lastStage = intent.getIntExtra("LAST_STAGE", 1)
            val lastWave = intent.getIntExtra("LAST_WAVE", 1)
            val cumulativeKills = intent.getIntExtra("CUMULATIVE_KILLS", 0)
            val currentDamage = intent.getIntExtra("CURRENT_DAMAGE", 1)
            val fireCooldown = intent.getLongExtra("FIRE_COOLDOWN", Config.BASE_FIRE_COOLDOWN)
            
            gameView.post {
                gameView.revive(lastScore, lastStage, lastWave, cumulativeKills, currentDamage, fireCooldown)
            }
        }

        gameView.onStateChanged = { runOnUiThread { updateUI() } }

        gameView.onUpgrade = { message ->
            runOnUiThread {
                upgradeMessage.text = message
                upgradeMessage.visibility = View.VISIBLE
                upgradeMessage.postDelayed({ upgradeMessage.visibility = View.GONE }, 2000)
            }
        }

        // 게임 오버 시 광고 노출
        gameView.onGameOver = { finalScore ->
            if (!isGameOverProcessed) {
                isGameOverProcessed = true
                saveManager.saveHighScore(finalScore)
                
                lifecycleScope.launch {
                    val accuracy = if (gameView.arrowsShot > 0) {
                        (gameView.hitCount.toFloat() / gameView.arrowsShot.toFloat()) * 100f
                    } else 0f

                    val record = GameRecord(
                        playDate = System.currentTimeMillis(),
                        survivalTimeSeconds = ((System.currentTimeMillis() - gameView.gameStartTime) / 1000).toInt(),
                        score = finalScore,
                        killCount = gameView.killCount,
                        arrowsShot = gameView.arrowsShot,
                        hitCount = gameView.hitCount,
                        accuracy = accuracy,
                        goldEarned = gameView.goldEarned,
                        goldSpent = gameView.goldSpent,
                        shopPurchaseCount = gameView.shopPurchaseCount,
                        bowChangeCount = 0, 
                        maxStage = gameView.stageManager.currentStage,
                        maxWave = gameView.stageManager.currentWave,
                        resultType = "DEAD",
                        isLowHpSurvivalAchieved = gameView.isLowHpSurvivalAchieved
                    )
                    val newAchievements = dataManager.saveGameRecord(record)
                    
                    runOnUiThread {
                        showInterstitialAd {
                            val intent = Intent(this@GameActivity, ResultActivity::class.java).apply {
                                putExtra("SCORE", finalScore)
                                putExtra("STAGE", gameView.stageManager.currentStage)
                                putExtra("WAVE", gameView.stageManager.currentWave)
                                putExtra("KILLS", gameView.killCount)
                                putExtra("ACCURACY", accuracy)
                                putExtra("SURVIVAL_TIME", record.survivalTimeSeconds)
                                putExtra("GOLD_EARNED", gameView.goldEarned)
                                // Pass new achievements as IDs
                                putIntegerArrayListExtra("NEW_ACHIEVEMENTS", ArrayList(newAchievements.map { it.id }))
                                
                                putExtra("CUMULATIVE_KILLS", gameView.upgradeManager.cumulativeKills)
                                putExtra("CURRENT_DAMAGE", gameView.player.currentDamage)
                                putExtra("FIRE_COOLDOWN", gameView.player.fireCooldown)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }

        updateUI()
    }

    private fun showSettingsDialog() {
        val bgmStatus = if (purchaseStore.isBgmEnabled()) getString(R.string.on) else getString(R.string.off)
        val vibStatus = if (purchaseStore.isVibrationEnabled()) getString(R.string.on) else getString(R.string.off)

        val options = arrayOf(
            getString(R.string.change_language),
            "${getString(R.string.bgm_setting)}: $bgmStatus",
            "${getString(R.string.vibration_setting)}: $vibStatus"
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLanguageDialog()
                    1 -> {
                        val enabled = !purchaseStore.isBgmEnabled()
                        purchaseStore.setBgmEnabled(enabled)
                        if (enabled) soundManager.startBGM() else soundManager.pauseBGM()
                        showSettingsDialog()
                    }
                    2 -> {
                        purchaseStore.setVibrationEnabled(!purchaseStore.isVibrationEnabled())
                        showSettingsDialog()
                    }
                }
            }
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English (영어)", "한국어 (Korean)", "Español", "中文", "日本語")
        val langCodes = arrayOf("en", "ko", "es", "zh", "ja")

        AlertDialog.Builder(this)
            .setTitle(R.string.select_language)
            .setItems(languages) { _, which ->
                val selectedLang = langCodes[which]
                LocaleHelper.setLocale(this, selectedLang)
                // In game language change - better to recreate but might lose temporary state if not handled.
                // However, current GameActivity doesn't save all monster positions.
                // To minimize loss, we just recreate.
                recreate()
            }
            .show()
    }

    private fun updateUI() {
        stageTextView.text = getString(R.string.stage_wave, gameView.stageManager.currentStage, gameView.stageManager.currentWave)
        scoreTextView.text = getString(R.string.score_label, gameView.score)
        val currentHp = gameView.player.hp
        hpProgressBar.max = Config.PLAYER_MAX_HP
        hpProgressBar.progress = currentHp

        updateItemUI()
    }

    private fun updateItemUI() {
        val potionCount = purchaseStore.getItemCount("item_potion")
        val powerCount = purchaseStore.getItemCount("item_power")
        val slowCount = purchaseStore.getItemCount("item_slow")

        slotPotion.visibility = if (potionCount > 0) View.VISIBLE else View.GONE
        countPotion.text = getString(R.string.battle_item_count_short, potionCount)

        slotPower.visibility = if (powerCount > 0) View.VISIBLE else View.GONE
        countPower.text = getString(R.string.battle_item_count_short, powerCount)

        slotSlow.visibility = if (slowCount > 0) View.VISIBLE else View.GONE
        countSlow.text = getString(R.string.battle_item_count_short, slowCount)

        // 타이머 업데이트
        if (gameView.isPowerArrowActive) {
            powerArrowTimer.visibility = View.VISIBLE
            powerArrowTimer.text = getString(R.string.power_arrow_timer, (gameView.powerArrowTimeLeft / 1000 + 1).toInt())
        } else {
            powerArrowTimer.visibility = View.GONE
        }

        if (gameView.isSlowStoneActive) {
            slowStoneTimer.visibility = View.VISIBLE
            slowStoneTimer.text = getString(R.string.slow_stone_timer, (gameView.slowStoneTimeLeft / 1000 + 1).toInt())
        } else {
            slowStoneTimer.visibility = View.GONE
        }
    }

    private fun initItemListeners() {
        slotPotion.setOnClickListener {
            if (gameView.player.hp < Config.PLAYER_MAX_HP) {
                if (purchaseStore.useItem("item_potion")) {
                    if (gameView.player.hp == 1) {
                        purchaseStore.incrementItemStat("stat_use_item_potion_at_hp1")
                    }
                    gameView.player.hp = (gameView.player.hp + 1).coerceAtMost(Config.PLAYER_MAX_HP)
                    purchaseStore.incrementItemStat("stat_use_item_potion")
                    gameView.onUpgrade?.invoke(getString(R.string.hp_recovered))
                    updateUI()
                }
            } else {
                Toast.makeText(this, getString(R.string.hp_full_toast), Toast.LENGTH_SHORT).show()
            }
        }

        slotPower.setOnClickListener {
            if (!gameView.isPowerArrowActive) {
                if (purchaseStore.useItem("item_power")) {
                    gameView.isPowerArrowActive = true
                    gameView.powerArrowTimeLeft = 20000L
                    purchaseStore.incrementItemStat("stat_use_item_power")
                    updateUI()
                }
            } else {
                Toast.makeText(this, getString(R.string.power_arrow_active_toast), Toast.LENGTH_SHORT).show()
            }
        }

        slotSlow.setOnClickListener {
            if (!gameView.isSlowStoneActive) {
                if (purchaseStore.useItem("item_slow")) {
                    gameView.isSlowStoneActive = true
                    gameView.slowStoneTimeLeft = 10000L
                    purchaseStore.incrementItemStat("stat_use_item_slow")
                    updateUI()
                }
            } else {
                Toast.makeText(this, getString(R.string.slow_stone_active_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "REDACTED_AD_IDENTIFIER", adRequest, 
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    Log.d(TAG, "Ad Loaded Successfully")
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                    Log.e(TAG, "Ad Failed to Load: ${adError.message}")
                }
            })
    }

    private fun showInterstitialAd(onAdDismissed: () -> Unit) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    loadInterstitialAd()
                    onAdDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    mInterstitialAd = null
                    onAdDismissed()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            onAdDismissed()
            loadInterstitialAd()
        }
    }

    private fun pauseGame() {
        isGamePaused = true
        gameView.pause()
        soundManager.pauseBGM()
        pauseScreen.visibility = View.VISIBLE
    }

    private fun resumeGame() {
        isGamePaused = false
        pauseScreen.visibility = View.GONE
        gameView.resume()
        soundManager.startBGM()
    }

    override fun onPause() {
        super.onPause()
        if (!isGamePaused) {
            gameView.pause()
            soundManager.pauseBGM()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isGamePaused) {
            gameView.resume()
            soundManager.startBGM()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }

    override fun onBackPressed() {
        if (pauseScreen.visibility == View.VISIBLE) {
            resumeGame()
        } else {
            pauseGame()
        }
    }
}
