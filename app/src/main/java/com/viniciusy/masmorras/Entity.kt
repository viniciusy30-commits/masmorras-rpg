package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI_F = 3.1415927f

abstract class Entity(
    var x: Float,
    var y: Float,
    var radius: Float,
    var hp: Float,
    var maxHp: Float
) {
    var alive = true
    var hitFlash = 0f
    var walkPhase = 0f
    var alt = 0f     // altitude absoluta (px)
    var ground = 0f  // altura do chão sob os pés (px)
    var shield = 0f  // escudo: absorve o dano antes da vida (Sylvarion)
    var reviveHook: (() -> Boolean)? = null // devolve true se reviveu (revive, se algum talento usar)

    fun takeDamage(dmg0: Float) {
        if (!alive) return
        var dmg = dmg0
        if (shield > 0f) {
            val abs = min(shield, dmg)
            shield -= abs
            dmg -= abs
            if (dmg <= 0f) {
                hitFlash = 1f
                return
            }
        }
        hp -= dmg
        hitFlash = 1f
        if (hp <= 0f) {
            if (reviveHook?.invoke() == true) return
            hp = 0f
            alive = false
        }
    }

    abstract fun update(dt: Float)
    abstract fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float)

    fun getBounds(): RectF = RectF(x - radius, y - radius, x + radius, y + radius)

    protected fun drawBar(canvas: Canvas, paint: Paint, cx: Float, top: Float, w: Float, pct: Float, color: Int) {
        val h = 6f
        paint.style = Paint.Style.FILL
        paint.color = 0xFF111111.toInt()
        canvas.drawRoundRect(cx - w / 2f - 1.5f, top - 1.5f, cx + w / 2f + 1.5f, top + h + 1.5f, 3f, 3f, paint)
        paint.color = 0xFF3A3A3A.toInt()
        canvas.drawRect(cx - w / 2f, top, cx + w / 2f, top + h, paint)
        paint.color = color
        canvas.drawRect(cx - w / 2f, top, cx - w / 2f + w * pct.coerceIn(0f, 1f), top + h, paint)
        paint.color = 0x44FFFFFF
        canvas.drawRect(cx - w / 2f, top, cx - w / 2f + w * pct.coerceIn(0f, 1f), top + h * 0.4f, paint)
    }
}

class Player(
    x: Float,
    y: Float,
    val classe: Classe
) : Entity(x, y, 24f, Herois.perfil(classe).maxHp, Herois.perfil(classe).maxHp) {

    companion object {
        const val ROLL_TIME = 0.46f
        const val DASH_TIME = 0.34f  // impulso do bárbaro
        const val MAGE_RATE = 2.3f
        const val BOW_HOLD_AT = 0.44f // ponto da animação (0..1) em que o arco fica puxado esperando soltar
        const val ARCHER_RATE = 2.6f // velocidade da animação do tiro (T_SHOT sincroniza a saída da flecha)
        const val HOP_TIME = 0.44f   // salto-dash do arqueiro
        const val RAIN_CD = 7000L    // recarga da Chuva de Flechas (ms)
        const val THUNDER_CD = 9000L // recarga da Flecha Trovão (ms)
        const val TP_TIME = 0.34f   // duração da animação do teleporte
        const val TP_COST = 15f     // mana gasta no teleporte
        const val TP_DIST = 165f    // alcance máximo do teleporte
        const val FLY_MAX = 2.8f    // segundos de voo por carga
        const val FLY_H = 70f       // altura do voo
        const val NINJA_DASH_DIST = 270f   // alcance do pulo-dash do ninja
        const val NINJA_DASH_T = 0.36f     // duração do pulo-dash para a frente
        const val NINJA_UP_T = 0.55f       // duração do pulo-dash reto para cima
        const val NINJA_RATE_1 = 2.7f      // velocidade dos 2 primeiros golpes do combo
        const val NINJA_RATE_3 = 2.2f      // velocidade do 3º golpe
        val NINJA_COST = floatArrayOf(0f, 18f, 22f, 20f, 35f)          // mana de Q, W, E, R
        val NINJA_CD = longArrayOf(0L, 5000L, 9000L, 6500L, 20000L)    // recarga de Q, W, E, R (ms)
        const val PIKA_RATE = 3.0f         // velocidade da animação do Choque do Trovão
        const val PIKA_QUICK_T = 0.30f     // duração do Ataque Rápido (o "rolamento" do Pikachu)
        const val PIKA_VOLT_T = 0.50f      // duração do avanço da Investida Trovão
        const val PIKA_VOLT_SPD = 560f     // velocidade do avanço da Investida Trovão
        val PIKA_COST = floatArrayOf(0f, 20f, 15f, 25f, 40f)           // mana: Trovoada, Cauda de Ferro, Investida, Trovão
        val PIKA_CD = longArrayOf(0L, 4500L, 6000L, 9000L, 16000L)     // recarga (ms)
        const val ICHI_RATE_1 = 2.6f       // velocidade dos 2 primeiros golpes do combo do Ichigo
        const val ICHI_RATE_3 = 2.1f       // velocidade do 3º golpe
        const val LUFFY_RATE_1 = 2.9f      // velocidade dos 2 primeiros socos do combo do Luffy
        const val LUFFY_RATE_3 = 2.2f      // velocidade do soco duplo
        const val LUFFY_REACH = 150f       // alcance do soco esticado (Gomu Gomu no Pistol)
        const val LUFFY_REACH_2 = 180f     // alcance do soco duplo (Bazooka)
        const val EXPL_RATE_1 = 1.55f      // velocidade dos 2 primeiros estalos do chicote do Explorador
        const val EXPL_RATE_3 = 1.2f       // velocidade da chicotada por cima
        const val EXPL_REACH = 165f        // alcance do estalo do chicote
        const val EXPL_REACH_3 = 195f      // alcance da chicotada por cima
        const val SWING_LEN = 120f         // comprimento da corda do balanço do Explorador
        const val SWING_G = 1500f          // gravidade do balanço
        const val SWING_PUMP = 900f        // empurrão do balanço para o lado em que o jogador anda
        const val SWING_MAXV = 620f        // velocidade máxima do balanço
        const val LUFFY_DASH_T = 0.50f     // duração do dash estilingue (preparação + lançamento)
        const val LUFFY_DASH_V = 1500f     // velocidade inicial do lançamento
        // distância percorrida no lançamento (a velocidade cai com (1-v)^1.2, cuja integral vale 1/2.2)
        val LUFFY_DASH_DIST: Float = LUFFY_DASH_V * LUFFY_DASH_T * (1f - Luffy3D.DASH_WIND) / 2.2f
        const val ICHI_HOLLOW_T = 32f      // duração da forma Vasto Lorde (s)
        const val ICHI_DASH_T = 0.24f      // duração do Shunpo / Sonído (botão de rolar)
        const val ICHI_DASH_DIST = 250f    // distância do Shunpo / Sonído
        const val ICHI_DASH_CD = 800L      // recarga do Shunpo / Sonído (ms)
        const val ICHI_DASH_SPD = 1200f    // velocidade de referência
        const val ICHI_SKY_COST = 3.5f     // mana por segundo andando no céu
        const val ICHI_SKY_H = 78f         // altura da plataforma de reiatsu
        // Bankai: Getsuga Tenshou, Getsuga Jujisho, Redemoinho de Getsuga, Golpe Lunar, Vasto Lorde
        val ICHI_COST_N = floatArrayOf(0f, 20f, 24f, 26f, 22f, 40f)
        val ICHI_CD_N = longArrayOf(0L, 3500L, 5000L, 8000L, 6500L, 24000L)
        // Vasto Lorde: Cero, Garras Rasgantes, Grito Hollow, Cero Oscuras, Mugetsu
        val ICHI_COST_H = floatArrayOf(0f, 26f, 14f, 28f, 36f, 40f)
        val ICHI_CD_H = longArrayOf(0L, 4500L, 3000L, 9000L, 11000L, 16000L)
    }

    // herói escolhido na criação de personagem (raça + atributos)
    val heroi: HeroiPerfil = Herois.perfil(classe)
    val maxMpH: Float = heroi.maxMp
    val velH: Float = heroi.velocidade
    val danoH: Float = heroi.dano
    val defesaH: Float = heroi.defesa
    val regenHpH: Float = heroi.regenHp
    val regenMpH: Float = heroi.regenMp
    val critH: Float = heroi.critChance
    val critMulH: Float = heroi.critMul

    init {
        Herois.ativar(heroi)
    }

    var mp = maxMpH
    var vx = 0f
    var vy = 0f
    var inX = 0f
    var inY = 0f
    var facingX = 1f
    var facingY = 0f
    var lastAttack = 0L
    var attackAnim = 0f

    // pulo e rolamento
    var vz = 0f
    var rollT = 0f
    var rollDur = ROLL_TIME
    var dashing = false

    // bárbaro: salto carregado (segurar pular) e pouso destruidor
    var leapCharging = false
    var leapChargeT = 0f
    var leaping = false
    var leapAir = false
    var leapDX = 0f
    var leapDY = 0f
    var leapSpd = 0f
    var leapClock = 0f
    var rollDX = 0f
    var rollDY = 1f
    var lastRoll = 0L
    var landed = 0f
    var landSquash = 0f
    var attackPunch = 0f

    val rolling: Boolean get() = rollT > 0f
    val grounded: Boolean get() = alt <= ground + 0.6f && vz <= 0f

    // combo do guerreiro: 0 corte diagonal, 1 corte vertical (volta), 2 estocada; 3 = golpe forte
    var comboStep = -1
    var swing = 0
    private var attackRate = 2.5f
    var charging = false
    var chargeT = 0f
    // mago: teleporte (no lugar do rolamento) e voo limitado (segurar o botão de pular)
    var tpT = 0f
    var tpFromX = 0f
    var tpFromY = 0f
    var lastTp = 0L
    var flying = false
    var glide = false
    var fuel = FLY_MAX
    private var flyClock = 0f
    val phased: Boolean get() = tpT > 0.14f || phaseT > 0f
    var weapon = 0 // 0 espada e escudo, 1 lança
    var spearOut = false // lança arremessada, ainda no chão esperando ser recolhida
    var swapUntil = 0L
    // arqueiro: salto-dash (planta armadilha) e skills (1 = chuva de flechas, 2 = flecha trovão)
    var hopping = false
    // pulo duplo do arqueiro: segundo toque no ar = mortal (flipT conta de flipDur até 0)
    var airJumped = false
    var flipT = 0f
    // arqueiro: segurando o botão de ataque ele fica com o arco puxado, mirando (solta = atira)
    var bowHold = false
    var bowPow = 0f
    var flipDur = 0.5f
    val flipping: Boolean get() = flipT > 0f
    var trapFx = false
    // armadilha arremessada no meio do salto (o GameView cria o objeto quando trapThrowReq liga)
    var trapThrown = false
    var trapThrowReq = false
    var hopX0 = 0f
    var hopY0 = 0f
    var trapRelX = 0f
    var trapRelY = 0f
    var trapRelUp = 0f
    var spdMul = 1f // multiplicador de velocidade de movimento (buffs do Sylvarion)
    var skillK = 0
    var skillT = 0f
    var skillDur = 1f
    var rainReadyAt = 0L
    var thunderReadyAt = 0L
    val skilling: Boolean get() = skillK != 0

    // ninja: pulo-dash (2º toque no ar), invulnerabilidade curta e recarga das 4 skills
    var dashT = 0f
    var dashDur = NINJA_DASH_T
    var dashDX = 0f
    var dashDY = 0f
    var dashUp = false
    var phaseT = 0f
    private val ninjaReadyAt = LongArray(5)

    // pikachu: avanço da Investida Trovão e recarga das 4 skills
    var voltT = 0f
    var voltDX = 0f
    var voltDY = 0f
    private val pikaReadyAt = LongArray(5)

    // ichigo: forma Vasto Lorde, avanço do Shunpo/Sonído e recarga das skills de cada forma
    var hollow = false
    var hollowT = 0f
    var endHollow = false
    var ichiSkH = false
    var ichiT = 0f
    var ichiDX = 0f
    var ichiDY = 0f
    var ichiDurNow = ICHI_DASH_T   // duração do avanço atual
    var ichiSpdNow = 1000f         // velocidade inicial do avanço atual
    var ichiKind = 0               // 1 = dash (botão de rolar), 2 = avanço de skill
    var lastIchiDash = 0L
    var sky = false                // andando no céu (segurar pular)
    private val skyOval = RectF()

    // explorador: pendurado no chicote (apertar pular no ar e segurar). O ponto de ancoragem fica no céu; o corpo é um pêndulo
    var swinging = false
    var swGrow = 0f                // 0..1: a corda sobe até o ponto de ancoragem
    var swAX = 0f
    var swAY = 0f
    var swAAlt = 0f                // altitude absoluta da ancoragem
    var swOX = 0f                  // posição do corpo em relação à ancoragem
    var swOY = 0f
    var swOZ = 0f
    var swVX = 0f
    var swVY = 0f
    var swVZ = 0f
    var momX = 0f                  // embalo ao soltar a corda
    var momY = 0f

    /** Lança o chicote para o céu e começa a balançar para o lado em que está andando (ou olhando). */
    fun startSwing(): Boolean {
        if (classe != Classe.EXPLORADOR || !alive || swinging || rolling || skillK != 0 || grounded) return false
        val up = (alt - ground).coerceAtLeast(0f)
        var dx = inX
        var dy = inY
        if (hypot(dx, dy) < 0.1f) {
            dx = facingX
            dy = facingY
        }
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l
        dy /= l
        val len = SWING_LEN
        val top = max(len + 28f, up + len * 0.85f)
        val cosT = ((top - up) / len).coerceIn(0.2f, 1f)
        val sinT = sqrt(1f - cosT * cosT)
        // a ancoragem fica à frente e no alto; o corpo começa onde está, atrás do ponto mais baixo, e já vai para a frente
        swOX = -dx * len * sinT
        swOY = -dy * len * sinT
        swOZ = -len * cosT
        swAX = x - swOX
        swAY = y - swOY
        swAAlt = alt - swOZ
        swVX = dx * 200f
        swVY = dy * 200f
        swVZ = max(0f, vz) * 0.4f
        swGrow = 0f
        swinging = true
        attackAnim = 0f
        charging = false
        chargeT = 0f
        flipT = 0f
        return true
    }

    /** Solta a corda: o corpo sai voando com o embalo do balanço. */
    fun endSwing() {
        if (!swinging) return
        swinging = false
        vz = swVZ.coerceIn(-300f, 560f)
        momX = swVX * 0.85f
        momY = swVY * 0.85f
        airJumped = false
    }

    private fun swingStep(dt: Float) {
        swGrow = min(1f, swGrow + dt / 0.16f)
        // se o mundo barrou o movimento, perde o embalo para dentro da parede
        val cx = x - swAX
        val cy = y - swAY
        if (hypot(cx - swOX, cy - swOY) > 0.8f) {
            swVX *= 0.25f
            swVY *= 0.25f
        }
        swOX = cx
        swOY = cy
        val h = hypot(inX, inY)
        val moving = h > 0.1f
        val pump = if (moving) SWING_PUMP / h else 0f
        swVX += inX * pump * dt
        swVY += inY * pump * dt
        swVZ -= SWING_G * dt
        val damp = max(0f, 1f - (if (moving) 0.25f else 0.9f) * dt)
        swVX *= damp
        swVY *= damp
        val hv = hypot(swVX, swVY)
        if (hv > SWING_MAXV) {
            swVX *= SWING_MAXV / hv
            swVY *= SWING_MAXV / hv
        }
        swOX += swVX * dt
        swOY += swVY * dt
        swOZ += swVZ * dt
        // a corda é inextensível: projeta o corpo de volta na esfera e tira só a velocidade que puxa para fora
        val r = sqrt(swOX * swOX + swOY * swOY + swOZ * swOZ)
        if (r > SWING_LEN) {
            val nx = swOX / r
            val ny = swOY / r
            val nz = swOZ / r
            swOX = nx * SWING_LEN
            swOY = ny * SWING_LEN
            swOZ = nz * SWING_LEN
            val vn = swVX * nx + swVY * ny + swVZ * nz
            if (vn > 0f) {
                swVX -= nx * vn
                swVY -= ny * vn
                swVZ -= nz * vn
            }
        }
        alt = swAAlt + swOZ
        if (alt < ground + 2f) {
            alt = ground + 2f
            swOZ = alt - swAAlt
            if (swVZ < 0f) swVZ = 0f
        }
        vz = swVZ
        if (facingX * swVX + facingY * swVY < 0f && !moving) {
            // sem comando, o corpo olha para onde está indo
            val l = hypot(swVX, swVY)
            if (l > 40f) {
                facingX = swVX / l
                facingY = swVY / l
            }
        }
    }
    private val ichiReadyN = LongArray(6)
    private val ichiReadyH = LongArray(6)

    fun ichiCost(k: Int): Float = (if (hollow) ICHI_COST_H else ICHI_COST_N)[k.coerceIn(1, 5)]

    fun canAttack(now: Long): Boolean {
        if (rolling || swinging || skillK != 0 || voltT > 0f || ichiT > 0f || now < swapUntil || leapCharging || leaping || flipT > 0f || (dashT > 0f && !dashUp)) return false
        val cd = if (classe == Classe.GUERREIRO) (if (comboStep == 2 || comboStep == -1 && swing == 3) 680L else 380L)
        else if (classe == Classe.BARBARO) (if (comboStep == 2) 720L else 440L)
        else if (classe == Classe.NINJA) (if (comboStep == 2) 520L else 300L)
        else if (classe == Classe.ICHIGO) (if (comboStep == 2) 560L else 320L)
        else if (classe == Classe.LUFFY) (if (comboStep == 2) 520L else 280L)
        else if (classe == Classe.EXPLORADOR) (if (comboStep == 2) 760L else 560L)
        else classe.cooldownAtaque
        return now - lastAttack >= cd
    }

    fun doAttack(now: Long) {
        if (classe == Classe.GUERREIRO) {
            val inWindow = comboStep >= 0 && now - lastAttack < 1200L
            comboStep = if (inWindow) (comboStep + 1) % 3 else 0
            swing = comboStep
            attackRate = if (weapon == 1) when (swing) {
                0 -> 2.0f
                1 -> 1.7f
                else -> 1.4f
            } else when (swing) {
                0 -> 1.65f
                1 -> 1.95f
                else -> 1.55f
            }
        }
        if (classe == Classe.BARBARO) {
            // combo: machado direito, machado esquerdo, batida dupla
            val inWindow = comboStep >= 0 && now - lastAttack < 1000L
            comboStep = if (inWindow) (comboStep + 1) % 3 else 0
            swing = comboStep
            attackRate = when (swing) {
                0 -> 1.7f
                1 -> 1.7f
                else -> 1.35f
            }
        }
        if (classe == Classe.NINJA) {
            // combo de 3: lâmina direita, lâmina esquerda e corte cruzado
            val inWindow = comboStep >= 0 && now - lastAttack < 900L
            comboStep = if (inWindow) (comboStep + 1) % 3 else 0
            swing = comboStep
            attackRate = if (swing == 2) NINJA_RATE_3 else NINJA_RATE_1
        }
        if (classe == Classe.ICHIGO) {
            // combo de 3: corte direito, corte esquerdo e golpe cruzado (na forma Vasto Lorde, garras)
            val inWindow = comboStep >= 0 && now - lastAttack < 900L
            comboStep = if (inWindow) (comboStep + 1) % 3 else 0
            swing = comboStep
            attackRate = if (swing == 2) ICHI_RATE_3 else ICHI_RATE_1
        }
        if (classe == Classe.LUFFY) {
            // combo de 3: soco de direita, soco de esquerda e os dois punhos juntos
            val inWindow = comboStep >= 0 && now - lastAttack < 900L
            comboStep = if (inWindow) (comboStep + 1) % 3 else 0
            swing = comboStep
            attackRate = if (swing == 2) LUFFY_RATE_3 else LUFFY_RATE_1
        }
        if (classe == Classe.EXPLORADOR) {
            // combo de 3: estalo de direita, estalo de revés e chicotada por cima
            val inWindow = comboStep >= 0 && now - lastAttack < 1300L
            comboStep = if (inWindow) (comboStep + 1) % 3 else 0
            swing = comboStep
            attackRate = if (swing == 2) EXPL_RATE_3 else EXPL_RATE_1
        }
        if (classe == Classe.MAGO) attackRate = MAGE_RATE
        if (classe == Classe.ARQUEIRO) attackRate = ARCHER_RATE
        if (classe == Classe.PIKACHU) attackRate = PIKA_RATE
        lastAttack = now
        attackAnim = 1f
        attackPunch = if (classe == Classe.MAGO) 0.5f else if (classe == Classe.ARQUEIRO) 0.35f else if (classe == Classe.NINJA) 0.6f else if (classe == Classe.PIKACHU) 0.4f else if (classe == Classe.ICHIGO) 0.7f else if (classe == Classe.LUFFY) 0.8f else if (classe == Classe.EXPLORADOR) 0.8f else 1f
    }

    /** Golpe forte (solta o botão depois de segurar). */
    fun doHeavy(now: Long) {
        comboStep = -1
        swing = 3
        attackRate = if (weapon == 1) 0.95f else 1.15f
        charging = false
        chargeT = 0f
        lastAttack = now
        attackAnim = 1f
        attackPunch = 1.6f
    }

    /** Segundos até a ponta da lança acertar (o dano só sai nesse instante, sincronizado com a animação). */
    fun strikeDelay(): Float = when (swing) {
        0 -> 0.30f / 2.0f
        1 -> 0.38f / 1.7f
        2 -> 0.42f / 1.4f
        else -> 0.30f / 0.95f
    }

    /** Segundos até o machado do bárbaro acertar (sincronizado com Barbarian3D.impactAt). */
    fun barbDelay(): Float = Barbarian3D.impactAt(swing) / attackRate

    /** Segundos até o orbe sair do cajado do mago (sincronizado com Mage3D.T_M). */
    fun castDelay(): Float = Mage3D.T_M / MAGE_RATE

    /** Segundos até a flecha sair do arco do arqueiro (sincronizado com Archer3D.T_SHOT). */
    fun shotDelay(): Float = Archer3D.T_SHOT / ARCHER_RATE

    /** Segundos até a lâmina do ninja acertar (sincronizado com Ninja3D.impactAt). */
    fun ninjaDelay(): Float = Ninja3D.impactAt(swing) / attackRate

    /** Segundos até a lâmina (ou garra) do Ichigo acertar (sincronizado com Ichigo3D.impactAt). */
    fun ichiDelay(): Float = Ichigo3D.impactAt(swing) / attackRate

    /** Segundos até o punho esticado do Luffy chegar ao alvo (sincronizado com Luffy3D.T_HIT). */
    fun luffyDelay(): Float = Luffy3D.impactAt(swing) / attackRate

    /** Segundos até a corda do chicote estalar no alcance máximo (sincronizado com Explorer3D.T_HIT). */
    fun explorerDelay(): Float = Explorer3D.impactAt(swing) / attackRate

    /** Segundos até o raio sair das mãos do Pikachu (sincronizado com Pikachu3D.T_SHOT). */
    fun pikaDelay(): Float = Pikachu3D.T_SHOT / PIKA_RATE

    /** Salto-dash do arqueiro: pulinho baixo com cambalhota na direção que está olhando. */
    fun startHop(now: Long): Boolean {
        if (classe != Classe.ARQUEIRO || !alive || rolling || skillK != 0 || !grounded || now - lastRoll < 700L) return false
        var dx = facingX
        var dy = facingY
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        dx /= l
        dy /= l
        rollDX = dx
        rollDY = dy
        rollT = HOP_TIME
        rollDur = HOP_TIME
        dashing = false
        hopping = true
        airJumped = false
        flipT = 0f
        vz = 330f
        alt = ground + 0.7f
        lastRoll = now
        hopX0 = x
        hopY0 = y
        trapThrown = false
        trapThrowReq = false
        attackAnim = 0f
        charging = false
        chargeT = 0f
        return true
    }

    /** Avanço veloz do Ichigo: [dist] px em [dur] s (kind 1 = Shunpo/Sonído no botão de rolar, 2 = avanço de skill). */
    fun beginIchiDash(dx: Float, dy: Float, dist: Float, dur: Float, kind: Int) {
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        ichiDX = dx / l
        ichiDY = dy / l
        ichiDurNow = dur
        ichiSpdNow = dist / (dur * 0.8f)
        ichiKind = kind
        ichiT = dur
        phaseT = max(phaseT, dur + 0.1f)
        facingX = ichiDX
        facingY = ichiDY
        attackAnim = 0f
        charging = false
        chargeT = 0f
    }

    /** Dash do botão de rolar (só Ichigo): pronto quando não há skill, avanço ou recarga em andamento. */
    fun ichiDashReady(now: Long): Boolean =
        classe == Classe.ICHIGO && alive && skillK == 0 && ichiT <= 0f && !rolling && now - lastIchiDash >= ICHI_DASH_CD

    fun skillReady(k: Int, now: Long): Boolean {
        if (classe == Classe.ICHIGO) {
            val rd = if (hollow) ichiReadyH else ichiReadyN
            return k in 1..5 && alive && skillK == 0 && ichiT <= 0f && !rolling && (grounded || sky) && now >= rd[k] && mp >= ichiCost(k)
        }
        if (classe == Classe.PIKACHU) {
            return k in 1..4 && alive && skillK == 0 && voltT <= 0f && !rolling && grounded && now >= pikaReadyAt[k] && mp >= PIKA_COST[k]
        }
        if (classe == Classe.NINJA) {
            return k in 1..3 && alive && skillK == 0 && !rolling && grounded && dashT <= 0f && now >= ninjaReadyAt[k] && mp >= NINJA_COST[k]
        }
        return classe == Classe.ARQUEIRO && alive && skillK == 0 && !rolling && grounded &&
            now >= (if (k == 1) rainReadyAt else thunderReadyAt)
    }

    /** Segundos de recarga que faltam (0 = pronta). */
    fun skillCd(k: Int, now: Long): Float {
        if (classe == Classe.ICHIGO) {
            val rd = if (hollow) ichiReadyH else ichiReadyN
            return if (k in 1..5) ((rd[k] - now) / 1000f).coerceAtLeast(0f) else 0f
        }
        if (classe == Classe.PIKACHU) return if (k in 1..4) ((pikaReadyAt[k] - now) / 1000f).coerceAtLeast(0f) else 0f
        if (classe == Classe.NINJA) return if (k in 1..4) ((ninjaReadyAt[k] - now) / 1000f).coerceAtLeast(0f) else 0f
        return (((if (k == 1) rainReadyAt else thunderReadyAt) - now) / 1000f).coerceAtLeast(0f)
    }

    fun startSkill(k: Int, now: Long): Boolean {
        if (!skillReady(k, now)) return false
        skillK = k
        if (classe == Classe.ICHIGO) {
            ichiSkH = hollow
            skillDur = Ichigo3D.dur(hollow, k)
            mp -= ichiCost(k)
            if (hollow) ichiReadyH[k] = now + ICHI_CD_H[k] else ichiReadyN[k] = now + ICHI_CD_N[k]
        } else if (classe == Classe.PIKACHU) {
            skillDur = Pikachu3D.DUR[k]
            mp -= PIKA_COST[k]
            pikaReadyAt[k] = now + PIKA_CD[k]
        } else if (classe == Classe.NINJA) {
            skillDur = when (k) {
                1 -> Ninja3D.Q_DUR
                2 -> Ninja3D.W_DUR
                3 -> Ninja3D.E_DUR
                else -> Ninja3D.R_DUR
            }
            mp -= NINJA_COST[k]
            ninjaReadyAt[k] = now + NINJA_CD[k]
        } else {
            skillDur = if (k == 1) Archer3D.RAIN_DUR else Archer3D.THUNDER_DUR
            if (k == 1) rainReadyAt = now + RAIN_CD else thunderReadyAt = now + THUNDER_CD
        }
        skillT = skillDur
        attackAnim = 0f
        charging = false
        chargeT = 0f
        return true
    }

    /** Devolve a mana e zera a recarga de uma skill do ninja que não chegou a sair (sem alvo, por exemplo). */
    fun refundNinja(k: Int) {
        if (classe != Classe.NINJA || k !in 1..4) return
        mp = (mp + NINJA_COST[k]).coerceAtMost(maxMpH)
        ninjaReadyAt[k] = 0L
        skillK = 0
        skillT = 0f
    }

    /** Segundos desde o começo da skill até ela sair (flecha, shuriken, sombra, corte ou salto). */
    fun skillReleaseAt(k: Int): Float {
        if (classe == Classe.ICHIGO) return Ichigo3D.dur(ichiSkH, k) * Ichigo3D.rel(ichiSkH, k)
        if (classe == Classe.PIKACHU) return Pikachu3D.DUR[k] * Pikachu3D.REL[k]
        if (classe == Classe.NINJA) {
            return when (k) {
                1 -> Ninja3D.Q_DUR * Ninja3D.Q_REL
                2 -> Ninja3D.W_DUR * Ninja3D.W_REL
                3 -> Ninja3D.E_DUR * Ninja3D.E_REL
                else -> Ninja3D.R_DUR * Ninja3D.R_REL
            }
        }
        return if (k == 1) Archer3D.RAIN_DUR * Archer3D.RAIN_REL else Archer3D.THUNDER_DUR * Archer3D.THUNDER_REL
    }

    fun jump(): Boolean {
        if (!alive || rolling || (skillK != 0 && classe != Classe.NINJA)) return false
        if (!grounded) return airJump()
        vz = 340f
        alt = ground + 0.7f
        airJumped = false
        flipT = 0f
        return true
    }

    /** Segundo pulo do arqueiro, no ar: impulso novo e mortal para a frente que termina de pé antes de pousar. */
    private fun airJump(): Boolean {
        if (classe == Classe.NINJA) return ninjaDash()
        if (classe != Classe.ARQUEIRO || airJumped || flying) return false
        val up = (alt - ground).coerceAtLeast(0f)
        if (up < 5f && vz > 0f) return false // acabou de decolar: ignora o toque duplicado
        airJumped = true
        vz = 520f
        // tempo até o chão com a gravidade de 1500: o mortal dura um pouco menos, para fechar a pose antes do pouso
        val land = (vz + sqrt(vz * vz + 3000f * up)) / 1500f
        flipDur = (land * 0.88f).coerceIn(0.45f, 1.05f)
        flipT = flipDur
        attackAnim = 0f
        charging = false
        chargeT = 0f
        return true
    }

    /**
     * Pulo-dash do ninja (2º toque no pulo, no ar): andando, dispara veloz e suave para onde está indo;
     * parado, salta reto para cima.
     */
    private fun ninjaDash(): Boolean {
        if (airJumped || !alive) return false
        val up = (alt - ground).coerceAtLeast(0f)
        if (up < 5f && vz > 0f) return false // acabou de decolar: ignora o toque duplicado
        airJumped = true
        attackAnim = 0f
        charging = false
        chargeT = 0f
        val l = hypot(inX, inY)
        if (l > 0.1f) {
            dashDX = inX / l
            dashDY = inY / l
            facingX = dashDX
            facingY = dashDY
            dashUp = false
            dashDur = NINJA_DASH_T
            vz = 150f
            phaseT = NINJA_DASH_T * 0.85f
        } else {
            dashDX = 0f
            dashDY = 0f
            dashUp = true
            dashDur = NINJA_UP_T
            vz = 650f
            phaseT = 0.12f
        }
        dashT = dashDur
        return true
    }

    fun startRoll(now: Long): Boolean {
        if (!alive || rolling || swinging || skillK != 0 || voltT > 0f || !grounded || now - lastRoll < (if (classe == Classe.PIKACHU) 900L else 700L)) return false
        var dx = inX
        var dy = inY
        if (hypot(dx, dy) < 0.1f) {
            dx = facingX
            dy = facingY
        }
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        rollDX = dx / l
        rollDY = dy / l
        facingX = rollDX
        facingY = rollDY
        rollT = if (classe == Classe.PIKACHU) PIKA_QUICK_T else if (classe == Classe.LUFFY) LUFFY_DASH_T else ROLL_TIME
        rollDur = rollT
        dashing = false
        lastRoll = now
        attackAnim = 0f
        charging = false
        chargeT = 0f
        return true
    }

    /** Impulso do bárbaro: arranca com a força das pernas na direção da mira. */
    fun startDash(now: Long, dx: Float, dy: Float): Boolean {
        if (!alive || rolling || !grounded || leaping || leapCharging || now - lastRoll < 700L) return false
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        rollDX = dx / l
        rollDY = dy / l
        facingX = rollDX
        facingY = rollDY
        rollT = DASH_TIME
        rollDur = DASH_TIME
        dashing = true
        lastRoll = now
        attackAnim = 0f
        charging = false
        chargeT = 0f
        return true
    }

    /** Começa a carregar o salto (botão de pular segurado, no chão). */
    fun startLeapCharge(): Boolean {
        if (!alive || classe != Classe.BARBARO || rolling || !grounded || leaping || leapCharging) return false
        leapCharging = true
        leapChargeT = 0f
        attackAnim = 0f
        return true
    }

    /** Solta o salto: andando, vai longe na direção do movimento; parado, sobe alto. Devolve true se saltou. */
    fun releaseLeap(): Boolean {
        if (!leapCharging) return false
        leapCharging = false
        val c = 0.4f + 0.6f * leapChargeT
        leapChargeT = 0f
        if (!alive || !grounded) return false
        val l = hypot(inX, inY)
        if (l > 0.1f) {
            leapDX = inX / l
            leapDY = inY / l
            leapSpd = 250f + 260f * c
            vz = 300f + 140f * c
        } else {
            leapDX = 0f
            leapDY = 0f
            leapSpd = 0f
            vz = 400f + 240f * c
        }
        alt = ground + 0.7f
        leaping = true
        leapAir = false
        leapClock = 0f
        return true
    }

    fun canTeleport(now: Long): Boolean =
        classe == Classe.MAGO && alive && now - lastTp >= 900L && mp >= TP_COST && !flyingBusy()

    private fun flyingBusy(): Boolean = false

    /** Gravidade e acompanhamento do relevo. */
    fun updateVertical(dt: Float, groundPx: Float) {
        ground = groundPx
        if (swinging) {
            // pendurado: a altura vem do pêndulo (swingStep); aqui só termina o balanço se o chão subir até os pés
            if (alt <= groundPx + 2.5f && swVZ <= 0f) endSwing()
            return
        }
        if (sky) {
            // andando no céu: sobe suave até a plataforma de reiatsu e fica flutuando
            flyClock += dt
            val target = groundPx + ICHI_SKY_H + sin(flyClock * 2.2f) * 2f
            alt += (target - alt) * min(1f, dt * 3.0f)
            vz = 0f
            return
        }
        if (flying) {
            // voo: flutua acima do chão com leve balanço
            flyClock += dt
            val target = groundPx + FLY_H + sin(flyClock * 2.6f) * 3f
            alt += (target - alt) * min(1f, dt * 3.5f)
            vz = 0f
            return
        }
        if (alt > groundPx + 0.01f || vz > 0f) {
            vz -= (if (dashT > 0f && !dashUp) 380f else 1500f) * dt
            if (glide && vz < -170f) vz = -170f // desce flutuando depois do voo
            alt += vz * dt
            if (alt <= groundPx) {
                glide = false
                if (vz < -210f) {
                    landed = (-vz) / 400f
                    landSquash = ((-vz) / 380f).coerceIn(0.15f, 0.55f)
                }
                alt = groundPx
                vz = 0f
                airJumped = false
                flipT = 0f
                dashT = 0f
            }
        } else {
            airJumped = false
            alt += (groundPx - alt) * min(1f, dt * 30f)
            if (abs(groundPx - alt) < 0.3f) alt = groundPx
            vz = 0f
        }
    }

    override fun update(dt: Float) {
        if (!alive) swinging = false
        if (swinging) swingStep(dt)
        if (flipT > 0f) flipT = max(0f, flipT - dt)
        if (phaseT > 0f) phaseT = max(0f, phaseT - dt)
        if (dashT > 0f) dashT = max(0f, dashT - dt)
        if (voltT > 0f) voltT = max(0f, voltT - dt)
        if (ichiT > 0f) ichiT = max(0f, ichiT - dt)
        if (classe == Classe.ICHIGO) {
            if (hollow) {
                hollowT -= dt
                if (hollowT <= 0f) { hollow = false; hollowT = 0f }
            }
            if (endHollow && skillK == 0) { endHollow = false; hollow = false; hollowT = 0f }
        }
        if (leaping) {
            leapClock += dt
            if (leapClock > 2.5f) leaping = false
        }
        if (rollT > 0f) {
            rollT -= dt
            if (rollT <= 0f) { rollT = 0f; dashing = false; hopping = false; landSquash = max(landSquash, 0.3f) }
            val q = 1f - rollT / rollDur
            if (hopping && !trapThrown && q >= Archer3D.HOP_THROW) {
                trapThrown = true
                trapThrowReq = true
                trapRelX = x
                trapRelY = y
                trapRelUp = (alt - ground).coerceAtLeast(0f)
            }
            if (classe == Classe.LUFFY) {
                // estilingue: segura as mãos no chão (leve recuo) e depois dispara, freando no fim
                val w = Luffy3D.DASH_WIND
                val spdL = if (q < w) -60f * (1f - q / w) else {
                    val v = ((q - w) / (1f - w)).coerceIn(0f, 1f)
                    LUFFY_DASH_V * Math.pow((1f - v).toDouble(), 1.2).toFloat()
                }
                vx = rollDX * spdL
                vy = rollDY * spdL
            } else {
            val spd = if (hopping) 440f * (1.25f - 0.9f * q) else if (dashing) 640f * (1.3f - 0.95f * q) else if (classe == Classe.PIKACHU) 640f * (1.35f - 0.95f * q) else 300f * (1.4f - 0.75f * q)
            vx = rollDX * spd
            vy = rollDY * spd
            }
        } else if (swinging) {
            // balanço: o corpo vai até a posição calculada pelo pêndulo (o jogo ainda pode barrar na parede)
            val inv = 1f / max(dt, 0.0001f)
            vx = (swAX + swOX - x) * inv
            vy = (swAY + swOY - y) * inv
        } else if (ichiT > 0f) {
            // Shunpo / Sonído: avanço veloz que freia no fim
            val q = 1f - ichiT / ichiDurNow
            val spd = ichiSpdNow * (1.1f - 0.6f * q)
            vx = ichiDX * spd
            vy = ichiDY * spd
        } else if (voltT > 0f) {
            // Investida Trovão: avança em linha reta
            vx = voltDX * PIKA_VOLT_SPD
            vy = voltDY * PIKA_VOLT_SPD
        } else if (dashT > 0f && !dashUp) {
            // pulo-dash: acelera e freia suave (meio seno), deslizando pelo ar
            val q = 1f - dashT / dashDur
            val spd = NINJA_DASH_DIST * (PI_F / (2f * NINJA_DASH_T)) * sin(q * PI_F)
            vx = dashDX * spd
            vy = dashDY * spd
        } else if (leaping) {
            vx = leapDX * leapSpd
            vy = leapDY * leapSpd
        } else {
            if (leapCharging) leapChargeT = (leapChargeT + dt / 0.7f).coerceAtMost(1f)
            val slow = (if (charging) (if (weapon == 1) 0.28f else 0.45f) else 1f) * (if (flying) 1.25f else if (sky) 1.15f else 1f) * (if (leapCharging) 0.3f else 1f) * (if (skillK != 0) (if (classe == Classe.NINJA) 0.9f else if (classe == Classe.PIKACHU) 0.45f else 0.3f) else 1f) * (if (bowHold) 0.5f else 1f) * (if (classe == Classe.ICHIGO && hollow) 1.12f else 1f)
            vx = inX * velH * 60f * slow * spdMul + momX
            vy = inY * velH * 60f * slow * spdMul + momY
            val md = max(0f, 1f - dt * 2.0f)
            momX *= md
            momY *= md
        }
        x += vx * dt
        y += vy * dt
        val sp = hypot(vx, vy)
        if (sp > 1f) {
            // arqueiro com o arco puxado: andar de costas faz a passada ao contrário (não desliza)
            var dirSign = 1f
            if (classe == Classe.ARQUEIRO && bowHold && vx * facingX + vy * facingY < -0.2f * sp) dirSign = -1f
            walkPhase += dirSign * sp * dt * 0.05f
        }
        hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
        if (attackAnim > 0f) {
            attackAnim = (attackAnim - dt * (if (classe == Classe.GUERREIRO || classe == Classe.MAGO || classe == Classe.BARBARO || classe == Classe.ARQUEIRO || classe == Classe.NINJA || classe == Classe.PIKACHU || classe == Classe.ICHIGO || classe == Classe.LUFFY || classe == Classe.EXPLORADOR) attackRate else 2.8f)).coerceAtLeast(0f)
        }
        // arco puxado: congela a animação logo antes do disparo até o botão ser solto
        if (bowHold && classe == Classe.ARQUEIRO && attackAnim < 1f - BOW_HOLD_AT) attackAnim = 1f - BOW_HOLD_AT
        if (tpT > 0f) tpT = (tpT - dt).coerceAtLeast(0f)
        if (skillK != 0) {
            skillT -= dt
            if (skillT <= 0f) { skillT = 0f; skillK = 0 }
        }
        if (classe == Classe.MAGO) {
            if (flying) {
                fuel -= dt
                if (fuel <= 0f) {
                    fuel = 0f
                    flying = false
                    glide = true
                }
            } else if (grounded) {
                fuel = (fuel + dt * 0.9f).coerceAtMost(FLY_MAX)
            }
        }
        landSquash = (landSquash - dt * 4.8f).coerceAtLeast(0f)
        attackPunch = (attackPunch - dt * 5.5f).coerceAtLeast(0f)
        if (!alive) sky = false
        if (classe == Classe.ICHIGO && sky) {
            mp -= ICHI_SKY_COST * dt
            if (mp <= 0f) {
                mp = 0f
                sky = false
                glide = true
            }
        } else if (maxMpH > 0f && mp < maxMpH) {
            mp = (mp + regenMpH * dt).coerceAtMost(maxMpH)
        }
        if (regenHpH > 0f && alive && hp < maxHp) {
            hp = (hp + regenHpH * dt).coerceAtMost(maxHp)
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        val sx = x - camX
        val sy = y - camY
        // Squash horizontal + stretch vertical no push do ataque / aterrissagem
        val up = (alt - ground).coerceAtLeast(0f)
        val airStretch = if (up > 1f) (vz / 340f).coerceIn(-1f, 1f) * 0.07f else 0f
        val tpU = if (classe == Classe.MAGO && tpT > 0f) 1f - tpT / TP_TIME else 1f
        val tpE = tpU * tpU * (3f - 2f * tpU)
        val squashX = (1f + landSquash * 0.22f - attackPunch * 0.08f - airStretch * 0.6f) * (0.35f + 0.65f * tpE)
        val squashY = (1f - landSquash * 0.18f + attackPunch * 0.12f + airStretch) * (1.6f - 0.6f * tpE)
        val s = (radius / 24f) * ((squashX + squashY) * 0.5f)
        val gy = sy + radius * 0.55f - ground
        val feet = sy + radius * 0.55f - alt
        val k = (1f - up / 90f).coerceIn(0.4f, 1f)
        val moving = hypot(vx, vy) > 1f
        // Offset de “punch” na direção do golpe
        val punchOff = attackPunch * 10f
        val drawX = sx + facingX * punchOff
        val drawY = feet + facingY * punchOff * 0.35f

        // Sombra no chão (encolhe quando pula)
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - radius * 1.05f * k, gy - radius * 0.32f * k, sx + radius * 1.05f * k, gy + radius * 0.38f * k, paint)
        paint.color = 0x44000000
        canvas.drawOval(sx - radius * 0.8f * k, gy - radius * 0.22f * k, sx + radius * 0.8f * k, gy + radius * 0.28f * k, paint)

        // Anel da classe no chão
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = (classe.cor and 0x00FFFFFF) or 0x99000000.toInt()
        canvas.drawOval(sx - radius * 1.1f * k, gy - radius * 0.36f * k, sx + radius * 1.1f * k, gy + radius * 0.42f * k, paint)
        paint.style = Paint.Style.FILL

        if (classe == Classe.ICHIGO && sky) {
            // plataforma de reiatsu sob os pés: disco rubro-negro com brilho, anéis, glifos girando e fagulhas subindo
            val pr = radius * 1.45f
            val t = flyClock
            val pulse = 0.5f + 0.5f * sin(t * 4.5f)
            val fade = ((alt - ground) / ICHI_SKY_H).coerceIn(0f, 1f)
            val red = 0xFFC8102E.toInt()
            val redL = 0xFFFF4B5C.toInt()
            val dark = 0xFF1A0610.toInt()
            paint.style = Paint.Style.FILL
            paint.color = (0x55 * fade).toInt() shl 24 or 0x1A0610
            canvas.drawOval(sx - pr * 1.3f, feet - pr * 0.45f, sx + pr * 1.3f, feet + pr * 0.55f, paint)
            Glow.glow(canvas, paint, sx, feet, pr * 1.5f, pr * 0.62f, red, (0.30f + 0.15f * pulse) * fade)
            Glow.glow(canvas, paint, sx, feet, pr * 0.9f, pr * 0.36f, redL, 0.35f * fade)
            Glow.ring(canvas, paint, sx, feet, pr, pr * 0.42f, 2.6f, red, redL, 0.9f * fade)
            Glow.ring(canvas, paint, sx, feet, pr * 1.25f * (1f + 0.04f * pulse), pr * 0.52f * (1f + 0.04f * pulse), 1.6f, dark, red, 0.55f * fade)
            // glifos: arcos curtos girando em sentidos opostos
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 2f
            paint.color = (0xCC * fade).toInt() shl 24 or 0xFFD0D6
            skyOval.set(sx - pr * 0.74f, feet - pr * 0.31f, sx + pr * 0.74f, feet + pr * 0.31f)
            for (i in 0..5) canvas.drawArc(skyOval, t * 70f + i * 60f, 26f, false, paint)
            paint.strokeWidth = 1.4f
            paint.color = (0x99 * fade).toInt() shl 24 or 0xFF4B5C
            skyOval.set(sx - pr * 0.46f, feet - pr * 0.19f, sx + pr * 0.46f, feet + pr * 0.19f)
            for (i in 0..3) canvas.drawArc(skyOval, -t * 95f + i * 90f, 40f, false, paint)
            paint.style = Paint.Style.FILL
            // fagulhas subindo da borda da plataforma
            for (i in 0..7) {
                val ph = (t * 0.9f + i * 0.125f) % 1f
                val ang = i * 0.785f + t * 0.6f
                val ex = sx + cos(ang) * pr * 0.9f
                val ey = feet + sin(ang) * pr * 0.38f - ph * radius * 1.6f
                Glow.ember(canvas, paint, ex, ey, 0.8f + 2.2f * (1f - ph), if (i % 2 == 0) redL else 0xFFFFFFFF.toInt(), (1f - ph) * fade)
            }
            paint.style = Paint.Style.FILL
        }

        VoxelArt.flash = hitFlash
        Warrior3D.rollP = if (rollT > 0f) 1f - rollT / ROLL_TIME else -1f
        Warrior3D.inAir = up > 1f
        Warrior3D.airH = up
        Warrior3D.airVz = vz
        Warrior3D.swing = swing
        Warrior3D.weapon = weapon
        Warrior3D.spearGone = weapon == 1 && spearOut
        Warrior3D.charge = if (charging) chargeT else -1f
        Barbarian3D.rollP = if (rollT > 0f) 1f - rollT / rollDur else -1f
        Barbarian3D.leapCharge = if (leapCharging) leapChargeT else -1f
        Barbarian3D.leaping = leaping && leapAir
        Barbarian3D.inAir = up > 1f
        Barbarian3D.airH = up
        Barbarian3D.airVz = vz
        Barbarian3D.swing = swing
        Archer3D.hopP = if (hopping && rollT > 0f) 1f - rollT / rollDur else -1f
        Archer3D.flipP = if (classe == Classe.ARQUEIRO && flipT > 0f) 1f - flipT / flipDur else -1f
        Archer3D.inAir = up > 1f
        Archer3D.airH = up
        Archer3D.airVz = vz
        Archer3D.skillK = skillK
        Archer3D.skillP = if (skillK != 0) 1f - skillT / skillDur else -1f
        Archer3D.holdOn = bowHold && classe == Classe.ARQUEIRO
        Archer3D.holdPow = bowPow
        if (trapFx) {
            trapFx = false
            Archer3D.trapReq = true
        }
        Archer3D.alphaV = 1f
        Ninja3D.rollP = if (rollT > 0f) 1f - rollT / ROLL_TIME else -1f
        Ninja3D.inAir = up > 1f
        Ninja3D.airH = up
        Ninja3D.airVz = vz
        Ninja3D.swing = swing
        Ninja3D.dashP = if (classe == Classe.NINJA && dashT > 0f) 1f - dashT / dashDur else -1f
        Ninja3D.dashUp = dashUp
        Ninja3D.skillK = if (classe == Classe.NINJA) skillK else 0
        Ninja3D.skillP = if (classe == Classe.NINJA && skillK != 0) 1f - skillT / skillDur else -1f
        Ninja3D.alphaV = 1f
        Pikachu3D.rollP = if (classe == Classe.PIKACHU && rollT > 0f) 1f - rollT / rollDur else -1f
        Pikachu3D.inAir = up > 1f
        Pikachu3D.airH = up
        Pikachu3D.airVz = vz
        Pikachu3D.skillK = if (classe == Classe.PIKACHU) skillK else 0
        Pikachu3D.skillP = if (classe == Classe.PIKACHU && skillK != 0) 1f - skillT / skillDur else -1f
        Pikachu3D.alphaV = 1f
        Ichigo3D.rollP = if (classe == Classe.ICHIGO && ichiT > 0f && ichiKind == 1) 1f - ichiT / ichiDurNow else -1f
        Ichigo3D.inAir = up > 1f
        Ichigo3D.airH = up
        Ichigo3D.airVz = vz
        Ichigo3D.swing = swing
        Ichigo3D.skillK = if (classe == Classe.ICHIGO) skillK else 0
        Ichigo3D.skillP = if (classe == Classe.ICHIGO && skillK != 0) 1f - skillT / skillDur else -1f
        Ichigo3D.skillHol = ichiSkH
        Ichigo3D.hollowOn = classe == Classe.ICHIGO && hollow
        Ichigo3D.sky = classe == Classe.ICHIGO && sky
        Ichigo3D.alphaV = 1f
        Luffy3D.rollP = if (classe == Classe.LUFFY && rollT > 0f) 1f - rollT / rollDur else -1f
        Luffy3D.inAir = up > 1f
        Luffy3D.airH = up
        Luffy3D.airVz = vz
        Luffy3D.swing = swing
        Luffy3D.alphaV = 1f
        Explorer3D.rollP = if (classe == Classe.EXPLORADOR && rollT > 0f) 1f - rollT / rollDur else -1f
        Explorer3D.inAir = up > 1f
        Explorer3D.airH = up
        Explorer3D.airVz = vz
        Explorer3D.swing = swing
        Explorer3D.alphaV = 1f
        Explorer3D.hangOn = classe == Classe.EXPLORADOR && swinging
        Explorer3D.hangGrow = swGrow
        Explorer3D.hangDX = swAX - x
        Explorer3D.hangDY = (swAY - y) + (alt - swAAlt)
        Explorer3D.hangVF = ((swVX * facingX + swVY * facingY) / 500f).coerceIn(-1f, 1f)
        Explorer3D.hangVS = ((swVX * facingY - swVY * facingX) / 500f).coerceIn(-1f, 1f)
        Mage3D.rollP = if (rollT > 0f) 1f - rollT / ROLL_TIME else -1f
        Mage3D.inAir = up > 1f
        Mage3D.airH = up
        Mage3D.airVz = vz
        Mage3D.flying = flying
        Mage3D.tpP = if (classe == Classe.MAGO && tpT > 0f) tpU else -1f
        Mage3D.tpOffX = tpFromX - x
        Mage3D.tpOffY = tpFromY - y
        Mage3D.alphaV = if (classe == Classe.MAGO && tpT > 0f) 0.12f + 0.88f * min(1f, tpU * 1.7f) else 1f
        canvas.save()
        canvas.scale(squashX, squashY, drawX, drawY)
        Sprites.drawHero(canvas, paint, classe, drawX, drawY, s / ((squashX + squashY) * 0.5f), walkPhase, moving, facingX, facingY, attackAnim)
        canvas.restore()
        VoxelArt.flash = 0f

        if (!classe.isRanged && classe != Classe.GUERREIRO && classe != Classe.BARBARO && classe != Classe.NINJA && classe != Classe.ICHIGO && classe != Classe.LUFFY && classe != Classe.EXPLORADOR) {
            Sprites.drawSlash(canvas, paint, sx, feet - 16f * s, facingX, facingY, attackAnim, classe.alcance, classe.cor)
        }

        drawBar(canvas, paint, sx, feet - (if (classe == Classe.MAGO) 80f else if (classe == Classe.BARBARO) 78f else if (classe == Classe.PIKACHU) 56f else if (classe == Classe.ICHIGO) (if (hollow) 76f else 70f) else if (classe == Classe.LUFFY) 72f else if (classe == Classe.EXPLORADOR) 74f else 68f) * s, radius * 2.2f, hp / maxHp, 0xFF4CAF50.toInt())
        if (classe == Classe.MAGO && (flying || fuel < FLY_MAX - 0.02f)) {
            drawBar(canvas, paint, sx, feet - 80f * s + 8f, radius * 2.2f, fuel / FLY_MAX, 0xFF5FD4FF.toInt())
        }
    }
}

class Enemy(
    x: Float,
    y: Float,
    val tipo: Int = 0
) : Entity(x, y, if (tipo == 2) 30f else 20f, 50f + tipo * 25f, 50f + tipo * 25f) {

    var speed = 1.6f + tipo * 0.3f
    var damage = 8f + tipo * 4f
    var lastHit = 0L
    var hitCooldown = 800L
    var faceX = 0f
    var faceY = 1f
    var attackAnim = 0f
    var alertFx = false  // o GameView consome: som de "viu o jogador"
    var swingFx = false  // o GameView consome: som do golpe do inimigo
    var stunT = 0f   // atordoado (armadilha / raio): fica parado
    var slowT = 0f   // lento (corte sombrio do ninja)
    var yaw = 0f
    private var yawInit = false

    // mundo aberto: fica parado/vagando até o jogador chegar perto
    val homeX = x
    val homeY = y
    var alerted = false
    private var wanderT = 0f
    private var wanderDX = 0f
    private var wanderDY = 0f
    private val aggro: Float get() = if (tipo == 2) 620f else 440f

    fun palette(): Int = when (tipo) {
        0 -> 0xFFE8E4D4.toInt()
        1 -> 0xFFB0BEC5.toInt()
        else -> 0xFFB388FF.toInt()
    }

    fun update(dt: Float, player: Player) {
        hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
        if (attackAnim > 0f) attackAnim = (attackAnim - dt * 2.8f).coerceAtLeast(0f)
        if (!alive) return
        if (slowT > 0f) slowT = (slowT - dt).coerceAtLeast(0f)
        if (stunT > 0f) {
            stunT = (stunT - dt).coerceAtLeast(0f)
            return
        }
        val dx = player.x - x
        val dy = player.y - y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (player.alive) {
            if (alerted) { if (dist > aggro * 1.9f) alerted = false }
            else if (dist < aggro) { alerted = true; alertFx = true }
        } else alerted = false

        var mx = 0f
        var my = 0f
        var sp = 0f
        if (alerted) {
            if (dist > 1f) { mx = dx / dist; my = dy / dist; sp = speed }
        } else {
            wanderT -= dt
            if (wanderT <= 0f) {
                wanderT = 1.5f + kotlin.random.Random.nextFloat() * 2.5f
                if (kotlin.random.Random.nextFloat() < 0.45f) { wanderDX = 0f; wanderDY = 0f }
                else {
                    val a = kotlin.random.Random.nextFloat() * 6.2832f
                    wanderDX = kotlin.math.cos(a); wanderDY = sin(a)
                }
            }
            val hx = homeX - x
            val hy = homeY - y
            val hd = kotlin.math.sqrt(hx * hx + hy * hy)
            if (hd > 160f) { wanderDX = hx / hd; wanderDY = hy / hd }
            mx = wanderDX; my = wanderDY
            sp = speed * 0.35f
        }
        if (sp > 0f && (mx != 0f || my != 0f)) {
            faceX = mx
            faceY = my
            val slowM = if (slowT > 0f) 0.45f else 1f
            x += mx * sp * dt * 60f * slowM
            y += my * sp * dt * 60f * slowM
            walkPhase += sp * dt * 4.2f * slowM
        }
        // giro suave
        val target = atan2(faceX, faceY)
        if (!yawInit) {
            yaw = target
            yawInit = true
        } else {
            val pi = Math.PI.toFloat()
            var d = target - yaw
            while (d > pi) d -= 2f * pi
            while (d < -pi) d += 2f * pi
            yaw += d * min(1f, dt * 10f)
        }
        val now = System.currentTimeMillis()
        if (alerted && player.alive && attackAnim <= 0f && dist < radius + player.radius + 22f && now - lastHit > hitCooldown - 250L) {
            attackAnim = 1f
            swingFx = true
        }
    }

    override fun update(dt: Float) {}

    override fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY
        val s = if (tipo == 2) 1.55f else 1f
        val feet = sy + radius * 0.55f - alt

        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - radius * 1.1f, feet - radius * 0.3f, sx + radius * 1.1f, feet + radius * 0.36f, paint)
        paint.color = 0x44000000
        canvas.drawOval(sx - radius * 0.8f, feet - radius * 0.2f, sx + radius * 0.8f, feet + radius * 0.26f, paint)

        VoxelArt.flash = hitFlash
        Warrior3D.drawSkeleton(canvas, paint, sx, feet, s, walkPhase, yaw, tipo, attackAnim)
        VoxelArt.flash = 0f

        val barW = if (tipo == 2) radius * 2.4f else radius * 2f
        drawBar(canvas, paint, sx, feet - 50f * s - 8f, barW, hp / maxHp, 0xFFE53935.toInt())
        if (stunT > 0f) drawStunStars(canvas, paint, sx, feet - 50f * s - 20f, s, VoxelArt.time)
    }
}

class Projectile(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float,
    val color: Int,
    val fromPlayer: Boolean = true,
    val kind: Int = 0 // 0 = orbe mágico, 1 = flecha
) {
    var alive = true
    val radius = 8f
    var life = 2.5f

    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        life -= dt
        if (life <= 0f) alive = false
    }

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        if (!alive) return
        val sx = x - camX
        val sy = y - camY - 14f
        paint.style = Paint.Style.FILL
        // sombra no chão
        paint.color = 0x33000000
        canvas.drawOval(sx - 8f, y - camY + 8f, sx + 8f, y - camY + 14f, paint)

        if (kind == 1) {
            val ang = kotlin.math.atan2(vy, vx) * 57.29578f
            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(ang)
            paint.color = 0xFF8D6E63.toInt()
            canvas.drawRect(-14f, -1.5f, 8f, 1.5f, paint)
            paint.color = 0xFFECEFF1.toInt()
            canvas.drawRect(-14f, -3.5f, -9f, 3.5f, paint)
            paint.color = 0xFFB0BEC5.toInt()
            canvas.drawRect(8f, -3f, 15f, 3f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawRect(11f, -1.5f, 17f, 1.5f, paint)
            canvas.restore()
        } else {
            val pulse = 1f + 0.12f * sin(VoxelArt.time * 18f)
            for (i in 1..4) {
                paint.color = VoxelArt.alpha(color, 0.30f - i * 0.06f)
                canvas.drawCircle(sx - vx * 0.016f * i, sy - vy * 0.016f * i, radius * (1.15f - i * 0.17f), paint)
            }
            paint.color = VoxelArt.alpha(color, 0.18f)
            canvas.drawCircle(sx, sy, radius * 2.4f * pulse, paint)
            paint.color = VoxelArt.alpha(color, 0.45f)
            canvas.drawCircle(sx, sy, radius * 1.5f * pulse, paint)
            paint.color = color
            canvas.drawCircle(sx, sy, radius, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(sx, sy, radius * 0.45f, paint)
        }
    }
}

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int, val size: Float) {
    val maxLife = life

    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        vy += 520f * dt
        vx *= 0.98f
        life -= dt
    }

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float) {
        paint.style = Paint.Style.FILL
        paint.color = VoxelArt.alpha(color, life / maxLife)
        val sx = x - camX
        val sy = y - camY
        canvas.drawRect(sx - size / 2f, sy - size / 2f, sx + size / 2f, sy + size / 2f, paint)
    }
}

class DmgText(var x: Float, var y: Float, val text: String, var life: Float, val color: Int)


/**
 * Lança arremessada: sai da mão do herói com o mesmo tamanho da lança que ele segura, acelera suave,
 * atravessa inimigos e fica fincada na parede (se bateu nela) ou no chão (se acabou o alcance).
 * [sc] = escala do sprite, calculada para ter o mesmo tamanho da lança 3D da mão.
 */
class ThrownSpear(
    var x: Float, var y: Float, val dx: Float, val dy: Float,
    val range: Float, val damage: Float, val sc: Float
) {
    var flying = true
    var travelled = 0f
    var age = 0f
    var stuckT = 0f
    var wobble = 0f
    var wall = false
    val hit = HashSet<Enemy>()

    fun update(dt: Float) {
        age += dt
        if (flying) {
            // sai da mão devagar e acelera até a velocidade máxima (sem tranco)
            val u = (age / 0.16f).coerceIn(0f, 1f)
            val speed = 360f + 500f * (u * u * (3f - 2f * u))
            val step = speed * min(dt, 0.04f)
            x += dx * step
            y += dy * step
            travelled += step
            if (travelled >= range) land(false)
        } else {
            stuckT += dt
            wobble = (wobble - dt * 2.4f).coerceAtLeast(0f)
        }
    }

    fun land(intoWall: Boolean) {
        flying = false
        wall = intoWall
        wobble = 1f
    }

    // desenha com a ponta na origem e o corpo para trás (-x); o comprimento total (94) = lança de 56,7 unidades do herói
    private fun shape(canvas: Canvas, paint: Paint) {
        canvas.translate(-14f, 0f)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF6D4C2F.toInt()
        canvas.drawRect(-76f, -2f, -6f, 2f, paint)
        paint.color = 0xFF9C6B3F.toInt()
        canvas.drawRect(-76f, -2f, -6f, -0.5f, paint)
        paint.color = 0xFF3E2A18.toInt()
        for (i in 0..3) canvas.drawRect(-70f + i * 14f, -2.3f, -69f + i * 14f, 2.3f, paint)
        paint.color = 0xFFF2C94C.toInt()
        canvas.drawRect(-80f, -3f, -76f, 3f, paint)
        canvas.drawRect(-14f, -3.2f, -9f, 3.2f, paint)
        // flâmula vermelha
        val fl = sin(age * 22f) * 1.6f * (if (flying) 1f else wobble)
        paint.color = 0xFFD83A4A.toInt()
        val pn = android.graphics.Path()
        pn.moveTo(-16f, 0f); pn.lineTo(-34f, -8f + fl); pn.lineTo(-30f, -3f); pn.lineTo(-40f, -3f + fl); pn.lineTo(-16f, 1.5f)
        pn.close()
        canvas.drawPath(pn, paint)
        // lâmina
        paint.color = 0xFFB9C3CC.toInt()
        val bl = android.graphics.Path()
        bl.moveTo(-8f, -3f); bl.lineTo(-2f, -5.5f); bl.lineTo(14f, 0f); bl.lineTo(-2f, 5.5f); bl.lineTo(-8f, 3f)
        bl.close()
        canvas.drawPath(bl, paint)
        paint.color = 0xFFF4F8FB.toInt()
        canvas.drawRect(-6f, -1f, 10f, 0.4f, paint)
        paint.color = 0xFF7C8A96.toInt()
        canvas.drawRect(-8f, 1.4f, 4f, 3f, paint)
    }

    private fun shortest(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return from + d
    }

    fun draw(canvas: Canvas, paint: Paint, camX: Float, camY: Float, time: Float) {
        val sx = x - camX
        val gy = y - camY
        paint.style = Paint.Style.FILL
        // direção na tela (o mapa é visto de cima inclinado: o eixo y encurta)
        val vx = dx
        val vy = dy * 0.717f
        val fl = hypot(vx, vy).coerceAtLeast(0.001f)
        val flightAng = atan2(vy, vx) * 57.29578f
        if (flying) {
            // sombra no chão + rastro
            paint.color = 0x33000000
            canvas.drawOval(sx - 20f * sc / 0.8f, gy - 3f, sx + 20f * sc / 0.8f, gy + 4f, paint)
            val ux = vx / fl
            val uy = vy / fl
            for (i in 1..6) {
                paint.color = ((0.30f * (1f - i / 7f) * 255f).toInt() shl 24) or 0x00FFF2C0
                paint.strokeWidth = 5f - i * 0.6f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(sx - ux * (i * 14f), gy - 22f - uy * (i * 14f), sx - ux * ((i + 1) * 14f), gy - 22f - uy * ((i + 1) * 14f), paint)
            }
            paint.style = Paint.Style.FILL
            canvas.save()
            canvas.translate(sx, gy - 22f)
            canvas.rotate(flightAng)
            canvas.scale(sc * fl, sc)
            shape(canvas, paint)
            canvas.restore()
        } else {
            // brilho no chão chamando o herói
            val pul = 0.5f + 0.5f * sin(time * 4.5f)
            val r = 26f + 6f * pul
            paint.color = ((0.16f + 0.14f * pul) * 255f).toInt() shl 24 or 0x00FFD54F
            canvas.drawOval(sx - r, gy - r * 0.55f, sx + r, gy + r * 0.55f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = ((0.55f + 0.3f * pul) * 255f).toInt() shl 24 or 0x00FFE08A
            canvas.drawOval(sx - r, gy - r * 0.55f, sx + r, gy + r * 0.55f, paint)
            paint.style = Paint.Style.FILL
            if (wall) {
                // fincada na parede: continua na altura do voo, mesma direção, ponta dentro da parede e vibrando
                val tx = sx + dx * 9f
                val ty = gy - 22f + dy * 7f
                val shake = sin(stuckT * 46f) * wobble * 3.2f
                canvas.save()
                canvas.translate(tx, ty)
                canvas.rotate(flightAng + shake)
                canvas.scale(sc * fl, sc)
                shape(canvas, paint)
                canvas.restore()
            } else {
                // caiu no chão: a ponta desce da altura do voo e a lança tomba e finca inclinada
                val lean = if (dx >= 0f) 1f else -1f
                val groundAng = atan2(1f, 0.32f * lean) * 57.29578f
                val k0 = (stuckT / 0.18f).coerceIn(0f, 1f)
                val k = k0 * k0 * (3f - 2f * k0)
                val ang = flightAng + (shortest(flightAng, groundAng) - flightAng) * k +
                    sin(stuckT * 38f) * wobble * 7f * lean
                val oy = -22f * (1f - k) + 3f * k
                val stretch = fl * (1f - k) + 0.85f * k
                paint.color = 0x44000000
                canvas.drawOval(sx - 14f, gy - 3f, sx + 14f, gy + 5f, paint)
                canvas.save()
                canvas.translate(sx, gy + oy)
                canvas.rotate(ang)
                canvas.scale(sc * stretch, sc)
                shape(canvas, paint)
                canvas.restore()
            }
            // setinha pulando acima
            val by = gy - 96f + sin(time * 5f) * 4f
            paint.color = 0xFFFFD54F.toInt()
            val ar = android.graphics.Path()
            ar.moveTo(sx - 8f, by - 8f); ar.lineTo(sx + 8f, by - 8f); ar.lineTo(sx, by + 4f)
            ar.close()
            canvas.drawPath(ar, paint)
        }
    }
}
