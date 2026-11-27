package com.example.bowdefense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var saveManager: SaveManager
    private lateinit var lifeManager: LifeManager
    private lateinit var dataManager: DataManager
    private lateinit var highScoreText: TextView
    private lateinit var lifeCountText: TextView
    private lateinit var adView: AdView
    private lateinit var purchaseStore: PurchaseStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Mobile Ads SDK 초기화
        // Screenshot-only working copy: advertising is disabled.

        saveManager = SaveManager(this)
        lifeManager = LifeManager(this)
        dataManager = DataManager(this)
        purchaseStore = PurchaseStore(this)

        // 업적 초기화
        lifecycleScope.launch {
            dataManager.initAchievements()
        }

        highScoreText = findViewById(R.id.highScoreTextView)
        lifeCountText = findViewById(R.id.lifeCountTextView)
        val startButton = findViewById<Button>(R.id.startButton)
        val shopButton = findViewById<Button>(R.id.shopButton)
        val recordButton = findViewById<Button>(R.id.recordButton)
        val achievementButton = findViewById<Button>(R.id.achievementButton)
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)

        updateUI()

        // 광고 로드
        adView = findViewById(R.id.adViewMain)
        val adRequest = AdRequest.Builder().build()
        adView.visibility = android.view.View.GONE

        // 게임 시작
        startButton.setOnClickListener {
            GameSessionManager.reset()
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }

        // 상점 이동
        shopButton.setOnClickListener {
            lifecycleScope.launch {
                dataManager.incrementShopVisit()
            }
            val intent = Intent(this, ShopActivity::class.java)
            startActivity(intent)
        }

        recordButton.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }

        achievementButton.setOnClickListener {
            startActivity(Intent(this, AchievementActivity::class.java))
        }

        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
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
                        purchaseStore.setBgmEnabled(!purchaseStore.isBgmEnabled())
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
                recreate()
            }
            .show()
    }

    private fun updateUI() {
        highScoreText.text = getString(R.string.high_score, saveManager.getHighScore())
        lifeCountText.text = getString(R.string.life_count, lifeManager.getLifeCount())
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        adView.resume()
    }

    override fun onPause() {
        adView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        adView.destroy()
        super.onDestroy()
    }
}
