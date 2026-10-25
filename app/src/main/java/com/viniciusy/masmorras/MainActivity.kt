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
    private lateinit var hudLayer: View
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

        // Auto check update
        Updater.autoCheck(this)

        // Mostra novidades se primeira vez após update
        checkNovidades()
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
            onClassPicked(classe)
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
        findViewById<View>(R.id.btnJump).setOnClickListener {
            gameView.jump()
        }
        btnWeapon.setOnClickListener { gameView.switchWeapon() }
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
                    if (p.classe.maxMp > 0f) {
                        mpRow.visibility = View.VISIBLE
                        mpBar.max = p.classe.maxMp.toInt()
                        mpBar.progress = p.mp.toInt().coerceAtLeast(0)
                        setOrbText(tvMpNum, p.mp.toInt().coerceAtLeast(0), p.classe.maxMp.toInt())
                    } else {
                        mpRow.visibility = View.GONE
                        tvMpNum.text = ""
                    }
                    tvInimigos.text = gameView.aliveEnemies().toString()
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
            }
        )
        btnAttack.setImageResource(R.drawable.hd_ic_sword)
        btnWeapon.visibility = if (classe == Classe.GUERREIRO) View.VISIBLE else View.GONE
        ivManaSlot.alpha = if (classe.maxMp > 0f) 1f else 0.4f
        overlayFim.visibility = View.GONE
        show(gameContainer)
        gameView.startGame(classe, custom)
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
        tv.background = Ui.button(this, if (selected) Ui.GOLD else Ui.STONE)
        tv.setTextColor(if (selected) Ui.INK else 0xFFFFFFFF.toInt())
    }

    private fun chip(text: String, w: Int, h: Int, size: Float, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = size
        tv.gravity = Gravity.CENTER
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setPadding(dp(8), 0, dp(8), 0)
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
        edTopBar.addView(chip("‹ Salvar e sair", 0, 36, 13f) { exitEditor() })
        tvEdName = TextView(this)
        tvEdName.setTextColor(0xFFFFFFFF.toInt())
        tvEdName.textSize = 14f
        tvEdName.gravity = Gravity.CENTER
        tvEdName.maxLines = 1
        tvEdName.ellipsize = TextUtils.TruncateAt.END
        tvEdName.setOnClickListener {
            val m = editingMap ?: return@setOnClickListener
            promptName("Nome do mapa", m.name) { n ->
                m.name = n
                tvEdName.text = n
                editorView.dirty = true
            }
        }
        edTopBar.addView(tvEdName, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        btnUndo = chip("↶", 40, 36, 16f) { editorView.undo() }
        btnRedo = chip("↷", 40, 36, 16f) { editorView.redo() }
        btnGrid = chip("Grade", 0, 36, 12f) {
            editorView.showGrid = !editorView.showGrid
            refreshHistory()
        }
        edTopBar.addView(btnUndo)
        edTopBar.addView(btnRedo)
        edTopBar.addView(btnGrid)
        edTopBar.addView(chip("▶ Testar", 0, 36, 13f) { testMap() })

        // ferramentas
        val tools = listOf(
            MapEditorView.T_BRUSH to "Pincel",
            MapEditorView.T_RECT to "Retâng.",
            MapEditorView.T_FILL to "Balde",
            MapEditorView.T_ERASE to "Borracha",
            MapEditorView.T_PAN to "Mover"
        )
        for ((t, name) in tools) {
            val c = chip(name, 68, 34, 11f) {
                editorView.tool = t
                refreshTools()
            }
            toolChips.add(t to c)
            edTools.addView(c)
        }

        // abas (2x2)
        val tabs = listOf(
            MapEditorView.L_FLOOR to "Chão",
            MapEditorView.L_WALL to "Paredes",
            MapEditorView.L_PROP to "Objetos",
            MapEditorView.L_ENT to "Entidades"
        )
        for (rowIdx in 0..1) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (colIdx in 0..1) {
                val (l, name) = tabs[rowIdx * 2 + colIdx]
                val c = chip(name, 74, 32, 11f) {
                    editorView.layer = l
                    refreshTabs()
                    refreshPalette()
                }
                tabChips.add(l to c)
                row.addView(c)
            }
            edTabs.addView(row)
        }
    }

    private fun openEditor(m: MapData) {
        editingMap = m
        editorView.setMap(m)
        editorView.layer = MapEditorView.L_FLOOR
        editorView.tool = MapEditorView.T_BRUSH
        tvEdName.text = m.name
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
            MapEditorView.L_PROP -> MT.props.map { it.id to it.name }
            else -> (0..3).map { it to MapEditorView.entNames[it] }
        }
        for ((id, name) in items) {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.setPadding(dp(3), dp(3), dp(3), dp(3))
            val iv = ImageView(this)
            iv.setImageBitmap(Bitmap.createScaledBitmap(MapEditorView.preview(layer, id), dp(40), dp(40), false))
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 9f
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
            lp.setMargins(dp(2), 0, dp(2), 0)
            edPalette.addView(box, lp)
            paletteViews.add(id to box)
        }
        refreshPaletteSelection()
    }

    private fun refreshPaletteSelection() {
        val cur = editorView.selected[editorView.layer]
        for ((id, v) in paletteViews) {
            if (id == cur) {
                val bg = GradientDrawable()
                bg.cornerRadius = dp(2).toFloat()
                bg.setColor(0x44F2B63D)
                bg.setStroke(dp(2), 0xFFF2B63D.toInt())
                v.background = bg
            } else {
                v.background = null
            }
        }
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
    }

    override fun onPause() {
        super.onPause()
        gameView.stopGame()
        if (editorLayout.visibility == View.VISIBLE) saveEditing()
    }

    override fun onResume() {
        super.onResume()
        // não reinicia automaticamente
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
