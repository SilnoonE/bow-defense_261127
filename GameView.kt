package com.example.bowdefense

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

class GameView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    @Volatile private var isPlaying = false
    private var isSurfaceCreated = false
    private val holder: SurfaceHolder = getHolder()

    private val arrows = CopyOnWriteArrayList<Arrow>()
    private val monsters = CopyOnWriteArrayList<Monster>()
    var player = Player()

    val stageManager = StageManager()
    val upgradeManager = UpgradeManager()
    val purchaseStore = PurchaseStore(context)
    
    private var soundManager: SoundManager? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var backgroundBitmap: Bitmap? = null
    private var bowBitmap: Bitmap? = null
    private var normalBitmap: Bitmap? = null
    private var fastBitmap: Bitmap? = null
    private var tankBitmap: Bitmap? = null

    private var bowX = 250f
    private var bowY = 0f
    private var bowAngle = 0f
    private val BOW_SIZE = 672f 

    private val arrowPaint = Paint().apply { 
        color = Color.WHITE
        strokeWidth = 12f 
        strokeCap = Paint.Cap.ROUND 
    }
    
    private var isTouching = false
    private var lastSpawnTime = 0L
    var gameStartTime = 0L
    private var elapsedSeconds = 0
    private var multiShotLevel = 1 
    private var shakeIntensity = 0f 

    // 기록용 변수들
    var arrowsShot = 0
    var hitCount = 0
    var goldEarned = 0
    var goldSpent = 0 // 상점에서 사용 (추가 연동 필요)
    var shopPurchaseCount = 0 // 상점 구매 횟수 (추가 연동 필요)
    var killCount = 0
    var isLowHpSurvivalAchieved = false
    private var lowHpStartTime = 0L

    // 배틀 아이템 상태
    var isPowerArrowActive = false
    var powerArrowTimeLeft = 0L
    var isSlowStoneActive = false
    var slowStoneTimeLeft = 0L
    private var lastEffectUpdateTime = 0L

    var onStateChanged: (() -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null
    var onUpgrade: ((String) -> Unit)? = null
    var score = 0

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.RGBA_8888)
        loadResources()
    }

    private fun loadResources() {
        try {
            val options = BitmapFactory.Options().apply { inScaled = true }
            backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.background, options)
            
            val equippedSkinId = purchaseStore.getEquippedBow()
            val skinResId = resources.getIdentifier(equippedSkinId, "drawable", context.packageName)
            bowBitmap = BitmapFactory.decodeResource(resources, if (skinResId != 0) skinResId else R.drawable.bow, options)
            
            normalBitmap = BitmapFactory.decodeResource(resources, R.drawable.normal, options)
            fastBitmap = BitmapFactory.decodeResource(resources, R.drawable.fast, options)
            tankBitmap = BitmapFactory.decodeResource(resources, R.drawable.tank, options)
        } catch (e: Exception) {
            Log.e("GameView", "Error loading resources: ${e.message}")
        }
    }

    fun revive(lastScore: Int, lastStage: Int, lastWave: Int, cumulativeKills: Int, damage: Int, cooldown: Long) {
        this.score = lastScore
        this.stageManager.sync(lastStage, lastWave)
        this.upgradeManager.cumulativeKills = cumulativeKills
        this.player.hp = Config.PLAYER_MAX_HP
        this.player.currentDamage = damage
        
        // 부활 시에도 현재 장착된 활 성능 적용
        applyBowPerformance()
        
        val totalElapsedSeconds = (lastStage - 1) * 90 + (lastWave - 1) * 30
        this.gameStartTime = System.currentTimeMillis() - (totalElapsedSeconds * 1000L)
        this.monsters.clear()
        this.arrows.clear()
        onStateChanged?.invoke()
        resume()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceCreated = true
        if (bowY == 0f) bowY = height / 2f
        if (gameStartTime == 0L) gameStartTime = System.currentTimeMillis()
        loadResources()
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { bowY = height / 2f }
    override fun surfaceDestroyed(holder: SurfaceHolder) { isSurfaceCreated = false; pause() }

    override fun run() {
        while (isPlaying) {
            if (!isSurfaceCreated) { try { Thread.sleep(100) } catch (e: Exception) {}; continue }
            val startTime = System.currentTimeMillis()
            try {
                update()
                draw()
            } catch (e: Exception) {
                Log.e("GameView", "Error in game loop", e)
            }
            val timeTaken = System.currentTimeMillis() - startTime
            val sleepTime = 16L - timeTaken
            if (sleepTime > 0) { try { Thread.sleep(sleepTime) } catch (e: Exception) {} }
        }
    }

    private fun update() {
        if (width <= 0 || height <= 0) return
        elapsedSeconds = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()

        updateEffects()

        // 위기 극복 업적 체크 (HP 30% 이하 30초 생존)
        if (player.hp > 0 && player.hp <= Config.PLAYER_MAX_HP * 0.3f) {
            if (lowHpStartTime == 0L) lowHpStartTime = System.currentTimeMillis()
            else if (System.currentTimeMillis() - lowHpStartTime >= 30000L) {
                isLowHpSurvivalAchieved = true
            }
        } else {
            lowHpStartTime = 0L
        }

        val newWave = (elapsedSeconds / 30) % 3 + 1
        val newStage = (elapsedSeconds / 90) + 1
        
        if (stageManager.currentWave != newWave || stageManager.currentStage != newStage) {
            stageManager.currentWave = newWave
            stageManager.currentStage = newStage
            stageManager.monstersSpawned = 0
            onStateChanged?.invoke()
        }

        updatePlayerStats() 
        spawnMonsterSystem() 

        val arrowsToRemove = mutableListOf<Arrow>()
        val monstersToRemove = mutableListOf<Monster>()

        // 화살 업데이트
        arrows.forEach { arrow ->
            arrow.update()
            // 화살이 화면의 오른쪽 끝(width)을 넘어가면 즉시 제거 대상에 추가
            if (arrow.x > width || arrow.x < -200 || arrow.y < -200 || arrow.y > height + 200) {
                arrowsToRemove.add(arrow)
            }
        }

        // 몬스터 업데이트 및 충돌 검사
        monsters.forEach { monster ->
            if (isSlowStoneActive) monster.updateSlowed()
            else monster.update()
            
            if (monster.x < bowX) {
                player.hp -= monster.type.damage
                shakeIntensity = 20f 
                vibrate(50) 
                monstersToRemove.add(monster)
                onStateChanged?.invoke()
                if (player.hp <= 0 && isPlaying) {
                    player.hp = 0
                    isPlaying = false
                    onGameOver?.invoke(score) 
                }
            } else {
                for (arrow in arrows) {
                    if (arrowsToRemove.contains(arrow)) continue
                    if (RectF.intersects(monster.getBounds(), arrow.getBounds())) {
                        monster.takeDamage(arrow.damage)
                        arrowsToRemove.add(arrow)
                        hitCount++ // 명중 횟수 증가
                        if (monster.isDead()) {
                            monstersToRemove.add(monster)
                            score += monster.scoreValue
                            killCount++ // 처치 수 증가
                            // 골드 획득: 기본 점수의 10%를 골드로 환산 (최소 1골드)
                            val earned = (monster.scoreValue / 10).coerceAtLeast(1)
                            goldEarned += earned
                            purchaseStore.addGold(earned)
                            
                            onStateChanged?.invoke()
                            break 
                        }
                    }
                }
            }
        }

        if (arrowsToRemove.isNotEmpty()) arrows.removeAll(arrowsToRemove)
        if (monstersToRemove.isNotEmpty()) monsters.removeAll(monstersToRemove)

        if (isTouching && player.canFire()) {
            fireMultiShot()
            player.resetFireTime()
            soundManager?.playShoot()
        }
    }

    private fun vibrate(ms: Long) {
        if (!purchaseStore.isVibrationEnabled()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (e: Exception) {}
    }

    /**
     * 장착된 활에 따라 연사 속도를 적용하고, 게임 진행도(점수)에 따라 추가로 가속합니다.
     */
    private fun applyBowPerformance() {
        val equippedBow = purchaseStore.getEquippedBow()
        val baseCooldown = Config.BASE_FIRE_COOLDOWN // 500ms
        
        val bowMultiplier = when (equippedBow) {
            "high_bow" -> 0.7f // 아스트라 기어 보우: 30% 더 빠름
            "bow" -> 1.0f // 기본 활
            else -> 0.8f // 기타 유료 활: 20% 더 빠름
        }

        // 점수에 따른 점진적 연사 속도 증가 (최대 2배 속도까지, 즉 쿨타임 절반)
        // 10000점 기준 최대 속도 도달 예시
        val scoreFactor = (score / 10000f).coerceAtMost(0.5f)
        val finalCooldown = (baseCooldown * bowMultiplier * (1.0f - scoreFactor)).toLong()
        
        // 최소 쿨타임을 베이스의 절반(250ms)으로 제한
        player.fireCooldown = finalCooldown.coerceAtLeast(baseCooldown / 2)
    }

    private fun updatePlayerStats() {
        val oldLevel = multiShotLevel
        
        // 멀티샷 해금: 점수 1000점 단위 (1000점: 2단계, 2000점: 3단계)
        multiShotLevel = when {
            score < 1000 -> 1
            score < 2000 -> 2
            else -> 3
        }

        // 장착된 활에 따른 연사 속도 고정 적용
        applyBowPerformance()

        // 데미지는 시간 경과에 따라 강화
        when {
            elapsedSeconds < Config.TIME_STEP_1 -> player.currentDamage = 1
            elapsedSeconds < Config.TIME_STEP_2 -> player.currentDamage = 2
            elapsedSeconds < Config.TIME_STEP_3 -> player.currentDamage = 3
            elapsedSeconds < Config.TIME_STEP_4 -> player.currentDamage = 4
            elapsedSeconds < Config.TIME_STEP_5 -> player.currentDamage = 6
            else -> player.currentDamage = 7 + (elapsedSeconds - 120) / 30
        }
        
        if (oldLevel != multiShotLevel) {
            onUpgrade?.invoke(context.getString(R.string.multishot_unlocked, multiShotLevel))
        }
    }

    private fun spawnMonsterSystem() {
        val currentTime = System.currentTimeMillis()
        
        // 화면에 있는 몬스터 수 제한 (최대 15마리)
        if (monsters.size >= 15) return

        // 스폰 간격 설정: 1000점 이후에도 무조건 1초(1000ms) 내외로 등장하도록 고정
        // 초반에는 조금 더 길게, 1000점 이후에는 0.6~0.9초 사이로 유지
        val spawnInterval = when {
            score < 500 -> 1800L
            score < 1000 -> 1200L
            else -> 800L // 1000점 이후에는 약 0.8초마다 한 마리씩 꾸준히 등장
        }

        if (currentTime - lastSpawnTime >= spawnInterval) {
            val type = getMonsterTypeByTime()
            
            // 난이도 조절 로직
            var hpMult = 1f + (elapsedSeconds / 60f) * 0.5f
            if (score >= 1000) {
                hpMult += ((score - 1000) / 200f)
            }

            var spdMult = 1f + (elapsedSeconds / 80f) * 0.1f
            if (score >= 1000) {
                spdMult += (score - 1000) / 5000f
            }
            spdMult = spdMult.coerceAtMost(3.0f)

            val minY = 150f
            val maxY = (height - 300f).coerceAtLeast(minY + 1f)
            val spawnY = (minY.toInt()..maxY.toInt()).random().toFloat()
            
            monsters.add(Monster(width.toFloat(), spawnY,
                (type.baseHp * hpMult).toInt(), type.speed * spdMult, type, type.score))
            
            stageManager.monstersSpawned++
            lastSpawnTime = currentTime
        }
    }

    private fun getMonsterTypeByTime(): Config.MonsterType {
        val rand = (1..100).random()
        return when {
            elapsedSeconds < Config.TIME_STEP_1 -> if (rand <= 85) Config.MonsterType.NORMAL else Config.MonsterType.FAST
            elapsedSeconds < Config.TIME_STEP_3 -> if (rand <= 65) Config.MonsterType.NORMAL else if (rand <= 95) Config.MonsterType.FAST else Config.MonsterType.TANK
            else -> if (rand <= 40) Config.MonsterType.NORMAL else if (rand <= 75) Config.MonsterType.FAST else Config.MonsterType.TANK
        }
    }

    private fun fireMultiShot() {
        val rad = Math.toRadians(bowAngle.toDouble())
        val startX = bowX + (BOW_SIZE / 3f * cos(rad)).toFloat()
        val startY = bowY + (BOW_SIZE / 3f * sin(rad)).toFloat()
        
        val offsetX = (45f * sin(rad)).toFloat()
        val offsetY = (45f * cos(rad)).toFloat()

        val damage = if (isPowerArrowActive) (player.currentDamage * 1.5f).toInt() else player.currentDamage

        when (multiShotLevel) {
            1 -> {
                arrows.add(Arrow(startX, startY, bowAngle, player.arrowSpeed, damage))
                arrowsShot++
            }
            2 -> {
                arrows.add(Arrow(startX + offsetX/2, startY - offsetY/2, bowAngle, player.arrowSpeed, damage))
                arrows.add(Arrow(startX - offsetX/2, startY + offsetY/2, bowAngle, player.arrowSpeed, damage))
                arrowsShot += 2
            }
            3 -> {
                arrows.add(Arrow(startX + offsetX, startY - offsetY, bowAngle, player.arrowSpeed, damage))
                arrows.add(Arrow(startX, startY, bowAngle, player.arrowSpeed, damage))
                arrows.add(Arrow(startX - offsetX, startY + offsetY, bowAngle, player.arrowSpeed, damage))
                arrowsShot += 3
            }
        }
    }

    private fun updateEffects() {
        val now = System.currentTimeMillis()
        if (lastEffectUpdateTime == 0L) lastEffectUpdateTime = now
        val delta = now - lastEffectUpdateTime
        lastEffectUpdateTime = now

        if (isPowerArrowActive) {
            powerArrowTimeLeft -= delta
            if (powerArrowTimeLeft <= 0) {
                isPowerArrowActive = false
                powerArrowTimeLeft = 0
            }
        }

        if (isSlowStoneActive) {
            slowStoneTimeLeft -= delta
            if (slowStoneTimeLeft <= 0) {
                isSlowStoneActive = false
                slowStoneTimeLeft = 0
            }
        }
    }

    private fun draw() {
        if (!holder.surface.isValid) return
        val canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
        try {
            if (shakeIntensity > 0) {
                val sx = (Math.random() * shakeIntensity * 2 - shakeIntensity).toFloat()
                val sy = (Math.random() * shakeIntensity * 2 - shakeIntensity).toFloat()
                canvas.translate(sx, sy)
                shakeIntensity *= 0.9f
                if (shakeIntensity < 0.5f) shakeIntensity = 0f
            }
            canvas.drawColor(Color.BLACK)
            if (backgroundBitmap != null) canvas.drawBitmap(backgroundBitmap!!, null, Rect(0, 0, width, height), null)
            monsters.forEach { monster ->
                val bitmap = when(monster.type) {
                    Config.MonsterType.NORMAL -> normalBitmap
                    Config.MonsterType.FAST -> fastBitmap
                    Config.MonsterType.TANK -> tankBitmap
                }
                
                // 탱크 몬스터는 크기 1.4배 적용 (기존 2배에서 30% 축소: 2.0 * 0.7 = 1.4)
                val isTank = monster.type == Config.MonsterType.TANK
                val sizeMultiplier = if (isTank) 1.4f else 1.0f
                val renderSize = Config.MONSTER_RENDER_SIZE * sizeMultiplier

                if (bitmap != null) canvas.drawBitmap(bitmap, null, RectF(monster.x, monster.y, monster.x + renderSize, monster.y + renderSize), null)
                else {
                    val p = Paint().apply { color = monster.type.color }
                    canvas.drawRect(monster.x, monster.y, monster.x + renderSize, monster.y + renderSize, p)
                }
            }
            arrows.forEach { arrow ->
                canvas.save()
                canvas.rotate(arrow.angle, arrow.x, arrow.y)
                if (isPowerArrowActive) arrowPaint.color = Color.RED else arrowPaint.color = Color.WHITE
                canvas.drawLine(arrow.x, arrow.y, arrow.x + 100f, arrow.y, arrowPaint)
                canvas.restore()
            }
            
            // 시간 지연 효과 (푸른 파동)
            if (isSlowStoneActive) {
                val p = Paint().apply {
                    color = Color.BLUE
                    alpha = 30
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            }

            canvas.save()
            canvas.translate(bowX, bowY); canvas.rotate(bowAngle)
            if (isPowerArrowActive) {
                val glowPaint = Paint().apply {
                    color = Color.RED
                    maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawCircle(0f, 0f, BOW_SIZE/2, glowPaint)
            }
            if (bowBitmap != null) canvas.drawBitmap(bowBitmap!!, null, RectF(-BOW_SIZE/2, -BOW_SIZE/2, BOW_SIZE/2, BOW_SIZE/2), null)
            canvas.restore()
        } finally { try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {} }
    }

    fun setSoundManager(sm: SoundManager) { this.soundManager = sm }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - bowX; val dy = event.y - bowY
        bowAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> isTouching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isTouching = false
        }
        performClick(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun resume() { if (isPlaying || !isSurfaceCreated) return; isPlaying = true; gameThread = Thread(this); gameThread?.start() }
    fun pause() { isPlaying = false; try { gameThread?.join(500); gameThread = null } catch (e: Exception) {} }
}
