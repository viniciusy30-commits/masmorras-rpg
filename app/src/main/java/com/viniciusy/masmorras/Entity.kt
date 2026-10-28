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

    fun takeDamage(dmg: Float) {
        if (!alive) return
        hp -= dmg
        hitFlash = 1f
        if (hp <= 0f) {
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

    fun canAttack(now: Long): Boolean {
        if (rolling || skillK != 0 || now < swapUntil || leapCharging || leaping || flipT > 0f || (dashT > 0f && !dashUp)) return false
        val cd = if (classe == Classe.GUERREIRO) (if (comboStep == 2 || comboStep == -1 && swing == 3) 680L else 380L)
        else if (classe == Classe.BARBARO) (if (comboStep == 2) 720L else 440L)
        else if (classe == Classe.NINJA) (if (comboStep == 2) 520L else 300L)
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
        if (classe == Classe.MAGO) attackRate = MAGE_RATE
        if (classe == Classe.ARQUEIRO) attackRate = ARCHER_RATE
        lastAttack = now
        attackAnim = 1f
        attackPunch = if (classe == Classe.MAGO) 0.5f else if (classe == Classe.ARQUEIRO) 0.35f else if (classe == Classe.NINJA) 0.6f else 1f
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

    fun skillReady(k: Int, now: Long): Boolean {
        if (classe == Classe.NINJA) {
            return k in 1..4 && alive && skillK == 0 && !rolling && grounded && dashT <= 0f && now >= ninjaReadyAt[k] && mp >= NINJA_COST[k]
        }
        return classe == Classe.ARQUEIRO && alive && skillK == 0 && !rolling && grounded &&
            now >= (if (k == 1) rainReadyAt else thunderReadyAt)
    }

    /** Segundos de recarga que faltam (0 = pronta). */
    fun skillCd(k: Int, now: Long): Float {
        if (classe == Classe.NINJA) return if (k in 1..4) ((ninjaReadyAt[k] - now) / 1000f).coerceAtLeast(0f) else 0f
        return (((if (k == 1) rainReadyAt else thunderReadyAt) - now) / 1000f).coerceAtLeast(0f)
    }

    fun startSkill(k: Int, now: Long): Boolean {
        if (!skillReady(k, now)) return false
        skillK = k
        if (classe == Classe.NINJA) {
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
        if (!alive || rolling || skillK != 0) return false
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
        if (!alive || rolling || skillK != 0 || !grounded || now - lastRoll < 700L) return false
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
        rollT = ROLL_TIME
        rollDur = ROLL_TIME
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
        if (flipT > 0f) flipT = max(0f, flipT - dt)
        if (phaseT > 0f) phaseT = max(0f, phaseT - dt)
        if (dashT > 0f) dashT = max(0f, dashT - dt)
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
            val spd = if (hopping) 440f * (1.25f - 0.9f * q) else if (dashing) 640f * (1.3f - 0.95f * q) else 300f * (1.4f - 0.75f * q)
            vx = rollDX * spd
            vy = rollDY * spd
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
            val slow = (if (charging) (if (weapon == 1) 0.28f else 0.45f) else 1f) * (if (flying) 1.25f else 1f) * (if (leapCharging) 0.3f else 1f) * (if (skillK != 0) 0.3f else 1f) * (if (bowHold) 0.5f else 1f)
            vx = inX * velH * 60f * slow
            vy = inY * velH * 60f * slow
        }
        x += vx * dt
        y += vy * dt
        val sp = hypot(vx, vy)
        if (sp > 1f) walkPhase += sp * dt * 0.05f
        hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
        if (attackAnim > 0f) {
            attackAnim = (attackAnim - dt * (if (classe == Classe.GUERREIRO || classe == Classe.MAGO || classe == Classe.BARBARO || classe == Classe.ARQUEIRO || classe == Classe.NINJA) attackRate else 2.8f)).coerceAtLeast(0f)
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
        if (maxMpH > 0f && mp < maxMpH) {
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

        if (!classe.isRanged && classe != Classe.GUERREIRO && classe != Classe.BARBARO && classe != Classe.NINJA) {
            Sprites.drawSlash(canvas, paint, sx, feet - 16f * s, facingX, facingY, attackAnim, classe.alcance, classe.cor)
        }

        drawBar(canvas, paint, sx, feet - (if (classe == Classe.MAGO) 80f else if (classe == Classe.BARBARO) 78f else 68f) * s, radius * 2.2f, hp / maxHp, 0xFF4CAF50.toInt())
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
