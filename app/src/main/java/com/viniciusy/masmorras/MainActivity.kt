package com.viniciusy.masmorras

import kotlin.math.hypot
import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import android.widget.ProgressBar
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Bitmap
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.SeekBar
import android.content.res.ColorStateList
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import android.os.Build
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager

class MainActivity : AppCompatActivity() {

    private lateinit var menuLayout: View
    private lateinit var classLayout: View
    private lateinit var gameContainer: View
    private lateinit var configLayout: View
    private lateinit var novidadesLayout: View
    private lateinit var gameView: GameView
    private lateinit var hpBar: ProgressBar
    private lateinit var mpBar: ProgressBar
    private lateinit var tvClasseHud: TextView
    private lateinit var ivClasseBadge: android.widget.ImageView
    private lateinit var tvHpNum: TextView
    private lateinit var tvMpNum: TextView
    private lateinit var tvInimigos: TextView
    private lateinit var mpRow: View
    private lateinit var btnWeapon: android.widget.ImageButton
    private lateinit var ivManaSlot: View
    private lateinit var btnAttack: android.widget.ImageButton
    private lateinit var btnSkill1: android.widget.ImageButton
    private lateinit var btnSkill2: android.widget.ImageButton
    private lateinit var tvSkill1Cd: TextView
    private lateinit var tvSkill2Cd: TextView
    private lateinit var btnSkill3: android.widget.ImageButton
    private lateinit var btnSkill4: android.widget.ImageButton
    private lateinit var btnSkill5: android.widget.ImageButton
    private lateinit var tvSkill5Cd: TextView
    private lateinit var tvSkill3Cd: TextView
    private lateinit var tvSkill4Cd: TextView
    private lateinit var hudLayer: View
    private val prevCd = FloatArray(5)
    private var ichiHolShown = false
    private lateinit var shadowTimer: ShadowTimerView
    private lateinit var btnRoll: android.widget.ImageButton
    private lateinit var menuBg: MenuBgView
    private lateinit var overlayFim: View
    private lateinit var tvFim: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var tvNovidades: TextView

    private var selectedClasse: Classe = Classe.GUERREIRO
    private var joystickActive = false
    private var joyCenterX = 0f
    private var joyCenterY = 0f
    private var knob: View? = null

    // mapas / editor
    private lateinit var mapListLayout: View
    private lateinit var editorLayout: View
    private lateinit var editorView: MapEditorView
    private lateinit var rvMaps: RecyclerView
    private lateinit var tvMapListTitle: TextView
    private lateinit var tvMapListHint: TextView
    private lateinit var btnNovoMapa: View
    private lateinit var edTopBar: LinearLayout
    private lateinit var edTools: LinearLayout
    private lateinit var edTabs: LinearLayout
    private lateinit var edPalette: LinearLayout
    private lateinit var tvEdName: TextView
    private lateinit var btnUndo: TextView
    private lateinit var btnRedo: TextView
    private lateinit var btnGrid: TextView
    private val toolChips = ArrayList<Pair<Int, TextView>>()
    private val tabChips = ArrayList<Pair<Int, TextView>>()
    private val paletteViews = ArrayList<Pair<Int, View>>()
    private var mapListEdit = false
    private var selectedMapId: String? = null
    private var currentCustom: MapData? = null
    private var editingMap: MapData? = null
    private var testingFromEditor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        setContentView(R.layout.activity_main)
        MapTex.init(this)
        Sfx.init(this)

        menuLayout = findViewById(R.id.menuLayout)
        classLayout = findViewById(R.id.classLayout)
        gameContainer = findViewById(R.id.gameContainer)
        configLayout = findViewById(R.id.configLayout)
        novidadesLayout = findViewById(R.id.novidadesLayout)
        gameView = findViewById(R.id.gameView)
        hpBar = findViewById(R.id.hpBar)
        mpBar = findViewById(R.id.mpBar)
        tvClasseHud = findViewById(R.id.tvClasseHud)
        ivClasseBadge = findViewById(R.id.ivClasseBadge)
        tvHpNum = findViewById(R.id.tvHpNum)
        tvMpNum = findViewById(R.id.tvMpNum)
        tvInimigos = findViewById(R.id.tvInimigos)
        mpRow = findViewById(R.id.mpRow)
        btnWeapon = findViewById(R.id.btnWeapon)
        btnAttack = findViewById(R.id.btnAttack)
        shadowTimer = findViewById(R.id.shadowTimer)
        btnRoll = findViewById(R.id.btnRoll)
        btnSkill1 = findViewById(R.id.btnSkill1)
        btnSkill2 = findViewById(R.id.btnSkill2)
        tvSkill1Cd = findViewById(R.id.tvSkill1Cd)
        tvSkill2Cd = findViewById(R.id.tvSkill2Cd)
        btnSkill3 = findViewById(R.id.btnSkill3)
        btnSkill4 = findViewById(R.id.btnSkill4)
        btnSkill5 = findViewById(R.id.btnSkill5)
        tvSkill5Cd = findViewById(R.id.tvSkill5Cd)
        tvSkill3Cd = findViewById(R.id.tvSkill3Cd)
        tvSkill4Cd = findViewById(R.id.tvSkill4Cd)
        ivManaSlot = findViewById(R.id.ivManaSlot)
        hudLayer = findViewById(R.id.hudLayer)
        menuBg = findViewById(R.id.menuBg)
        overlayFim = findViewById(R.id.overlayFim)
        tvFim = findViewById(R.id.tvFim)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        tvNovidades = findViewById(R.id.tvNovidades)
        knob = findViewById(R.id.joystickKnob)
        mapListLayout = findViewById(R.id.mapListLayout)
        editorLayout = findViewById(R.id.editorLayout)
        editorView = findViewById(R.id.editorView)
        rvMaps = findViewById(R.id.rvMaps)
        tvMapListTitle = findViewById(R.id.tvMapListTitle)
        tvMapListHint = findViewById(R.id.tvMapListHint)
        btnNovoMapa = findViewById(R.id.btnNovoMapa)
        edTopBar = findViewById(R.id.edTopBar)
        edTools = findViewById(R.id.edTools)
        edTabs = findViewById(R.id.edTabs)
        edPalette = findViewById(R.id.edPalette)

        setupMenu()
        setupClassSelect()
        setupMapList()
        setupEditor()
        setupGameControls()
        setupConfig()
        setupNovidades()
        setupBack()
        setupSafeArea()
        enterImmersive()
        menuBg.setRunning(true)

        Herois.init(this)

        // Auto check update
        Updater.autoCheck(this)

        // Mostra novidades se primeira vez após update
        checkNovidades()
        Sfx.playMusic("music_menu")
    }

    // ───────────────────────── sons de interface ─────────────────────────

    private val silentIds: Set<Int> by lazy {
        setOf(R.id.btnAttack, R.id.btnJump, R.id.btnRoll, R.id.btnWeapon, R.id.btnSkill1, R.id.btnSkill2, R.id.btnSkill3, R.id.btnSkill4, R.id.btnSkill5, R.id.joystickArea)
    }

    /** Qualquer toque num botão/cartão da interface faz um "clique"; os botões de combate têm sons próprios. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val v = clickableAt(window.decorView, ev.rawX.toInt(), ev.rawY.toInt())
            if (v != null && v.id !in silentIds) {
                val back = v.id == R.id.btnVoltarClasse || v.id == R.id.btnVoltarMapas || v.id == R.id.btnVoltarConfig ||
                    v.id == R.id.btnFecharNovidades || v.id == R.id.btnMenu || v.id == R.id.btnSairJogo
                val confirm = v.id == R.id.btnJogar || v.id == R.id.btnTentar ||
                    (v.parent != null && (v.parent as? View)?.id == R.id.rvClasses)
                Sfx.play(if (back) "ui_back" else if (confirm) "ui_confirm" else "ui_click", 0.8f)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun clickableAt(v: View, x: Int, y: Int): View? {
        if (!v.isShown) return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        if (x < loc[0] || x >= loc[0] + v.width || y < loc[1] || y >= loc[1] + v.height) return null
        if (v is ViewGroup) {
            for (i in v.childCount - 1 downTo 0) {
                val r = clickableAt(v.getChildAt(i), x, y)
                if (r != null) return r
            }
        }
        return if (v.isClickable && v.isEnabled) v else null
    }

    /** Tela cheia: esconde status bar e barra de navegação (deslize da borda para mostrar). */
    private fun enterImmersive() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Empurra a interface para longe do notch / bordas, sem encolher o jogo em si. */
    private fun setupSafeArea() {
        val targets = listOf(menuLayout, classLayout, mapListLayout, configLayout, hudLayer, editorLayout, novidadesLayout)
        val base = targets.associateWith { intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
            for ((v, b) in base) v.setPadding(b[0] + i.left, b[1] + i.top, b[2] + i.right, b[3] + i.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun setupMenu() {
        findViewById<View>(R.id.btnJogar).setOnClickListener {
            openMapList(false)
        }
        findViewById<View>(R.id.btnEditor).setOnClickListener {
            openMapList(true)
        }
        findViewById<View>(R.id.btnConfig).setOnClickListener {
            show(configLayout)
        }
    }

    private fun setupClassSelect() {
        val rv = findViewById<RecyclerView>(R.id.rvClasses)
        rv.layoutManager = GridLayoutManager(this, 4)
        rv.adapter = ClasseAdapter(Classe.values().toList()) { classe ->
            selectedClasse = classe
            if (classe == Classe.PIKACHU || classe == Classe.ICHIGO || classe == Classe.LUFFY) onClassPicked(classe) else HeroCreator.mostrar(this, classe) { onClassPicked(classe) }
        }
        findViewById<View>(R.id.btnVoltarClasse).setOnClickListener {
            openMapList(false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGameControls() {
        val joyArea = findViewById<FrameLayout>(R.id.joystickArea)
        joyArea.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    joystickActive = true
                    val cx = v.width / 2f
                    val cy = v.height / 2f
                    joyCenterX = cx
                    joyCenterY = cy
                    var dx = event.x - cx
                    var dy = event.y - cy
                    val maxR = 32f * resources.displayMetrics.density
                    val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (len > maxR) {
                        dx = dx / len * maxR
                        dy = dy / len * maxR
                    }
                    knob?.translationX = dx
                    knob?.translationY = dy
                    gameView.setMove(dx / maxR, dy / maxR)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    joystickActive = false
                    knob?.translationX = 0f
                    knob?.translationY = 0f
                    gameView.setMove(0f, 0f)
                    true
                }
                else -> false
            }
        }

        // toque = combo de 3 golpes; segurar = levanta a espada, soltar = golpe forte
        findViewById<View>(R.id.btnAttack).setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    gameView.attackDown()
                }
                MotionEvent.ACTION_MOVE -> {
                    // lança: arraste o dedo a partir do botão para mirar (solte para arremessar)
                    val ax = ev.x - v.width * 0.5f
                    val ay = ev.y - v.height * 0.5f
                    if (hypot(ax, ay) > v.width * 0.2f) gameView.setAim(ax, ay) else gameView.clearAim()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    gameView.attackUp()
                    gameView.clearAim()
                }
            }
            true
        }
        findViewById<View>(R.id.btnJump).setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    gameView.jumpDown()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    gameView.jumpUp()
                }
            }
            true
        }
        btnWeapon.setOnClickListener { gameView.switchWeapon() }
        btnSkill1.setOnClickListener { gameView.skill(1) }
        btnSkill2.setOnClickListener { gameView.skill(2) }
        btnSkill3.setOnClickListener { gameView.skill(3) }
        btnSkill4.setOnClickListener { gameView.skill(4) }
        btnSkill5.setOnClickListener { gameView.skill(5) }
        gameView.onWeapon = { w ->
            runOnUiThread { btnAttack.setImageResource(if (w == 1) R.drawable.hd_ic_spear else R.drawable.hd_ic_sword) }
        }
        findViewById<View>(R.id.btnRoll).setOnClickListener {
            gameView.roll()
        }

        val chargeRing = findViewById<ChargeRingView>(R.id.chargeRing)
        gameView.onCharge = { chargeRing.setLevel(it) }

        gameView.onGameOver = { vitoria ->
            runOnUiThread {
                overlayFim.visibility = View.VISIBLE
                tvFim.text = if (vitoria) getString(R.string.vitoria) else getString(R.string.derrota)
                tvFim.setTextColor(if (vitoria) 0xFFF2B63D.toInt() else 0xFFE53935.toInt())
            }
        }

        findViewById<View>(R.id.btnTentar).setOnClickListener {
            overlayFim.visibility = View.GONE
            startGame(selectedClasse, currentCustom)
        }
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            leaveGame()
        }
        findViewById<View>(R.id.btnSairJogo).setOnClickListener {
            leaveGame()
        }

        // Atualiza HUD
        gameView.post(object : Runnable {
            override fun run() {
                val p = gameView.getPlayer()
                if (p != null && gameContainer.visibility == View.VISIBLE) {
                    hpBar.max = p.maxHp.toInt()
                    hpBar.progress = p.hp.toInt().coerceAtLeast(0)
                    setOrbText(tvHpNum, p.hp.toInt().coerceAtLeast(0), p.maxHp.toInt())
                    if (p.maxMpH > 0f) {
                        mpRow.visibility = View.VISIBLE
                        mpBar.max = p.maxMpH.toInt()
                        mpBar.progress = p.mp.toInt().coerceAtLeast(0)
                        setOrbText(tvMpNum, p.mp.toInt().coerceAtLeast(0), p.maxMpH.toInt())
                    } else {
                        mpRow.visibility = View.GONE
                        tvMpNum.text = ""
                    }
                    tvInimigos.text = gameView.aliveEnemies().toString()
                    if (p.classe == Classe.ICHIGO) {
                        if (p.hollow != ichiHolShown) {
                            ichiHolShown = p.hollow
                            applyIchiForm(p.hollow)
                        }
                        val label = if (p.hollow) "Vasto Lorde " + Math.ceil(p.hollowT.toDouble()).toInt() + "s" else "Ichigo"
                        if (tvClasseHud.text.toString() != label) tvClasseHud.text = label
                        val nowMs = System.currentTimeMillis()
                        val btns = arrayOf(btnSkill1, btnSkill2, btnSkill3, btnSkill4, btnSkill5)
                        val tvs = arrayOf(tvSkill1Cd, tvSkill2Cd, tvSkill3Cd, tvSkill4Cd, tvSkill5Cd)
                        var ping = false
                        btnRoll.alpha = if (nowMs - p.lastIchiDash < Player.ICHI_DASH_CD) 0.45f else 1f
                        for (i in 0..4) {
                            val c = p.skillCd(i + 1, nowMs)
                            if (prevCd[i] > 0f && c <= 0f) ping = true
                            prevCd[i] = c
                            val semMana = c <= 0f && p.mp < p.ichiCost(i + 1)
                            tvs[i].text = if (c > 0f) Math.ceil(c.toDouble()).toInt().toString() else ""
                            btns[i].alpha = if (c > 0f || semMana) 0.45f else 1f
                        }
                        if (ping) Sfx.play("ui_ping", 0.6f)
                    }
                    if (p.classe == Classe.PIKACHU) {
                        val nowMs = System.currentTimeMillis()
                        val btns = arrayOf(btnSkill1, btnSkill2, btnSkill3, btnSkill4)
                        val tvs = arrayOf(tvSkill1Cd, tvSkill2Cd, tvSkill3Cd, tvSkill4Cd)
                        var ping = false
                        for (i in 0..3) {
                            val c = p.skillCd(i + 1, nowMs)
                            if (prevCd[i] > 0f && c <= 0f) ping = true
                            prevCd[i] = c
                            val semMana = c <= 0f && p.mp < Player.PIKA_COST[i + 1]
                            tvs[i].text = if (c > 0f) Math.ceil(c.toDouble()).toInt().toString() else ""
                            btns[i].alpha = if (c > 0f || semMana) 0.45f else 1f
                        }
                        if (ping) Sfx.play("ui_ping", 0.6f)
                    }
                    if (p.classe == Classe.ARQUEIRO || p.classe == Classe.NINJA) {
                        val nowMs = System.currentTimeMillis()
                        val ninja = p.classe == Classe.NINJA
                        val btns = arrayOf(btnSkill1, btnSkill2, btnSkill3, btnSkill4)
                        val tvs = arrayOf(tvSkill1Cd, tvSkill2Cd, tvSkill3Cd, tvSkill4Cd)
                        var ping = false
                        for (i in 0 until (if (ninja) 3 else 2)) {
                            val c = p.skillCd(i + 1, nowMs)
                            if (prevCd[i] > 0f && c <= 0f) ping = true
                            prevCd[i] = c
                            if (ninja && i == 1) {
                                // Sombra Viva fica no botão de rolar: anel com o tempo que falta da sombra,
                                // ou a recarga; com a sombra no ar o botão vira "trocar de lugar"
                                val left = gameView.shadowLeft()
                                val swap = gameView.swapReady()
                                if (left > 0f) shadowTimer.setShadow(left, NinjaShadow.LIFE) else shadowTimer.setIdle(c)
                                val semMana = left <= 0f && c <= 0f && p.mp < Player.NINJA_COST[2]
                                btnRoll.alpha = if ((c > 0f && !swap) || semMana) 0.45f else 1f
                                continue
                            }
                            val semMana = ninja && c <= 0f && p.mp < Player.NINJA_COST[i + 1]
                            tvs[i].text = if (c > 0f) Math.ceil(c.toDouble()).toInt().toString() else ""
                            btns[i].alpha = if (c > 0f || semMana) 0.45f else 1f
                        }
                        if (ping) Sfx.play("ui_ping", 0.6f)
                    }
                }
                gameView.postDelayed(this, 100)
            }
        })
    }

    private fun setupConfig() {
        findViewById<View>(R.id.btnVerificarUpdate).setOnClickListener {
            tvUpdateStatus.text = getString(R.string.procurando)
            Updater.check(this, silent = false, statusCallback = { msg ->
                tvUpdateStatus.text = msg
            })
        }
        findViewById<View>(R.id.btnVoltarConfig).setOnClickListener {
            show(menuLayout)
        }
        val gold = ColorStateList.valueOf(0xFFF2B63D.toInt())
        val sbSfx = findViewById<SeekBar>(R.id.sbSfx)
        val sbMusic = findViewById<SeekBar>(R.id.sbMusic)
        for (sb in listOf(sbSfx, sbMusic)) {
            sb.progressTintList = gold
            sb.thumbTintList = gold
        }
        sbSfx.progress = (Sfx.sfxVol * 100f).toInt()
        sbMusic.progress = (Sfx.musicVol * 100f).toInt()
        sbSfx.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) Sfx.setSfxVolume(p / 100f)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                Sfx.play("sword1", 0.9f)
            }
        })
        sbMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) Sfx.setMusicVolume(p / 100f)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun setupNovidades() {
        findViewById<View>(R.id.btnFecharNovidades).setOnClickListener {
            novidadesLayout.visibility = View.GONE
            val prefs = getSharedPreferences("novidades", Context.MODE_PRIVATE)
            val lastId = readLatestNovidadeId()
            prefs.edit().putString("visto_id", lastId).apply()
        }
    }

    private fun checkNovidades() {
        val latestId = readLatestNovidadeId() ?: return
        val prefs = getSharedPreferences("novidades", Context.MODE_PRIVATE)
        val visto = prefs.getString("visto_id", null)
        if (visto != latestId) {
            val texto = assets.open("novidades.txt").bufferedReader().readText()
            tvNovidades.text = formatNovidades(texto)
            novidadesLayout.visibility = View.VISIBLE
        }
    }

    private fun readLatestNovidadeId(): String? {
        return try {
            val lines = assets.open("novidades.txt").bufferedReader().readLines()
            lines.firstOrNull { it.startsWith("#id ") }?.removePrefix("#id ")?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun formatNovidades(raw: String): String {
        val sb = StringBuilder()
        raw.lines().forEach { line ->
            when {
                line.startsWith("#id ") -> {} // skip
                line.startsWith("#titulo ") -> {
                    if (sb.isNotEmpty()) sb.append("\n\n")
                    sb.append("✦ ").append(line.removePrefix("#titulo ").trim()).append("\n")
                }
                line.startsWith("- ") -> sb.append("  • ").append(line.removePrefix("- ").trim()).append("\n")
                line.isNotBlank() -> sb.append(line).append("\n")
            }
        }
        return sb.toString().trim()
    }

    private fun onClassPicked(classe: Classe) {
        val id = selectedMapId
        var custom: MapData? = null
        if (id != null) {
            custom = MapStore.load(this, id)
            if (custom == null) {
                Toast.makeText(this, "Não consegui abrir esse mapa", Toast.LENGTH_SHORT).show()
                return
            }
        }
        testingFromEditor = false
        startGame(classe, custom)
    }

    /** Número dentro do orbe: "atual/máx", com a fonte diminuindo para sempre caber no círculo. */
    private fun setOrbText(tv: TextView, cur: Int, max: Int) {
        val s = "$cur/$max"
        tv.text = s
        tv.textSize = when {
            s.length <= 5 -> 11f
            s.length == 6 -> 10f
            s.length == 7 -> 9f
            else -> 8f
        }
    }

    private fun startGame(classe: Classe, custom: MapData?) {
        selectedClasse = classe
        currentCustom = custom
        tvClasseHud.text = classe.nome
        tvClasseHud.setTextColor(0xFFF2B63D.toInt())
        ivClasseBadge.setImageResource(
            when (classe) {
                Classe.GUERREIRO -> R.drawable.hd_ic_c_guerreiro
                Classe.MAGO -> R.drawable.hd_ic_c_mago
                Classe.ARQUEIRO -> R.drawable.hd_ic_c_arqueiro
                Classe.BARBARO -> R.drawable.hd_ic_c_barbaro
                Classe.NINJA -> R.drawable.hd_ic_c_ninja
                Classe.PIKACHU -> R.drawable.hd_ic_c_pikachu
                Classe.ICHIGO -> R.drawable.hd_ic_c_ichigo
                Classe.LUFFY -> R.drawable.hd_ic_c_luffy
            }
        )
        btnAttack.setImageResource(if (classe == Classe.PIKACHU) R.drawable.hd_ic_pika_shock else R.drawable.hd_ic_sword)
        btnWeapon.visibility = if (classe == Classe.GUERREIRO) View.VISIBLE else View.GONE
        val skillVis = if (classe == Classe.ARQUEIRO || classe == Classe.NINJA || classe == Classe.PIKACHU || classe == Classe.ICHIGO) View.VISIBLE else View.GONE
        val skill2Vis = if (classe == Classe.ARQUEIRO || classe == Classe.PIKACHU || classe == Classe.ICHIGO) View.VISIBLE else View.GONE
        val skill3Vis = if (classe == Classe.NINJA || classe == Classe.PIKACHU || classe == Classe.ICHIGO) View.VISIBLE else View.GONE
        // ninja: Sombra Viva toma o lugar do rolamento
        if (classe == Classe.NINJA) {
            btnRoll.setImageResource(R.drawable.hd_ic_nin_w)
            btnRoll.contentDescription = "Sombra Viva"
        } else if (classe == Classe.PIKACHU) {
            btnRoll.setImageResource(R.drawable.hd_ic_pika_quick)
            btnRoll.contentDescription = "Ataque Rápido"
        } else if (classe == Classe.ICHIGO) {
            btnRoll.setImageResource(R.drawable.hd_ic_ichi_shunpo)
            btnRoll.contentDescription = "Shunpo"
        } else {
            btnRoll.setImageResource(R.drawable.hd_ic_roll)
            btnRoll.contentDescription = "Rolar"
        }
        btnRoll.alpha = 1f
        shadowTimer.clear()
        if (classe == Classe.NINJA) {
            btnSkill1.setImageResource(R.drawable.hd_ic_nin_q)
            btnSkill1.contentDescription = "Shuriken Navalha"
        } else if (classe == Classe.ICHIGO) {
            ichiHolShown = false
            applyIchiForm(false)
        } else if (classe == Classe.PIKACHU) {
            btnSkill1.setImageResource(R.drawable.hd_ic_pika_bolt)
            btnSkill2.setImageResource(R.drawable.hd_ic_pika_tail)
            btnSkill3.setImageResource(R.drawable.hd_ic_pika_volt)
            btnSkill4.setImageResource(R.drawable.hd_ic_pika_thunder)
            btnSkill1.contentDescription = "Trovoada"
            btnSkill2.contentDescription = "Cauda de Ferro"
            btnSkill3.contentDescription = "Investida Trovão"
            btnSkill4.contentDescription = "Trovão"
        } else {
            btnSkill1.setImageResource(R.drawable.hd_ic_rain)
            btnSkill2.setImageResource(R.drawable.hd_ic_thunder)
            btnSkill1.contentDescription = "Chuva de Flechas"
            btnSkill2.contentDescription = "Flecha Trovão"
        }
        for (i in prevCd.indices) prevCd[i] = 0f
        btnSkill1.alpha = 1f
        btnSkill2.alpha = 1f
        btnSkill3.alpha = 1f
        btnSkill4.alpha = 1f
        btnSkill5.alpha = 1f
        btnSkill1.visibility = skillVis
        btnSkill2.visibility = skill2Vis
        tvSkill1Cd.visibility = skillVis
        tvSkill2Cd.visibility = skill2Vis
        btnSkill3.visibility = skill3Vis
        tvSkill3Cd.visibility = skill3Vis
        // ultimate (R) do ninja removida por enquanto; o Pikachu usa o 4º botão para o Trovão
        val skill4Vis = if (classe == Classe.PIKACHU || classe == Classe.ICHIGO) View.VISIBLE else View.GONE
        btnSkill4.visibility = skill4Vis
        tvSkill4Cd.visibility = skill4Vis
        val skill5Vis = if (classe == Classe.ICHIGO) View.VISIBLE else View.GONE
        btnSkill5.visibility = skill5Vis
        tvSkill5Cd.visibility = skill5Vis
        ivManaSlot.alpha = if (classe.maxMp > 0f) 1f else 0.4f
        overlayFim.visibility = View.GONE
        show(gameContainer)
        gameView.startGame(classe, custom)
    }

    /** Ichigo: troca os ícones e nomes dos botões de skill conforme a forma (Bankai ou Vasto Lorde). */
    private fun applyIchiForm(hollow: Boolean) {
        if (hollow) {
            btnSkill1.setImageResource(R.drawable.hd_ic_ichi_cero)
            btnSkill2.setImageResource(R.drawable.hd_ic_ichi_garras)
            btnSkill3.setImageResource(R.drawable.hd_ic_ichi_grito)
            btnSkill4.setImageResource(R.drawable.hd_ic_ichi_oscuras)
            btnSkill5.setImageResource(R.drawable.hd_ic_ichi_mugetsu)
            btnRoll.setImageResource(R.drawable.hd_ic_ichi_sonido)
            btnSkill1.contentDescription = "Cero"
            btnSkill2.contentDescription = "Garras Rasgantes"
            btnSkill3.contentDescription = "Grito Hollow"
            btnSkill4.contentDescription = "Cero Oscuras"
            btnSkill5.contentDescription = "Mugetsu"
            btnRoll.contentDescription = "Sonído"
            tvClasseHud.setTextColor(0xFFFF4B5C.toInt())
        } else {
            btnSkill1.setImageResource(R.drawable.hd_ic_ichi_getsuga)
            btnSkill2.setImageResource(R.drawable.hd_ic_ichi_jujisho)
            btnSkill3.setImageResource(R.drawable.hd_ic_ichi_spin)
            btnSkill4.setImageResource(R.drawable.hd_ic_ichi_lunar)
            btnSkill5.setImageResource(R.drawable.hd_ic_ichi_hollow)
            btnRoll.setImageResource(R.drawable.hd_ic_ichi_shunpo)
            btnSkill1.contentDescription = "Getsuga Tenshou"
            btnSkill2.contentDescription = "Getsuga Jujisho"
            btnSkill3.contentDescription = "Redemoinho de Getsuga"
            btnSkill4.contentDescription = "Golpe Lunar"
            btnSkill5.contentDescription = "Vasto Lorde"
            btnRoll.contentDescription = "Shunpo"
            tvClasseHud.setTextColor(0xFFF2B63D.toInt())
        }
    }

    private fun leaveGame() {
        gameView.stopGame()
        overlayFim.visibility = View.GONE
        if (testingFromEditor && editingMap != null) {
            show(editorLayout)
        } else {
            testingFromEditor = false
            show(menuLayout)
        }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Sfx.play("ui_back", 0.8f)
                when {
                    novidadesLayout.visibility == View.VISIBLE -> findViewById<View>(R.id.btnFecharNovidades).performClick()
                    editorLayout.visibility == View.VISIBLE -> exitEditor()
                    gameContainer.visibility == View.VISIBLE -> leaveGame()
                    classLayout.visibility == View.VISIBLE -> openMapList(false)
                    mapListLayout.visibility == View.VISIBLE || configLayout.visibility == View.VISIBLE -> show(menuLayout)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // ───────────────────────── lista de mapas ─────────────────────────

    private fun setupMapList() {
        rvMaps.layoutManager = LinearLayoutManager(this)
        findViewById<View>(R.id.btnVoltarMapas).setOnClickListener { show(menuLayout) }
        btnNovoMapa.setOnClickListener { promptNewMap() }
    }

    private fun openMapList(edit: Boolean) {
        mapListEdit = edit
        tvMapListTitle.text = if (edit) "Editor de mapas" else "Escolha o mapa"
        btnNovoMapa.visibility = if (edit) View.VISIBLE else View.GONE
        refreshMapList()
        show(mapListLayout)
    }

    private fun refreshMapList() {
        val maps = MapStore.list(this)
        val items = ArrayList<ListItem>()
        if (!mapListEdit) {
            items.add(ListItem(null, "Mundo aberto", "Vilarejo, floresta e ruínas do chefe (gerado a cada partida)", 0xFF2E7D32.toInt()))
        }
        for (m in maps) items.add(ListItem(m.id, m.name, "${m.w}×${m.h} tiles", 0xFFFFC107.toInt()))
        tvMapListHint.text = when {
            mapListEdit && maps.isEmpty() -> "Você ainda não tem mapas. Toque em \"+ Novo mapa\"."
            mapListEdit -> "Toque para editar • segure para renomear, duplicar ou excluir"
            else -> "Jogue o mundo aberto ou um mapa criado por você"
        }
        rvMaps.adapter = MapListAdapter(items, { item -> onMapPicked(item) }, { item ->
            if (mapListEdit && item.id != null) showMapOptions(item)
        })
    }

    private fun onMapPicked(item: ListItem) {
        if (mapListEdit) {
            val id = item.id ?: return
            val m = MapStore.load(this, id)
            if (m == null) {
                Toast.makeText(this, "Não consegui abrir esse mapa", Toast.LENGTH_SHORT).show()
                return
            }
            openEditor(m)
        } else {
            selectedMapId = item.id
            show(classLayout)
        }
    }

    private fun showMapOptions(item: ListItem) {
        val id = item.id ?: return
        AlertDialog.Builder(this)
            .setTitle(item.nome)
            .setItems(arrayOf("Renomear", "Duplicar", "Excluir")) { _, which ->
                when (which) {
                    0 -> promptName("Renomear mapa", item.nome) { name ->
                        val m = MapStore.load(this, id)
                        if (m != null) {
                            m.name = name
                            MapStore.save(this, m)
                        }
                        refreshMapList()
                    }
                    1 -> {
                        MapStore.duplicate(this, id)
                        refreshMapList()
                    }
                    else -> AlertDialog.Builder(this)
                        .setTitle("Excluir \"${item.nome}\"?")
                        .setMessage("Isso não pode ser desfeito.")
                        .setPositiveButton("Excluir") { _, _ ->
                            MapStore.delete(this, id)
                            refreshMapList()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            .show()
    }

    private fun promptName(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.isSingleLine = true
        input.setText(initial)
        input.selectAll()
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(input)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("OK") { _, _ ->
                val n = input.text.toString().trim()
                onOk(if (n.isEmpty()) "Meu mapa" else n.take(40))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptNewMap() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.isSingleLine = true
        input.setText("Meu mapa")
        input.selectAll()
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(input)
        fun create(w: Int, h: Int) {
            val n = input.text.toString().trim().ifEmpty { "Meu mapa" }.take(40)
            val m = MapData(n, w, h)
            m.fillDefault()
            MapStore.save(this, m)
            openEditor(m)
        }
        AlertDialog.Builder(this)
            .setTitle("Novo mapa")
            .setMessage("Dê um nome e escolha o tamanho:")
            .setView(box)
            .setNegativeButton("Pequeno 24×18") { _, _ -> create(24, 18) }
            .setNeutralButton("Médio 40×30") { _, _ -> create(40, 30) }
            .setPositiveButton("Grande 64×48") { _, _ -> create(64, 48) }
            .show()
    }

    // ───────────────────────── editor ─────────────────────────

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun styleChip(tv: TextView, selected: Boolean) {
        tv.background = Ui.chipBg(this, selected)
        tv.setTextColor(if (selected) Ui.INK else 0xFFF1E6CC.toInt())
    }

    private fun chip(text: String, w: Int, h: Int, size: Float, pad: Int = 8, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = size
        tv.gravity = Gravity.CENTER
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setPadding(dp(pad), 0, dp(pad), 0)
        tv.isClickable = true
        tv.setOnClickListener { onClick() }
        styleChip(tv, false)
        val lp = LinearLayout.LayoutParams(if (w > 0) dp(w) else ViewGroup.LayoutParams.WRAP_CONTENT, dp(h))
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        tv.layoutParams = lp
        return tv
    }

    private fun setupEditor() {
        editorView.onHistoryChanged = { refreshHistory() }
        editorView.onMessage = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }

        // barra superior
        edTopBar.background = Ui.dock(this, 0f)
        findViewById<View>(R.id.edDock).background = Ui.dock(this, 0f)
        edTopBar.addView(chip("‹ Sair", 64, 36, 13f) { exitEditor() })
        tvEdName = TextView(this)
        tvEdName.setTextColor(0xFFFFE9A8.toInt())
        tvEdName.background = Ui.round(this, 0x55000000, 0x33000000, 0xFF5C503C.toInt(), 10f, 1f)
        tvEdName.setPadding(dp(12), 0, dp(12), 0)
        tvEdName.textSize = 14f
        tvEdName.gravity = Gravity.CENTER
        tvEdName.maxLines = 1
        tvEdName.ellipsize = TextUtils.TruncateAt.END
        tvEdName.setOnClickListener {
            val m = editingMap ?: return@setOnClickListener
            promptName("Nome do mapa", m.name) { n ->
                m.name = n
                tvEdName.text = "✎ " + n
                editorView.dirty = true
            }
        }
        val nameLp = LinearLayout.LayoutParams(0, dp(34), 1f)
        nameLp.setMargins(dp(6), 0, dp(6), 0)
        edTopBar.addView(tvEdName, nameLp)
        btnUndo = chip("↶", 40, 36, 17f) { editorView.undo() }
        btnRedo = chip("↷", 40, 36, 17f) { editorView.redo() }
        btnGrid = chip("▦ Grade", 0, 36, 11f) {
            editorView.showGrid = !editorView.showGrid
            refreshHistory()
        }
        edTopBar.addView(btnUndo)
        edTopBar.addView(btnRedo)
        edTopBar.addView(btnGrid)
        val testBtn = chip("▶ Testar", 0, 36, 13f) { testMap() }
        testBtn.background = Ui.chipBg(this, false, 0xFF3F8A3F.toInt())
        testBtn.setTextColor(0xFFFFFFFF.toInt())
        edTopBar.addView(testBtn)

        // ferramentas agrupadas, em duas colunas
        edTools.orientation = LinearLayout.VERTICAL
        edTools.background = Ui.dock(this, 10f)
        edTools.setPadding(dp(4), dp(4), dp(4), dp(4))
        fun header(t: String) {
            val h = TextView(this)
            h.text = t
            h.textSize = 8.5f
            h.setTextColor(Ui.GOLD)
            h.gravity = Gravity.CENTER
            h.setPadding(0, dp(3), 0, dp(1))
            edTools.addView(h, LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        fun group(title: String, views: List<View>) {
            header(title)
            var i = 0
            while (i < views.size) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.addView(views[i])
                if (i + 1 < views.size) row.addView(views[i + 1])
                edTools.addView(row)
                i += 2
            }
        }
        fun toolBtn(t: Int, icon: String, name: String): View {
            val c = chip(icon + "\n" + name, 54, 40, 9.5f, 2) {
                editorView.tool = t
                refreshTools()
            }
            toolChips.add(t to c)
            return c
        }
        fun actBtn(icon: String, name: String, f: () -> Unit): View = chip(icon + "\n" + name, 54, 40, 9.5f, 2) { f() }
        lateinit var symChip: TextView
        val symNames = listOf("não", "↔", "↕", "✛")
        symChip = chip("◧\nSimetria " + symNames[0], 54, 40, 8.5f, 2) {
            editorView.symmetry = (editorView.symmetry + 1) % 4
            symChip.text = "◧\nSimetria " + symNames[editorView.symmetry]
            styleChip(symChip, editorView.symmetry != 0)
        }
        group("DESENHAR", listOf(
            toolBtn(MapEditorView.T_BRUSH, "✎", "Pincel"),
            toolBtn(MapEditorView.T_LINE, "／", "Linha"),
            toolBtn(MapEditorView.T_RECT, "▭", "Retâng."),
            toolBtn(MapEditorView.T_OVAL, "◯", "Oval"),
            toolBtn(MapEditorView.T_FRAME, "▢", "Contorno"),
            toolBtn(MapEditorView.T_FILL, "▣", "Balde"),
            toolBtn(MapEditorView.T_SPRAY, "∴", "Espalhar")
        ))
        group("EDITAR", listOf(
            toolBtn(MapEditorView.T_ERASE, "⌫", "Borracha"),
            toolBtn(MapEditorView.T_PICK, "◎", "Gotas"),
            toolBtn(MapEditorView.T_REPLACE, "⇄", "Trocar"),
            symChip
        ))
        group("ÁREA", listOf(
            toolBtn(MapEditorView.T_COPY, "❐", "Copiar"),
            toolBtn(MapEditorView.T_CUT, "✂", "Recortar"),
            actBtn("↔", "Espelhar") { editorView.transformClip(1) },
            actBtn("↕", "Espelhar") { editorView.transformClip(2) },
            actBtn("⟳", "Girar 90°") { editorView.transformClip(3) }
        ))
        lateinit var capChip: TextView
        capChip = chip("⌂\nTelhado", 54, 40, 9f, 2) {
            MapEditorView.hideCaps = !MapEditorView.hideCaps
            styleChip(capChip, MapEditorView.hideCaps)
            capChip.text = if (MapEditorView.hideCaps) "⌂\nOculto" else "⌂\nTelhado"
            editorView.invalidate()
        }
        MapEditorView.hideCaps = false
        group("VISTA", listOf(toolBtn(MapEditorView.T_PAN, "✥", "Tela"), capChip))
        editorView.onPick = {
            refreshTabs()
            refreshPalette()
            refreshTools()
        }

        // abas em uma fileira com rolagem
        val tabs = listOf(
            MapEditorView.L_FLOOR to "▦ Chão",
            MapEditorView.L_WALL to "▮ Paredes",
            MapEditorView.L_PROP to "♜ Objetos",
            MapEditorView.L_ENT to "☠ Entidades",
            MapEditorView.L_HOUSE to "⌂ Casas",
            MapEditorView.L_TERRAIN to "▲ Relevo",
            MapEditorView.L_BRIDGE to "≈ Pontes",
            MapEditorView.L_BUILD to "⚒ Obra"
        )
        for ((l, name) in tabs) {
            val c = chip(name, 0, 28, 11f, 10) {
                editorView.layer = l
                if (l == MapEditorView.L_BUILD && (editorView.tool == MapEditorView.T_BRUSH || editorView.tool == MapEditorView.T_LINE || editorView.tool == MapEditorView.T_FILL)) {
                    editorView.tool = MapEditorView.T_RECT
                }
                when (l) {
                    MapEditorView.L_BUILD -> Toast.makeText(this, "Arraste uma área no mapa para construir o cômodo", Toast.LENGTH_SHORT).show()
                    MapEditorView.L_TERRAIN -> Toast.makeText(this, "Rampa: pincel (arraste na direção) ou retângulo (rampa comprida)", Toast.LENGTH_SHORT).show()
                    MapEditorView.L_BRIDGE -> Toast.makeText(this, "Use a Linha para esticar a ponte sobre a água", Toast.LENGTH_SHORT).show()
                }
                refreshTabs()
                refreshPalette()
                refreshTools()
            }
            tabChips.add(l to c)
            edTabs.addView(c)
        }
    }

    private fun openEditor(m: MapData) {
        editingMap = m
        editorView.setMap(m)
        editorView.layer = MapEditorView.L_FLOOR
        editorView.tool = MapEditorView.T_BRUSH
        tvEdName.text = "✎ " + m.name
        refreshTabs()
        refreshPalette()
        refreshTools()
        refreshHistory()
        show(editorLayout)
    }

    private fun saveEditing() {
        val m = editingMap ?: return
        MapStore.save(this, m)
        editorView.dirty = false
    }

    private fun exitEditor() {
        saveEditing()
        editingMap = null
        Toast.makeText(this, "Mapa salvo", Toast.LENGTH_SHORT).show()
        openMapList(true)
    }

    private fun testMap() {
        val m = editingMap ?: return
        saveEditing()
        val copy = MapData.fromJson(m.id, m.toJson())
        testingFromEditor = true
        startGame(selectedClasse, copy)
    }

    private fun refreshTools() {
        for ((t, c) in toolChips) styleChip(c, t == editorView.tool)
    }

    private fun refreshTabs() {
        for ((l, c) in tabChips) styleChip(c, l == editorView.layer)
    }

    private fun refreshHistory() {
        btnUndo.alpha = if (editorView.canUndo) 1f else 0.35f
        btnRedo.alpha = if (editorView.canRedo) 1f else 0.35f
        styleChip(btnGrid, editorView.showGrid)
    }

    private fun refreshPalette() {
        edPalette.removeAllViews()
        paletteViews.clear()
        val layer = editorView.layer
        val items: List<Pair<Int, String>> = when (layer) {
            MapEditorView.L_FLOOR -> MT.floors.map { it.id to it.name }
            MapEditorView.L_WALL -> MT.walls.map { it.id to it.name }
            MapEditorView.L_PROP -> MT.props.filter { propCatIdx == 0 || MT.propCat(it.id) == propCatIdx }.map { it.id to it.name }
            MapEditorView.L_HOUSE -> Houses.list.map { it.id to it.name }
            MapEditorView.L_TERRAIN -> MapEditorView.terrainItems
            MapEditorView.L_BRIDGE -> MT.bridgeNames
            MapEditorView.L_BUILD -> emptyList()
            else -> (0..3).map { it to MapEditorView.entNames[it] }
        }
        if (layer == MapEditorView.L_PROP) {
            val ctrl = LinearLayout(this)
            ctrl.orientation = LinearLayout.VERTICAL
            ctrl.gravity = Gravity.CENTER_VERTICAL
            val cc = ctrlChip(112, { "Tipo: " + MT.propCats[propCatIdx] }) {
                propCatIdx = (propCatIdx + 1) % MT.propCats.size
                refreshPalette()
            }
            ctrl.addView(cc)
            val faceNames = listOf("Sul ▼ (frente)", "Leste ▶", "Norte ▲ (costas)", "Oeste ◀")
            ctrl.addView(ctrlChip(112, { "Virado: " + faceNames[editorView.propFace and 3] }) {
                editorView.propFace = (editorView.propFace + 1) and 3
            })
            edPalette.addView(ctrl, LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (layer == MapEditorView.L_TERRAIN) {
            val ctrl = LinearLayout(this)
            ctrl.orientation = LinearLayout.VERTICAL
            ctrl.gravity = Gravity.CENTER_VERTICAL
            ctrl.addView(ctrlChip(112, { "Colina: altura " + editorView.hillPeak }) {
                editorView.hillPeak = editorView.hillPeak % 4 + 1
            })
            edPalette.addView(ctrl, LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (layer == MapEditorView.L_BUILD) addBuildControls()
        if (layer == MapEditorView.L_WALL) {
            // controles de empilhar: altura e material dos andares de cima
            val ctrl = LinearLayout(this)
            ctrl.orientation = LinearLayout.VERTICAL
            ctrl.gravity = Gravity.CENTER_VERTICAL
            val capIds = MT.capChoices
            lateinit var hChip: TextView
            lateinit var cChip: TextView
            fun upd() {
                hChip.text = "Andares: " + editorView.wallHeight
                val cn = if (editorView.wallCap == 0) "igual" else (MT.wallDef(editorView.wallCap)?.name ?: "?")
                cChip.text = "Topo: " + cn
                cChip.alpha = if (editorView.wallHeight > 1) 1f else 0.4f
            }
            hChip = chip("", 104, 32, 10f) {
                editorView.wallHeight = editorView.wallHeight % 4 + 1
                upd()
            }
            cChip = chip("", 104, 32, 10f) {
                val i = capIds.indexOf(editorView.wallCap)
                editorView.wallCap = capIds[(i + 1) % capIds.size]
                if (editorView.wallHeight < 2) editorView.wallHeight = 2
                upd()
            }
            styleChip(hChip, true)
            styleChip(cChip, true)
            upd()
            ctrl.addView(hChip)
            ctrl.addView(cChip)
            edPalette.addView(ctrl, LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val allItems = if (layer == MapEditorView.L_HOUSE && Houses.clipboard != null) items + listOf(Houses.CLIP_ID to "Cópia") else items
        for ((id, name) in allItems) {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.setPadding(dp(3), dp(4), dp(3), dp(3))
            val iv = ImageView(this)
            iv.setImageBitmap(Bitmap.createScaledBitmap(MapEditorView.preview(layer, id), dp(40), dp(40), false))
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 8.5f
            tv.maxLines = 2
            tv.gravity = Gravity.CENTER
            tv.setTextColor(0xFFDDDDDD.toInt())
            box.addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)))
            box.addView(tv, LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT))
            box.setOnClickListener {
                editorView.selected[layer] = id
                refreshPaletteSelection()
            }
            val lp = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(3), 0, dp(3), 0)
            edPalette.addView(box, lp)
            paletteViews.add(id to box)
        }
        refreshPaletteSelection()
    }

    private var propCatIdx = 0


    /** Botão de controle que muda de valor a cada toque; o texto vem de label(). */
    private fun ctrlChip(w: Int, label: () -> String, onTap: () -> Unit): TextView {
        lateinit var c: TextView
        c = chip(label(), w, 32, 10f, 4) {
            onTap()
            c.text = label()
        }
        styleChip(c, true)
        return c
    }

    /** Controles da aba Obra: arraste uma área no mapa e o cômodo é construído com estas opções. */
    private fun addBuildControls() {
        val o = editorView.buildOpts
        val w = 138
        fun col(a: TextView, b: TextView) {
            val ctrl = LinearLayout(this)
            ctrl.orientation = LinearLayout.VERTICAL
            ctrl.gravity = Gravity.CENTER_VERTICAL
            ctrl.addView(a)
            ctrl.addView(b)
            edPalette.addView(ctrl, LinearLayout.LayoutParams(dp(w + 6), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val capIds = MT.capChoices
        lateinit var capChip: TextView
        lateinit var storeyChip: TextView
        val wallChip = ctrlChip(w, { "Parede: " + (MT.wallDef(o.wall)?.name ?: "?") }) {
            val i = RoomBuilder.wallChoices.indexOf(o.wall)
            o.wall = RoomBuilder.wallChoices[(i + 1) % RoomBuilder.wallChoices.size]
        }
        val floorChip = ctrlChip(w, { "Piso: " + MT.floorDef(o.floor).name }) {

            val i = RoomBuilder.floorChoices.indexOf(o.floor)
            o.floor = RoomBuilder.floorChoices[(i + 1) % RoomBuilder.floorChoices.size]
        }
        storeyChip = ctrlChip(w, { "Andares: " + o.storeys }) {
            o.storeys = o.storeys % 4 + 1
            if (o.storeys < 2) o.cap = 0
            capChip.text = "Topo: " + (if (o.cap == 0) "nenhum" else (MT.wallDef(o.cap)?.name ?: "?"))
        }
        capChip = ctrlChip(w, { "Topo: " + (if (o.cap == 0) "nenhum" else (MT.wallDef(o.cap)?.name ?: "?")) }) {
            val i = capIds.indexOf(o.cap)
            o.cap = capIds[(i + 1) % capIds.size]
            if (o.cap != 0 && o.storeys < 2) o.storeys = 2
            storeyChip.text = "Andares: " + o.storeys
        }
        val doorChip = ctrlChip(w, { "Porta: " + RoomBuilder.doorNames[o.door] }) {
            o.door = (o.door + 1) % RoomBuilder.doorNames.size
        }
        val winChip = ctrlChip(w, { "Janelas: " + RoomBuilder.windowNames[o.window] }) {
            o.window = (o.window + 1) % RoomBuilder.windowNames.size
        }
        val sideChip = ctrlChip(w, { "Lado da porta: " + RoomBuilder.sideNames[o.side] }) {
            o.side = (o.side + 1) % RoomBuilder.sideNames.size
        }
        val furnChip = ctrlChip(w, { "Móveis: " + RoomBuilder.furnNames[o.furn] }) {
            o.furn = (o.furn + 1) % RoomBuilder.furnNames.size
        }
        val kindChip = ctrlChip(w, { "Tipo: " + (if (o.solid) "maciça (cenário)" else "aberta (entrar)") }) {
            o.solid = !o.solid
        }
        val porchChip = ctrlChip(w, { "Entrada: " + (if (o.porch) "caminho e lampião" else "simples") }) {
            o.porch = !o.porch
        }

        col(wallChip, floorChip)
        col(storeyChip, capChip)
        col(doorChip, winChip)
        col(sideChip, furnChip)
        col(kindChip, porchChip)
    }

    private fun refreshPaletteSelection() {
        val cur = editorView.selected[editorView.layer]
        for ((id, v) in paletteViews) v.background = Ui.card(this, id == cur)
    }

    private fun show(target: View) {
        menuLayout.visibility = View.GONE
        classLayout.visibility = View.GONE
        gameContainer.visibility = View.GONE
        configLayout.visibility = View.GONE
        mapListLayout.visibility = View.GONE
        editorLayout.visibility = View.GONE
        // novidades fica por cima se necessário
        target.visibility = View.VISIBLE
        if (target != gameContainer) {
            gameView.stopGame()
        }
        menuBg.setRunning(target != gameContainer && target != editorLayout)
        Sfx.playMusic(if (target == gameContainer) "music_game" else "music_menu")
    }

    override fun onPause() {
        super.onPause()
        Sfx.pauseAll()
        gameView.stopGame()
        if (editorLayout.visibility == View.VISIBLE) saveEditing()
    }

    override fun onResume() {
        super.onResume()
        Sfx.resumeAll()
        // não reinicia automaticamente
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            Sfx.stopLoops()
            Sfx.stopMusic()
        }
    }
}

class ClasseAdapter(
    private val list: List<Classe>,
    private val onClick: (Classe) -> Unit
) : RecyclerView.Adapter<ClasseAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cor: TextView = view.findViewById(R.id.corClasse)
        val nome: TextView = view.findViewById(R.id.tvNomeClasse)
        val desc: TextView = view.findViewById(R.id.tvDescClasse)
        val hp: ProgressBar = view.findViewById(R.id.statHp)
        val dmg: ProgressBar = view.findViewById(R.id.statDmg)
        val spd: ProgressBar = view.findViewById(R.id.statSpd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_classe_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = list[position]
        val ctx = holder.itemView.context
        holder.nome.text = c.nome
        holder.desc.text = c.descricao
        holder.cor.text = c.nome.take(1).uppercase()
        holder.cor.background = Ui.bevel(ctx, c.cor)
        holder.hp.progress = (c.maxHp / 200f * 100f).toInt().coerceIn(5, 100)
        holder.dmg.progress = (c.danoBase / 32f * 100f).toInt().coerceIn(5, 100)
        holder.spd.progress = (c.velocidade / 3.6f * 100f).toInt().coerceIn(5, 100)
        holder.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = list.size
}

class ListItem(val id: String?, val nome: String, val desc: String, val cor: Int)

class MapListAdapter(
    private val list: List<ListItem>,
    private val onClick: (ListItem) -> Unit,
    private val onLong: (ListItem) -> Unit
) : RecyclerView.Adapter<MapListAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cor: TextView = view.findViewById(R.id.corClasse)
        val nome: TextView = view.findViewById(R.id.tvNomeClasse)
        val desc: TextView = view.findViewById(R.id.tvDescClasse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_classe, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.nome.text = item.nome
        holder.desc.text = item.desc
        holder.cor.text = item.nome.take(1).uppercase()
        holder.cor.background = Ui.bevel(holder.itemView.context, item.cor)
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLong(item)
            true
        }
    }

    override fun getItemCount() = list.size
}
