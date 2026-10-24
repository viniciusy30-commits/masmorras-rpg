package com.viniciusy.masmorras

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
    private lateinit var overlayFim: View
    private lateinit var tvFim: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var tvNovidades: TextView

    private var selectedClasse: Classe = Classe.GUERREIRO
    private var joystickActive = false
    private var joyCenterX = 0f
    private var joyCenterY = 0f
    private var knob: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        menuLayout = findViewById(R.id.menuLayout)
        classLayout = findViewById(R.id.classLayout)
        gameContainer = findViewById(R.id.gameContainer)
        configLayout = findViewById(R.id.configLayout)
        novidadesLayout = findViewById(R.id.novidadesLayout)
        gameView = findViewById(R.id.gameView)
        hpBar = findViewById(R.id.hpBar)
        mpBar = findViewById(R.id.mpBar)
        tvClasseHud = findViewById(R.id.tvClasseHud)
        overlayFim = findViewById(R.id.overlayFim)
        tvFim = findViewById(R.id.tvFim)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        tvNovidades = findViewById(R.id.tvNovidades)
        knob = findViewById(R.id.joystickKnob)

        setupMenu()
        setupClassSelect()
        setupGameControls()
        setupConfig()
        setupNovidades()

        // Auto check update
        Updater.autoCheck(this)

        // Mostra novidades se primeira vez após update
        checkNovidades()
    }

    private fun setupMenu() {
        findViewById<MaterialButton>(R.id.btnJogar).setOnClickListener {
            show(classLayout)
        }
        findViewById<MaterialButton>(R.id.btnConfig).setOnClickListener {
            show(configLayout)
        }
    }

    private fun setupClassSelect() {
        val rv = findViewById<RecyclerView>(R.id.rvClasses)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = ClasseAdapter(Classe.values().toList()) { classe ->
            selectedClasse = classe
            startGame(classe)
        }
        findViewById<MaterialButton>(R.id.btnVoltarClasse).setOnClickListener {
            show(menuLayout)
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
                    val maxR = 50f
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

        findViewById<MaterialButton>(R.id.btnAttack).setOnClickListener {
            gameView.tryAttack()
        }

        gameView.onGameOver = { vitoria ->
            runOnUiThread {
                overlayFim.visibility = View.VISIBLE
                tvFim.text = if (vitoria) getString(R.string.vitoria) else getString(R.string.derrota)
                tvFim.setTextColor(if (vitoria) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
            }
        }

        findViewById<MaterialButton>(R.id.btnTentar).setOnClickListener {
            overlayFim.visibility = View.GONE
            startGame(selectedClasse)
        }
        findViewById<MaterialButton>(R.id.btnMenu).setOnClickListener {
            gameView.stopGame()
            overlayFim.visibility = View.GONE
            show(menuLayout)
        }

        // Atualiza HUD
        gameView.post(object : Runnable {
            override fun run() {
                val p = gameView.getPlayer()
                if (p != null && gameContainer.visibility == View.VISIBLE) {
                    hpBar.max = p.maxHp.toInt()
                    hpBar.progress = p.hp.toInt().coerceAtLeast(0)
                    if (p.classe.maxMp > 0f) {
                        mpBar.visibility = View.VISIBLE
                        mpBar.max = p.classe.maxMp.toInt()
                        mpBar.progress = p.mp.toInt().coerceAtLeast(0)
                    } else {
                        mpBar.visibility = View.GONE
                    }
                }
                gameView.postDelayed(this, 100)
            }
        })
    }

    private fun setupConfig() {
        findViewById<MaterialButton>(R.id.btnVerificarUpdate).setOnClickListener {
            tvUpdateStatus.text = getString(R.string.procurando)
            Updater.check(this, silent = false, statusCallback = { msg ->
                tvUpdateStatus.text = msg
            })
        }
        findViewById<MaterialButton>(R.id.btnVoltarConfig).setOnClickListener {
            show(menuLayout)
        }
    }

    private fun setupNovidades() {
        findViewById<MaterialButton>(R.id.btnFecharNovidades).setOnClickListener {
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

    private fun startGame(classe: Classe) {
        selectedClasse = classe
        tvClasseHud.text = classe.nome
        tvClasseHud.setTextColor(classe.cor)
        overlayFim.visibility = View.GONE
        show(gameContainer)
        gameView.startGame(classe)
    }

    private fun show(target: View) {
        menuLayout.visibility = View.GONE
        classLayout.visibility = View.GONE
        gameContainer.visibility = View.GONE
        configLayout.visibility = View.GONE
        // novidades fica por cima se necessário
        target.visibility = View.VISIBLE
        if (target != gameContainer) {
            gameView.stopGame()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.stopGame()
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
        val cor: View = view.findViewById(R.id.corClasse)
        val nome: TextView = view.findViewById(R.id.tvNomeClasse)
        val desc: TextView = view.findViewById(R.id.tvDescClasse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_classe, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = list[position]
        holder.nome.text = c.nome
        holder.desc.text = c.descricao
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(c.cor)
        holder.cor.background = bg
        holder.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = list.size
}
