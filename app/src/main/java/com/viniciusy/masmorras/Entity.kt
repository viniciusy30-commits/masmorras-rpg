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

    /** Boneco de treino: nunca morre (a vida enche de novo quando o golpe seria fatal). */
    open val immortal: Boolean get() = false

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
            if (immortal) {
                hp = maxHp
                return
            }
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
        const val LUFFY_RATE_1 = 1.3f      // velocidade do soco do Luffy (1 / duração da animação em s): 0,77 s, com a volta do braço sem pressa
        const val LUFFY_RANGE = 260f       // alcance máximo do soco por alvo (px, da borda do alvo)
        const val LUFFY_PUNCH_CD = 1000L   // recarga do soco (ms)
        const val LUFFY_RATE_3 = 2.2f      // velocidade do soco duplo
        const val LUFFY_REACH = 150f       // alcance do soco esticado (Gomu Gomu no Pistol)
        const val LUFFY_REACH_2 = 180f     // alcance do soco duplo (Bazooka)
        // Gomu Gomu no Gatling (botão de skill 1): chuva de socos num retângulo à frente (a animação está em Luffy3D.GAT_*)
        const val LUFFY_GAT_CD = 8000L     // recarga (ms)
        const val LUFFY_GAT_LEN = 240f     // comprimento do retângulo à frente do Luffy (px)
        const val LUFFY_GAT_HALFW = 64f    // meia largura do retângulo (px)
        // Gomu Gomu no Gear Second (botão de skill 2): bombeia o sangue, a pele fica rosa e sai vapor. Buff temporário
        const val LUFFY_G2_TIME = 16f      // duração do buff depois da animação (s)
        const val LUFFY_G2_CD = 12000L     // recarga (ms), contada a partir do fim do buff
        const val LUFFY_G2_SPD = 1.4f      // velocidade de movimento
        const val LUFFY_G2_ATK = 1.5f      // velocidade dos socos (animação e recarga)
        const val LUFFY_G2_DMG = 1.3f      // dano
        const val LUFFY_G2_RATE = 3.8f     // Gear Second: o soco é só um tranco rápido de braço (animação de 0,26 s)
        const val LUFFY_G2_PUNCH_CD = 230L // Gear Second: recarga do soco básico (ms)
        // Soru (dash do Gear Second): fica invisível de tão rápido, controlável pelo joystick
        const val LUFFY_SORU_T = 1.0f      // duração (s)
        const val LUFFY_SORU_SPD = 2.8f    // multiplicador de velocidade (além do +40% do Gear Second)
        const val LUFFY_SORU_CD = 3500L    // recarga depois que acaba (ms)
        const val LUFFY_GAT_DMG = 0.16f    // dano de cada soco (x dano do herói); são Luffy3D.GAT_N = 60 socos (total 9,6x; antes 16 x 0,5 = 8x)
        const val EXPL_RATE_1 = 1.55f      // velocidade dos 2 primeiros estalos do chicote do Explorador
        const val EXPL_RATE_3 = 1.2f       // velocidade da chicotada por cima
        const val EXPL_REACH = 165f        // alcance do estalo do chicote
        const val EXPL_REACH_3 = 195f      // alcance da chicotada por cima
        const val SWING_LEN = 120f         // comprimento da corda do balanço do Explorador
        const val SWING_G = 1500f          // gravidade do balanço
        const val SWING_PUMP = 900f        // empurrão do balanço para o lado em que o jogador anda
        const val SWING_MAXV = 620f        // velocidade máxima do balanço
        // dash estilingue do Luffy: segurar o botão puxa o corpo (carga), soltar lança em arco; pousa rolando e levanta
        const val LUFFY_ANCHOR_D = 60f     // distância das mãos presas no chão (px)
        const val LUFFY_STRAFE_MAX = 34f   // quanto o corpo pode andar para os lados durante a carga (px): só um pouco, para não ficar feio
        const val LUFFY_STRAFE_SPD = 100f  // velocidade lateral durante a carga (px/s)
        const val LUFFY_WIND_MIN = 0.24f   // preparação mínima (toque rápido no botão)
        const val LUFFY_CHARGE_T = 0.55f   // tempo extra segurando até a carga máxima
        const val LUFFY_DIST_MIN = 200f    // alcance do voo sem carga (px)
        const val LUFFY_DIST_MAX = 500f    // alcance do voo com carga máxima (px)
        const val LUFFY_AIR_MIN = 0.52f    // duração do voo sem carga (s)
        const val LUFFY_AIR_MAX = 0.82f    // duração do voo com carga máxima (s)
        const val LUFFY_H_MIN = 46f        // altura do arco sem carga (px)
        const val LUFFY_H_MAX = 84f        // altura do arco com carga máxima (px)
        const val LUFFY_ROLL_T = 0.60f     // rolagem no chão + levantar (s)
        // toque rápido no dash = mortal para a frente que cai de pé (sem rolar)
        const val LUFFY_FLIP_C = 0.12f     // carga abaixo disso conta como toque
        const val LUFFY_FLIP_H = 62f       // altura do arco do mortal (px)
        const val LUFFY_FLIP_AIR = 0.56f   // duração do mortal no ar (s)
        // pulo carregado do Luffy: segurar = agacha e torce as pernas; soltar = pulo alto, pernas viram hélice e ele plana
        const val LUFFY_HELI_TAP = 0.16f   // abaixo disso (s) é só um toque: pulo normal
        const val LUFFY_HELI_CHARGE_T = 0.45f // tempo extra segurando até a carga máxima
        const val LUFFY_HELI_VZ_MIN = 560f // impulso com pouca carga (o pulo normal é 340)
        const val LUFFY_HELI_VZ_MAX = 740f // impulso com carga máxima
        const val LUFFY_HELI_G = 1150f     // gravidade enquanto sobe (mais leve = pulo mais alto)
        const val LUFFY_HELI_FALL = -105f  // velocidade máxima de queda planando (px/s)
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
    private val danoBase: Float = heroi.dano
    /** Dano do herói; o Luffy no Gear Second causa mais. */
    val danoH: Float get() = danoBase * (if (classe == Classe.LUFFY && g2T > 0f) LUFFY_G2_DMG else 1f)
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

    // Luffy: dash estilingue. Fases: 0 = nada, 1 = preparação (segurar carrega), 2 = voo em arco, 3 = rolagem e levantar
    var luffyPh = 0
    var luffyT = 0f          // tempo dentro da fase (s)
    var luffyCharge = 0f     // 0..1
    var luffyHeld = false    // botão de rolar ainda pressionado
    var luffyAirT = LUFFY_AIR_MIN
    var luffyVh = 0f         // velocidade horizontal do voo
    var luffyVl = 0f         // velocidade horizontal ao pousar (decai na rolagem)
    var luffyG = 1000f       // gravidade do arco
    var luffyFlip = false    // o dash atual é o mortal (toque rápido)
    var luffyLandT = LUFFY_ROLL_T // duração da fase 3 (rolagem ou pouso do mortal)
    var luffyAX = 0f         // ponto do chão onde as mãos agarram (fixo durante a carga)
    var luffyAY = 0f
    var luffyDX0 = 0f        // direção inicial do dash
    var luffyDY0 = 1f
    var luffyOff = 0f        // deslocamento lateral do corpo durante a carga (px)
    var luffyLaunchReq = false // o GameView toca o som / poeira do lançamento quando liga
    var heliCharging = false // Luffy: segurando o pulo no chão (agacha e fica parado: não anda)
    var heliChargeT = 0f     // segundos segurando
    var luffyPunchLen = 150f // distância (px) até o alvo do soco atual (reserva, se o alvo sumir)
    // alvo do soco atual, atualizado a cada quadro pelo GameView (coordenadas do mundo; o desenho converte para a tela)
    var luffyTgtOn = false
    var luffyTgtX = 0f       // x do peito do alvo
    var luffyTgtY = 0f       // y do peito do alvo (já com o deslocamento de pé/altura)
    var luffyTgtGY = 0f      // y do chão do alvo
    var luffyTgtR = 0f       // raio do alvo
    var heli = false         // Luffy: pulo carregado ativo (da soltura até pousar)
    var luffyGatReadyAt = 0L // Gatling: instante em que a recarga acaba
    var luffyG2ReadyAt = 0L  // Gear Second: instante em que a recarga acaba
    var g2T = 0f             // Gear Second: segundos restantes do buff (0 = desligado)
    var soruT = 0f           // Soru: segundos restantes (0 = fora)
    var soruDX = 0f          // direção atual do Soru (unitária); o joystick muda
    var soruDY = 1f
    var luffySoruReadyAt = 0L
    var gatPtN = 0           // Gatling: inimigos dentro do retângulo (frente, lado em px), preenchido pelo GameView
    val gatPtF = FloatArray(6)
    val gatPtU = FloatArray(6)
    var heliT = 0f           // segundos desde a soltura

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

    /** Imune a contato: rolando, exceto o Luffy ainda carregando o dash (aí ele está parado e vulnerável). */
    val invuln: Boolean get() = (rolling && !(classe == Classe.LUFFY && luffyPh == 1)) || soruT > 0f
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
        if (soruT > 0f || rolling || swinging || skillK != 0 || voltT > 0f || ichiT > 0f || now < swapUntil || leapCharging || leaping || flipT > 0f || (dashT > 0f && !dashUp)) return false
        if (classe == Classe.LUFFY && (heliCharging || heli)) return false
        val cd = if (classe == Classe.GUERREIRO) (if (comboStep == 2 || comboStep == -1 && swing == 3) 680L else 380L)
        else if (classe == Classe.BARBARO) (if (comboStep == 2) 720L else 440L)
        else if (classe == Classe.NINJA) (if (comboStep == 2) 520L else 300L)
        else if (classe == Classe.ICHIGO) (if (comboStep == 2) 560L else 320L)
        else if (classe == Classe.LUFFY) (if (g2T > 0f) LUFFY_G2_PUNCH_CD else LUFFY_PUNCH_CD)
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
            // soco direto no alvo: alterna a mão direita e a esquerda
            comboStep = if (comboStep == 0) 1 else 0
            swing = comboStep
            attackRate = if (g2T > 0f) LUFFY_G2_RATE else LUFFY_RATE_1
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

    /** Soru: só no Gear Second, parado em qualquer outra ação e fora da recarga. */
    fun soruReady(now: Long): Boolean =
        classe == Classe.LUFFY && alive && g2T > 0f && soruT <= 0f && skillK == 0 && !rolling && luffyPh == 0 &&
            !heli && !heliCharging && now >= luffySoruReadyAt

    fun startSoru(now: Long, dx: Float, dy: Float) {
        soruT = LUFFY_SORU_T
        soruDX = dx
        soruDY = dy
        facingX = dx
        facingY = dy
        attackAnim = 0f
        luffySoruReadyAt = now + (LUFFY_SORU_T * 1000f).toLong() + LUFFY_SORU_CD
    }

    fun skillReady(k: Int, now: Long): Boolean {
        if (classe == Classe.ICHIGO) {
            val rd = if (hollow) ichiReadyH else ichiReadyN
            return k in 1..5 && alive && skillK == 0 && ichiT <= 0f && !rolling && (grounded || sky) && now >= rd[k] && mp >= ichiCost(k)
        }
        if (classe == Classe.LUFFY) {
            if (k == 2) return alive && skillK == 0 && !rolling && grounded && luffyPh == 0 && !heli && !heliCharging && !swinging && g2T <= 0f && now >= luffyG2ReadyAt
            return k == 1 && alive && skillK == 0 && !rolling && grounded && luffyPh == 0 && !heli && !heliCharging && !swinging && now >= luffyGatReadyAt
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
        if (classe == Classe.LUFFY) return if (k == 1) ((luffyGatReadyAt - now) / 1000f).coerceAtLeast(0f) else if (k == 2) ((luffyG2ReadyAt - now) / 1000f).coerceAtLeast(0f) else 0f
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
        } else if (classe == Classe.LUFFY) {
            if (k == 2) {
                skillDur = Luffy3D.G2_DUR
                luffyG2ReadyAt = now + ((Luffy3D.G2_DUR + LUFFY_G2_TIME) * 1000f).toLong() + LUFFY_G2_CD
            } else {
                skillDur = Luffy3D.GAT_DUR
                luffyGatReadyAt = now + LUFFY_GAT_CD
            }
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

    /** Luffy: botão de pular apertado no chão. Se segurar, agacha e torce as pernas; se soltar rápido, é um pulo normal. */
    fun startHeliCharge(): Boolean {
        if (!alive || classe != Classe.LUFFY || rolling || !grounded || heliCharging || luffyPh != 0 || skillK != 0) return false
        heliCharging = true
        heliChargeT = 0f
        return true
    }

    /**
     * Luffy: soltou o botão. Toque rápido = pulo normal. Segurou = pula mais alto (quanto mais carga, mais alto)
     * com as pernas torcidas; no ar elas soltam e giram como hélice, e ele plana até o chão.
     * Devolve true se saiu do chão.
     */
    fun releaseHeli(): Boolean {
        if (!heliCharging) return false
        heliCharging = false
        val t = heliChargeT
        heliChargeT = 0f
        if (!alive || !grounded) return false
        if (t < LUFFY_HELI_TAP) return jump()
        val c = lfSmooth((t - LUFFY_HELI_TAP) / LUFFY_HELI_CHARGE_T)
        vz = lfLerp(LUFFY_HELI_VZ_MIN, LUFFY_HELI_VZ_MAX, c)
        alt = ground + 0.7f
        airJumped = true
        flipT = 0f
        heli = true
        heliT = 0f
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
        if (!alive || rolling || swinging || skillK != 0 || voltT > 0f || !grounded || heliCharging || now - lastRoll < (if (classe == Classe.PIKACHU) 900L else 700L)) return false
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
        rollT = if (classe == Classe.PIKACHU) PIKA_QUICK_T else if (classe == Classe.LUFFY) 9f else ROLL_TIME
        rollDur = rollT
        dashing = false
        lastRoll = now
        attackAnim = 0f
        charging = false
        chargeT = 0f
        if (classe == Classe.LUFFY) luffyBegin()
        return true
    }

    // ───────────── Luffy: dash estilingue (Gomu Gomu no Rocket) ─────────────

    private fun lfSmooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun lfLerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun luffyBegin() {
        heli = false
        luffyFlip = false
        luffyPh = 1
        luffyT = 0f
        luffyCharge = 0f
        luffyHeld = true
        luffyVh = 0f
        luffyVl = 0f
        luffyDX0 = rollDX
        luffyDY0 = rollDY
        luffyAX = x + rollDX * LUFFY_ANCHOR_D
        luffyAY = y + rollDY * LUFFY_ANCHOR_D
        luffyOff = 0f
        rollT = 9f    // fica positivo nas 3 fases (o resto do jogo usa "rolling"); o fim é decidido por luffyEnd()
        rollDur = 9f
    }

    /** Solta o estilingue: sobe em arco para a frente. Quanto mais carga, mais longe, mais alto e mais demorado. */
    private fun luffyLaunch() {
        val c = luffyCharge
        luffyFlip = c < LUFFY_FLIP_C
        val dist = if (luffyFlip) LUFFY_DIST_MIN else lfLerp(LUFFY_DIST_MIN, LUFFY_DIST_MAX, c)
        val h = if (luffyFlip) LUFFY_FLIP_H else lfLerp(LUFFY_H_MIN, LUFFY_H_MAX, c)
        luffyAirT = if (luffyFlip) LUFFY_FLIP_AIR else lfLerp(LUFFY_AIR_MIN, LUFFY_AIR_MAX, c)
        luffyVh = dist / luffyAirT
        luffyG = 8f * h / (luffyAirT * luffyAirT)   // parábola: sobe h e volta ao chão em luffyAirT
        vz = 4f * h / luffyAirT
        alt = ground + 0.7f
        airJumped = false
        luffyPh = 2
        luffyT = 0f
        luffyLaunchReq = true
    }

    /** Chamado pelo updateVertical quando o voo toca o chão: começa a rolagem. */
    private fun luffyTouch() {
        luffyPh = 3
        luffyT = 0f
        if (luffyFlip) {
            // cai de pé: se está andando, o pouso é curto e já emenda no passo; parado, assenta um pouco
            luffyLandT = if (hypot(inX, inY) > 0.3f) 0.34f else 0.5f
            luffyVl = luffyVh * 0.9f
            landed = max(landed, 0.6f)
        } else {
            luffyLandT = LUFFY_ROLL_T
            luffyVl = luffyVh * 0.8f
            landed = max(landed, 0.85f)   // poeira + som de pouso do GameView
        }
    }

    private fun luffyEnd() {
        luffyPh = 0
        luffyT = 0f
        luffyHeld = false
        luffyVh = 0f
        luffyVl = 0f
        rollT = 0f
        dashing = false
        if (!luffyFlip) {
            vx = 0f
            vy = 0f
        }   // no mortal a velocidade continua: o jogo assume o passo sem parada
        lastRoll = System.currentTimeMillis() - (if (luffyFlip) 450L else 350L)   // recarga curta depois de levantar
        luffyFlip = false
    }

    /** Bateu na parede durante o dash: perde velocidade (o jogo ainda desliza pela parede). */
    fun luffyBump(dt: Float) {
        val k = max(0f, 1f - dt * 5f)
        if (luffyPh == 2) luffyVh *= k
        if (luffyPh == 3) luffyVl *= k
    }

    private fun luffyStep(dt: Float) {
        rollT = 9f
        luffyT += dt
        when (luffyPh) {
            1 -> {
                // preparação: segurar depois do mínimo vai carregando; soltar (ou carga cheia) lança
                val ready = luffyT >= LUFFY_WIND_MIN
                if (luffyHeld && ready) luffyCharge = min(1f, (luffyT - LUFFY_WIND_MIN) / LUFFY_CHARGE_T)
                // as mãos ficam presas no chão: o corpo só anda para os lados (sem dar a volta no braço) e o
                // lançamento sai na direção das mãos: reto ou nas diagonais
                val px = -luffyDY0
                val py = luffyDX0
                var mv = (inX * px + inY * py) * LUFFY_STRAFE_SPD
                if (dt > 0f) {
                    val next = (luffyOff + mv * dt).coerceIn(-LUFFY_STRAFE_MAX, LUFFY_STRAFE_MAX)
                    mv = (next - luffyOff) / dt
                    luffyOff = next
                }
                val k = 1f - min(1f, luffyT / LUFFY_WIND_MIN)
                val rec = -50f * k * k          // leve recuo no começo, depois fica firme no chão
                vx = px * mv + luffyDX0 * rec
                vy = py * mv + luffyDY0 * rec
                val ax = luffyAX - x
                val ay = luffyAY - y
                val al = hypot(ax, ay)
                if (al > 8f) {
                    rollDX = ax / al
                    rollDY = ay / al
                    facingX = rollDX
                    facingY = rollDY
                }
                if (ready && !luffyHeld) luffyLaunch()
            }
            2 -> {
                // voo: sai um pouco mais devagar e ganha velocidade suave (sem arrancada seca)
                var acc = 0.55f + 0.45f * lfSmooth(luffyT / 0.14f)
                // mortal: perde velocidade nos últimos 30% do voo para pousar sem escorregar
                if (luffyFlip) acc *= 1f - 0.5f * lfSmooth((luffyT / luffyAirT - 0.7f) / 0.3f)
                vx = rollDX * luffyVh * acc
                vy = rollDY * luffyVh * acc
            }
            else -> {
                if (luffyFlip) {
                    // pouso do mortal: a velocidade do voo vira a velocidade de caminhada (sem freada seca)
                    val k = lfSmooth(luffyT / (luffyLandT * 0.55f))
                    vx = rollDX * luffyVl * (1f - k) + inX * velH * 60f * spdMul * k
                    vy = rollDY * luffyVl * (1f - k) + inY * velH * 60f * spdMul * k
                } else {
                    // rolagem: o avanço vai morrendo até ele estar de pé
                    val k = 1f - min(1f, luffyT / (LUFFY_ROLL_T * 0.8f))
                    val spd = luffyVl * k * k
                    vx = rollDX * spd
                    vy = rollDY * spd
                }
                if (luffyT >= luffyLandT) luffyEnd()
            }
        }
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
            val grav = if (classe == Classe.LUFFY && luffyPh == 2) luffyG
                else if (classe == Classe.LUFFY && heli && vz > 0f) LUFFY_HELI_G
                else if (dashT > 0f && !dashUp) 380f else 1500f
            vz -= grav * dt
            if (classe == Classe.LUFFY && heli && vz < LUFFY_HELI_FALL) vz = LUFFY_HELI_FALL // planando
            if (glide && vz < -170f) vz = -170f // desce flutuando depois do voo
            alt += vz * dt
            if (alt <= groundPx) {
                glide = false
                if (classe == Classe.LUFFY && luffyPh == 2) {
                    luffyTouch()
                } else if (vz < -210f) {
                    landed = (-vz) / 400f
                    landSquash = ((-vz) / 380f).coerceIn(0.15f, 0.55f)
                }
                alt = groundPx
                vz = 0f
                airJumped = false
                heli = false
                flipT = 0f
                dashT = 0f
            }
        } else {
            airJumped = false
            heli = false
            alt += (groundPx - alt) * min(1f, dt * 30f)
            if (abs(groundPx - alt) < 0.3f) alt = groundPx
            vz = 0f
        }
    }

    override fun update(dt: Float) {
        if (!alive) swinging = false
        if (!alive && luffyPh != 0) luffyEnd()
        if (swinging) swingStep(dt)
        if (flipT > 0f) flipT = max(0f, flipT - dt)
        if (heli) heliT += dt else heliT = 0f
        if (heliCharging) {
            heliChargeT += dt
            if (!alive || !grounded || rolling) { heliCharging = false; heliChargeT = 0f }
        }
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
        if (classe == Classe.LUFFY && luffyPh != 0) {
            luffyStep(dt)
        } else if (rollT > 0f) {
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
            run {
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
            var ix = inX
            var iy = inY
            if (classe == Classe.LUFFY && soruT > 0f) {
                // Soru: sempre em velocidade máxima; o joystick vira o rumo, sem joystick segue o último
                val il = hypot(inX, inY)
                if (il > 0.15f) {
                    soruDX = inX / il
                    soruDY = inY / il
                }
                ix = soruDX
                iy = soruDY
                facingX = soruDX
                facingY = soruDY
            }
            val slow = if (classe == Classe.LUFFY && soruT > 0f) LUFFY_SORU_SPD * LUFFY_G2_SPD else (if (charging) (if (weapon == 1) 0.28f else 0.45f) else 1f) * (if (flying) 1.25f else if (sky) 1.15f else 1f) * (if (leapCharging) 0.3f else 1f) * (if (heliCharging) 0f else 1f) * (if (classe == Classe.LUFFY && attackAnim > 0f) 0.45f else 1f) * (if (skillK != 0) (if (classe == Classe.NINJA) 0.9f else if (classe == Classe.PIKACHU) 0.45f else if (classe == Classe.LUFFY) 0f else 0.3f) else 1f) * (if (bowHold) 0.5f else 1f) * (if (classe == Classe.ICHIGO && hollow) 1.12f else 1f) * (if (classe == Classe.LUFFY && g2T > 0f) LUFFY_G2_SPD else 1f)
            vx = ix * velH * 60f * slow * spdMul + momX
            vy = iy * velH * 60f * slow * spdMul + momY
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
            if (skillT <= 0f) {
                if (classe == Classe.LUFFY && skillK == 2) g2T = LUFFY_G2_TIME   // terminou de bombear: o buff começa
                skillT = 0f
                skillK = 0
            }
        }
        if (g2T > 0f) {
            g2T = if (alive) max(0f, g2T - dt) else 0f
        }
        if (soruT > 0f) soruT = if (alive) max(0f, soruT - dt) else 0f
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
        val gatNow = classe == Classe.LUFFY && skillK == 1
        Luffy3D.skillP = if (gatNow) 1f - skillT / skillDur else -1f
        Luffy3D.gatActive = gatNow
        Luffy3D.gatDX = facingX
        Luffy3D.gatDY = facingY
        Luffy3D.gatLen = LUFFY_GAT_LEN
        Luffy3D.gatHalfW = LUFFY_GAT_HALFW
        Luffy3D.gatSqX = squashX
        Luffy3D.gatSqY = squashY
        Luffy3D.gatPtN = gatPtN
        for (gi in 0 until gatPtN) {
            Luffy3D.gatPtF[gi] = gatPtF[gi]
            Luffy3D.gatPtU[gi] = gatPtU[gi]
        }
        Luffy3D.g2P = if (classe == Classe.LUFFY && skillK == 2) 1f - skillT / skillDur else -1f
        Luffy3D.g2Act = if (classe == Classe.LUFFY && g2T > 0f) min(1f, g2T) else 0f   // apaga devagar no último segundo
        Luffy3D.rollP = if (classe == Classe.LUFFY && luffyPh != 0) 0.5f else -1f
        Luffy3D.dashPh = if (classe == Classe.LUFFY) luffyPh else 0
        Luffy3D.dashU = when (luffyPh) {
            1 -> luffyT                                           // segundos de preparação
            2 -> (luffyT / luffyAirT).coerceIn(0f, 1f)             // progresso do voo
            3 -> (luffyT / luffyLandT).coerceIn(0f, 1f)            // progresso da rolagem / pouso
            else -> 0f
        }
        Luffy3D.dashCharge = luffyCharge
        Luffy3D.dashFlip = classe == Classe.LUFFY && luffyFlip
        Luffy3D.heli = classe == Classe.LUFFY && heli
        Luffy3D.heliCharge = if (classe == Classe.LUFFY && heliCharging) heliChargeT else -1f
        Luffy3D.heliT = heliT
        Luffy3D.wx = x                 // posição no mundo (o rastro do voo é guardado em coordenadas do mundo)
        Luffy3D.wy = y - alt
        Luffy3D.inAir = up > 1f
        Luffy3D.airH = up
        Luffy3D.airVz = vz
        Luffy3D.swing = swing
        Luffy3D.punchLen = luffyPunchLen
        // alvo do soco e âncora das mãos, em px de tela a partir do pé do herói (desfeito o squash do canvas)
        Luffy3D.punchOn = classe == Classe.LUFFY && luffyTgtOn && attackAnim > 0.001f
        Luffy3D.punchTX = (luffyTgtX - camX - drawX) / squashX
        Luffy3D.punchTY = (luffyTgtY - camY - drawY) / squashY
        Luffy3D.punchGY = (luffyTgtGY - camY - drawY) / squashY
        Luffy3D.punchInset = luffyTgtR * 0.5f
        Luffy3D.anchorOn = classe == Classe.LUFFY && luffyPh == 1
        Luffy3D.anchorTX = (luffyAX - camX - drawX) / squashX
        Luffy3D.anchorTY = (luffyAY + radius * 0.55f - alt - camY - drawY) / squashY
        Luffy3D.dashVX = vx
        Luffy3D.dashVY = vy
        // Soru: some quase por completo (some em 0,08 s e volta em 0,15 s)
        Luffy3D.alphaV = if (classe == Classe.LUFFY && soruT > 0f) {
            val el = LUFFY_SORU_T - soruT
            if (el < 0.08f) 1f - el / 0.08f else if (soruT < 0.15f) 1f - soruT / 0.15f else 0.03f
        } else 1f
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

    // boneco de treino: parado, não ataca, não morre e recupera a vida sozinho
    var dummy = false
    var wobble = 0f          // balanço depois de levar um golpe (1 = máximo)
    var wobbleSide = 1f
    private var regenT = 0f
    override val immortal: Boolean get() = dummy

    /** O GameView chama quando o boneco leva um golpe: balança para o lado oposto ao do golpe. */
    fun dummyHit(fromX: Float) {
        wobble = 1f
        wobbleSide = if (fromX < x) 1f else -1f
        regenT = 3f
    }

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
        if (dummy) {
            wobble = (wobble - dt * 1.7f).coerceAtLeast(0f)
            if (regenT > 0f) regenT -= dt else hp = min(maxHp, hp + maxHp * 0.6f * dt)
            return
        }
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

        if (dummy) {
            drawDummy(canvas, paint, sx, feet)
            return
        }

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

    /** Boneco de treino de madeira e palha: poste, travessa, tronco de palha com alvo e cabeça de saco. */
    private fun drawDummy(canvas: Canvas, paint: Paint, sx: Float, feet: Float) {
        val u = 0.82f
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(sx - 24f * u, feet - 7f * u, sx + 24f * u, feet + 7f * u, paint)
        canvas.save()
        val ang = sin(VoxelArt.time * 24f) * 9f * wobble * wobble * wobbleSide
        canvas.rotate(ang, sx, feet)
        val fl = hitFlash
        fun mix(c: Int): Int {
            val k = fl * 0.7f
            val r = ((c shr 16 and 255) * (1f - k) + 255f * k).toInt()
            val g = ((c shr 8 and 255) * (1f - k) + 255f * k).toInt()
            val b = ((c and 255) * (1f - k) + 255f * k).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        // base
        paint.color = mix(0xFF5D4037.toInt())
        canvas.drawRoundRect(sx - 20f * u, feet - 8f * u, sx + 20f * u, feet + 3f * u, 4f, 4f, paint)
        paint.color = mix(0xFF795548.toInt())
        canvas.drawRoundRect(sx - 20f * u, feet - 8f * u, sx + 20f * u, feet - 4f * u, 4f, 4f, paint)
        // poste
        paint.color = mix(0xFF8D6E63.toInt())
        canvas.drawRect(sx - 5f * u, feet - 66f * u, sx + 5f * u, feet - 6f * u, paint)
        paint.color = mix(0xFF6D4C41.toInt())
        canvas.drawRect(sx + 1.5f * u, feet - 66f * u, sx + 5f * u, feet - 6f * u, paint)
        // travessa dos braços
        paint.color = mix(0xFF7B5A48.toInt())
        canvas.drawRoundRect(sx - 30f * u, feet - 56f * u, sx + 30f * u, feet - 47f * u, 3f, 3f, paint)
        paint.color = mix(0xFFA1887F.toInt())
        canvas.drawRect(sx - 30f * u, feet - 56f * u, sx + 30f * u, feet - 53f * u, paint)
        // tronco de palha
        paint.color = mix(0xFFD9B66A.toInt())
        canvas.drawRoundRect(sx - 15f * u, feet - 64f * u, sx + 15f * u, feet - 26f * u, 6f, 6f, paint)
        paint.color = mix(0xFFB8924A.toInt())
        canvas.drawRoundRect(sx + 6f * u, feet - 64f * u, sx + 15f * u, feet - 26f * u, 6f, 6f, paint)
        paint.color = mix(0xFF8B6B3E.toInt())
        canvas.drawRect(sx - 15f * u, feet - 58f * u, sx + 15f * u, feet - 55f * u, paint)
        canvas.drawRect(sx - 15f * u, feet - 34f * u, sx + 15f * u, feet - 31f * u, paint)
        // alvo no peito
        val cy = feet - 45f * u
        paint.color = mix(0xFFC62828.toInt())
        canvas.drawCircle(sx, cy, 10f * u, paint)
        paint.color = mix(0xFFF5F0E1.toInt())
        canvas.drawCircle(sx, cy, 6.5f * u, paint)
        paint.color = mix(0xFFC62828.toInt())
        canvas.drawCircle(sx, cy, 3f * u, paint)
        // cabeça de saco
        paint.color = mix(0xFFE6D3A3.toInt())
        canvas.drawCircle(sx, feet - 76f * u, 11f * u, paint)
        paint.color = mix(0xFFCDB887.toInt())
        canvas.drawArc(sx - 11f * u, feet - 87f * u, sx + 11f * u, feet - 65f * u, -40f, 120f, true, paint)
        paint.color = mix(0xFF4E342E.toInt())
        paint.strokeWidth = 1.6f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(sx - 6f * u, feet - 80f * u, sx - 2f * u, feet - 76f * u, paint)
        canvas.drawLine(sx - 2f * u, feet - 80f * u, sx - 6f * u, feet - 76f * u, paint)
        canvas.drawLine(sx + 2f * u, feet - 80f * u, sx + 6f * u, feet - 76f * u, paint)
        canvas.drawLine(sx + 6f * u, feet - 80f * u, sx + 2f * u, feet - 76f * u, paint)
        canvas.drawLine(sx - 4f * u, feet - 70f * u, sx + 4f * u, feet - 70f * u, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
        drawBar(canvas, paint, sx, feet - 96f * u - 8f, 44f, hp / maxHp, 0xFFE53935.toInt())
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
