package com.viniciusy.masmorras

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Tela de criação do herói: raça, atributos (27 pontos) e aparência, com o herói de verdade girando ao lado. */
object HeroCreator {
    private const val OURO = 0xFFF2B63D.toInt()
    private const val FUNDO = 0xFF14110D.toInt()
    private const val PAINEL = 0xFF241E16.toInt()
    private const val TEXTO = 0xFFEFE6D2.toInt()
    private const val TEXTO_FRACO = 0xFFA89B82.toInt()

    private val PELES = intArrayOf(
        0xFFF6DCC0.toInt(), 0xFFF1C27D.toInt(), 0xFFE0A87A.toInt(), 0xFFD99A5E.toInt(), 0xFFB07440.toInt(),
        0xFF8A5530.toInt(), 0xFF5C3A21.toInt(), 0xFF7CA855.toInt(), 0xFF8DB04A.toInt(), 0xFF3F8F5B.toInt(),
        0xFFB5463F.toInt(), 0xFF8E9FB4.toInt(), 0xFFD9A066.toInt(), 0xFF6A5ACD.toInt()
    )
    private val CABELOS = intArrayOf(
        0xFF1B1B1B.toInt(), 0xFF3E2723.toInt(), 0xFF5D4037.toInt(), 0xFF8D5524.toInt(), 0xFFB8421C.toInt(),
        0xFFE0702E.toInt(), 0xFFEAD79B.toInt(), 0xFFEDEFF3.toInt(), 0xFF9E9E9E.toInt(), 0xFF3B6FD4.toInt(),
        0xFF3FA34D.toInt(), 0xFFE87AA8.toInt(), 0xFF8E44AD.toInt(), 0xFFFFFFFF.toInt()
    )
    private val METAIS = intArrayOf(
        0xFFB0B8C0.toInt(), 0xFFF2B72E.toInt(), 0xFFB87333.toInt(), 0xFF3A3A44.toInt(), 0xFFEDEFF3.toInt(),
        0xFFB3261E.toInt(), 0xFF3B6FD4.toInt(), 0xFF3FA34D.toInt(), 0xFF8E44AD.toInt(), 0xFF18B5C8.toInt(),
        0xFFE87AA8.toInt(), 0xFFE0702E.toInt()
    )
    private val TECIDOS = intArrayOf(
        0xFFC62828.toInt(), 0xFF2E5CB8.toInt(), 0xFF3FA34D.toInt(), 0xFF8E44AD.toInt(), 0xFFF2B72E.toInt(),
        0xFF1B1A24.toInt(), 0xFFEDEFF3.toInt(), 0xFF8D5524.toInt(), 0xFFE87AA8.toInt(), 0xFF18B5C8.toInt(),
        0xFFE0702E.toInt(), 0xFF5C3A21.toInt()
    )
    private val OLHOS = intArrayOf(
        0xFFFFC107.toInt(), 0xFF5FD4FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFFF5252.toInt(), 0xFFB388FF.toInt(),
        0xFFFFE9C0.toInt(), 0xFFF5F5F5.toInt(), 0xFFFF9800.toInt(), 0xFFFF80AB.toInt(), 0xFF18FFFF.toInt()
    )

    private fun fundo(cor: Int, raio: Float, traco: Int = 0, tracoW: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(cor)
        g.cornerRadius = raio
        if (tracoW > 0) g.setStroke(tracoW, traco)
        return g
    }

    fun mostrar(act: Activity, classe: Classe, aoConfirmar: () -> Unit) {
        val ed = Herois.perfil(classe).copia()
        val dm = act.resources.displayMetrics
        fun dp(v: Float): Int = (v * dm.density + 0.5f).toInt()

        val dlg = Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dlg.window?.setBackgroundDrawable(ColorDrawable(FUNDO))
        var confirmou = false

        val preview = PreviewView(act, classe)
        val atualizadores = ArrayList<() -> Unit>()

        val tvRacaDesc = TextView(act)
        val tvPontos = TextView(act)
        val tvStats = TextView(act)
        val racas = Raca.values()
        val atribs = Atrib.values()
        val btnRaca = ArrayList<Button>()
        val tvNota = ArrayList<TextView>()
        val tvBase = ArrayList<TextView>()
        val btnMais = ArrayList<Button>()
        val btnMenos = ArrayList<Button>()

        fun titulo(t: String): TextView {
            val v = TextView(act)
            v.text = t
            v.setTextColor(OURO)
            v.textSize = 15f
            v.typeface = Typeface.DEFAULT_BOLD
            v.setPadding(0, dp(14f), 0, dp(6f))
            return v
        }

        fun botao(t: String, ouro: Boolean): Button {
            val b = Button(act)
            b.text = t
            b.isAllCaps = false
            b.minHeight = 0
            b.minimumHeight = 0
            b.minWidth = 0
            b.minimumWidth = 0
            b.textSize = 13f
            b.setTextColor(if (ouro) 0xFF1B1408.toInt() else TEXTO)
            b.background = fundo(if (ouro) OURO else 0xFF3A3024.toInt(), dp(10f).toFloat())
            b.setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            return b
        }

        fun refresh() {
            HeroLook.aplicar(ed)
            for (i in racas.indices) {
                val sel = racas[i] == ed.raca
                btnRaca[i].background = fundo(
                    if (sel) 0xFF5A4318.toInt() else 0xFF3A3024.toInt(), dp(10f).toFloat(),
                    if (sel) OURO else 0xFF3A3024.toInt(), dp(2f)
                )
                btnRaca[i].setTextColor(if (sel) OURO else TEXTO)
            }
            tvRacaDesc.text = ed.raca.descricao
            for (i in atribs.indices) {
                val a = atribs[i]
                tvNota[i].text = ed.total(a).toString()
                val b = ed.raca.bonusDe(a)
                tvBase[i].text = "base " + ed.nota(a) + (if (b > 0) " +$b" else if (b < 0) " $b" else "")
                btnMais[i].isEnabled = ed.pode(a, 1)
                btnMenos[i].isEnabled = ed.pode(a, -1)
                btnMais[i].alpha = if (btnMais[i].isEnabled) 1f else 0.35f
                btnMenos[i].alpha = if (btnMenos[i].isEnabled) 1f else 0.35f
            }
            tvPontos.text = "Pontos para gastar: " + ed.livres() + " de " + CompraPontos.TOTAL
            val sb = StringBuilder()
            sb.append(String.format(Locale.US, "Vida %d", ed.maxHp.toInt()))
            if (ed.maxMp > 0f) sb.append(String.format(Locale.US, "  ·  Mana %d", ed.maxMp.toInt()))
            sb.append(String.format(Locale.US, "  ·  Dano %d", ed.dano.toInt()))
            sb.append(String.format(Locale.US, "  ·  Velocidade %.1f", ed.velocidade))
            sb.append(String.format(Locale.US, "\nDefesa %d%%  ·  Regeneração %.1f/s  ·  Crítico %d%% (x%.2f)", (ed.defesa * 100f).toInt(), ed.regenHp, (ed.critChance * 100f).toInt(), ed.critMul))
            tvStats.text = sb.toString()
            for (u in atualizadores) u()
            preview.invalidate()
        }

        // ── raça ──
        val painel = LinearLayout(act)
        painel.orientation = LinearLayout.VERTICAL
        painel.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))

        val tit = TextView(act)
        tit.text = "Criar herói — " + classe.nome
        tit.setTextColor(OURO)
        tit.textSize = 20f
        tit.typeface = Typeface.DEFAULT_BOLD
        painel.addView(tit)
        val dica = TextView(act)
        dica.text = "Arraste o herói para girar."
        dica.setTextColor(TEXTO_FRACO)
        dica.textSize = 12f
        painel.addView(dica)

        painel.addView(titulo("Raça"))
        var linha: LinearLayout? = null
        for (i in racas.indices) {
            if (i % 3 == 0) {
                linha = LinearLayout(act)
                linha.orientation = LinearLayout.HORIZONTAL
                painel.addView(linha, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val b = botao(racas[i].nome, false)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(3f), dp(3f), dp(3f), dp(3f))
            linha!!.addView(b, lp)
            b.setOnClickListener {
                ed.raca = racas[i]
                ed.skin = null
                refresh()
            }
            btnRaca.add(b)
        }
        tvRacaDesc.setTextColor(TEXTO)
        tvRacaDesc.textSize = 13f
        tvRacaDesc.setPadding(dp(4f), dp(6f), dp(4f), 0)
        painel.addView(tvRacaDesc)

        // ── atributos ──
        painel.addView(titulo("Atributos"))
        tvPontos.setTextColor(TEXTO)
        tvPontos.textSize = 13f
        tvPontos.typeface = Typeface.DEFAULT_BOLD
        painel.addView(tvPontos)
        for (i in atribs.indices) {
            val a = atribs[i]
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(3f), 0, dp(3f))

            val nome = LinearLayout(act)
            nome.orientation = LinearLayout.VERTICAL
            val n1 = TextView(act)
            n1.text = a.nome
            n1.setTextColor(TEXTO)
            n1.textSize = 14f
            val n2 = TextView(act)
            n2.text = a.efeito
            n2.setTextColor(TEXTO_FRACO)
            n2.textSize = 10.5f
            nome.addView(n1)
            nome.addView(n2)
            row.addView(nome, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val menos = botao("−", false)
            val mais = botao("+", false)
            val nota = TextView(act)
            nota.setTextColor(OURO)
            nota.textSize = 18f
            nota.typeface = Typeface.DEFAULT_BOLD
            nota.gravity = Gravity.CENTER
            val base = TextView(act)
            base.setTextColor(TEXTO_FRACO)
            base.textSize = 11f
            base.gravity = Gravity.CENTER

            menos.setOnClickListener { if (ed.mudar(a, -1)) refresh() }
            mais.setOnClickListener { if (ed.mudar(a, 1)) refresh() }
            tvNota.add(nota)
            tvBase.add(base)
            btnMais.add(mais)
            btnMenos.add(menos)

            row.addView(menos, LinearLayout.LayoutParams(dp(40f), dp(36f)))
            row.addView(nota, LinearLayout.LayoutParams(dp(38f), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(mais, LinearLayout.LayoutParams(dp(40f), dp(36f)))
            row.addView(base, LinearLayout.LayoutParams(dp(74f), ViewGroup.LayoutParams.WRAP_CONTENT))
            painel.addView(row)
        }
        tvStats.setTextColor(TEXTO)
        tvStats.textSize = 12.5f
        tvStats.background = fundo(PAINEL, dp(10f).toFloat())
        tvStats.setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        val lpStats = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpStats.topMargin = dp(8f)
        painel.addView(tvStats, lpStats)

        // ── aparência ──
        fun linhaCores(nome: String, cores: IntArray, atual: () -> Int?, mudar: (Int?) -> Unit) {
            val t = TextView(act)
            t.text = nome
            t.setTextColor(TEXTO)
            t.textSize = 13f
            t.setPadding(0, dp(8f), 0, dp(2f))
            painel.addView(t)
            val hs = HorizontalScrollView(act)
            hs.isHorizontalScrollBarEnabled = false
            val fila = LinearLayout(act)
            fila.orientation = LinearLayout.HORIZONTAL
            fila.gravity = Gravity.CENTER_VERTICAL
            hs.addView(fila)

            val padrao = botao("Padrão", false)
            padrao.setOnClickListener {
                mudar(null)
                refresh()
            }
            fila.addView(padrao, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)))
            atualizadores.add {
                val sel = atual() == null
                padrao.background = fundo(if (sel) 0xFF5A4318.toInt() else 0xFF3A3024.toInt(), dp(10f).toFloat(), if (sel) OURO else 0xFF3A3024.toInt(), dp(2f))
                padrao.setTextColor(if (sel) OURO else TEXTO)
            }
            for (cor in cores) {
                val v = View(act)
                val lp = LinearLayout.LayoutParams(dp(34f), dp(34f))
                lp.setMargins(dp(4f), dp(2f), dp(4f), dp(2f))
                fila.addView(v, lp)
                v.setOnClickListener {
                    mudar(cor)
                    refresh()
                }
                atualizadores.add {
                    val sel = atual() == cor
                    val g = GradientDrawable()
                    g.shape = GradientDrawable.OVAL
                    g.setColor(cor)
                    g.setStroke(if (sel) dp(3f) else dp(1f), if (sel) Color.WHITE else 0xFF000000.toInt())
                    v.background = g
                }
            }
            painel.addView(hs)
        }
        // ── opções em fichas (cicatriz, pintura, acessório, altura) ──
        fun linhaOpcoes(nome: String, opcoes: List<String>, atual: () -> Int, mudar: (Int) -> Unit) {
            val t = TextView(act)
            t.text = nome
            t.setTextColor(TEXTO)
            t.textSize = 13f
            t.setPadding(0, dp(8f), 0, dp(2f))
            painel.addView(t)
            val hs = HorizontalScrollView(act)
            hs.isHorizontalScrollBarEnabled = false
            val fila = LinearLayout(act)
            fila.orientation = LinearLayout.HORIZONTAL
            fila.gravity = Gravity.CENTER_VERTICAL
            hs.addView(fila)
            for (i in opcoes.indices) {
                val b = botao(opcoes[i], false)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36f))
                lp.setMargins(dp(3f), dp(2f), dp(3f), dp(2f))
                fila.addView(b, lp)
                b.setOnClickListener {
                    mudar(i)
                    refresh()
                }
                atualizadores.add {
                    val sel = atual() == i
                    b.background = fundo(if (sel) 0xFF5A4318.toInt() else 0xFF3A3024.toInt(), dp(10f).toFloat(), if (sel) OURO else 0xFF3A3024.toInt(), dp(2f))
                    b.setTextColor(if (sel) OURO else TEXTO)
                }
            }
            painel.addView(hs)
        }
        painel.addView(titulo("Aparência"))
        linhaOpcoes("Sexo", listOf("Masculino", "Feminino"), { ed.sexo }, { ed.sexo = it; if (it == 1 && ed.cabelo == 0) ed.cabelo = 3 })
        linhaCores("Pele", PELES, { ed.skin }, { ed.skin = it })
        linhaCores("Cabelo e barba", CABELOS, { ed.hair }, { ed.hair = it })
        linhaCores("Olhos", OLHOS, { ed.eye }, { ed.eye = it })
        linhaOpcoes("Altura", listOf("Muito baixo", "Baixo", "Normal", "Alto", "Muito alto"), { ed.altura }, { ed.altura = it })
        painel.addView(titulo("Rosto"))
        linhaOpcoes("Cicatriz", listOf("Nenhuma", "No olho", "Na bochecha", "No lábio", "Garras"), { ed.cicatriz }, { ed.cicatriz = it })
        linhaOpcoes("Pintura", listOf("Nenhuma", "Listras de guerra", "Máscara", "Lágrima dourada", "Tigre"), { ed.pintura }, { ed.pintura = it })
        linhaOpcoes("Acessório", listOf("Nenhum", "Tapa-olho", "Brincos", "Aro na sobrancelha", "Aro no nariz", "Óculos", "Monóculo"), { ed.acess }, { ed.acess = it })
        painel.addView(titulo("Cabelo e barba"))
        linhaOpcoes("Cabelo", listOf("Padrão", "Careca", "Curto", "Longo", "Moicano", "Rabo de cavalo", "Coque", "Trança", "Espetado", "Franja"), { ed.cabelo }, { ed.cabelo = it })
        linhaOpcoes("Barba", listOf("Padrão", "Sem barba", "Curta", "Cavanhaque", "Bigode"), { ed.barba }, { ed.barba = it })
        painel.addView(titulo("Roupas e armadura"))
        val nomeElmo = when (classe) {
            Classe.MAGO -> "Chapéu"
            Classe.NINJA -> "Máscara"
            else -> "Capacete"
        }
        if (classe != Classe.ARQUEIRO) {
            linhaOpcoes(nomeElmo, listOf("Usar", "Tirar"), { ed.elmo }, { ed.elmo = it })
        }
        linhaOpcoes("Na cabeça", listOf("Nada", "Bandana", "Tiara", "Coroa", "Casquete de aço"), { ed.topo }, { ed.topo = it })
        linhaOpcoes("Roupa do peito", Roupas.nomesPeito(classe), { ed.peito }, { ed.peito = it })
        linhaOpcoes("Roupa das pernas", Roupas.nomesPernas(classe), { ed.pernas }, { ed.pernas = it })
        linhaOpcoes("Capa", Roupas.nomesCapa(classe), { ed.capa }, { ed.capa = it })
        linhaOpcoes("Ombreiras", listOf("Usar", "Tirar"), { ed.ombro }, { ed.ombro = it })
        linhaCores("Cor da armadura (metal)", METAIS, { ed.metal }, { ed.metal = it })
        linhaCores("Cor da roupa (tecido)", TECIDOS, { ed.tecido }, { ed.tecido = it })
        val folga = View(act)
        painel.addView(folga, LinearLayout.LayoutParams(1, dp(16f)))

        val rolagem = ScrollView(act)
        rolagem.addView(painel)

        // ── barra de baixo ──
        val barra = LinearLayout(act)
        barra.orientation = LinearLayout.HORIZONTAL
        barra.setBackgroundColor(PAINEL)
        barra.setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        val bVoltar = botao("Voltar", false)
        val bPadrao = botao("Herói padrão", false)
        val bJogar = botao("Jogar", true)
        bJogar.typeface = Typeface.DEFAULT_BOLD
        val lpb = { w: Float ->
            val l = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
            l.setMargins(dp(4f), 0, dp(4f), 0)
            l
        }
        barra.addView(bVoltar, lpb(1f))
        barra.addView(bPadrao, lpb(1.3f))
        barra.addView(bJogar, lpb(1.6f))

        bVoltar.setOnClickListener { dlg.dismiss() }
        bPadrao.setOnClickListener {
            ed.copiarDe(HeroiPerfil.padrao(classe))
            refresh()
        }
        bJogar.setOnClickListener {
            Herois.salvar(ed)
            confirmou = true
            dlg.dismiss()
            aoConfirmar()
        }

        val coluna = LinearLayout(act)
        coluna.orientation = LinearLayout.VERTICAL
        coluna.addView(rolagem, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        coluna.addView(barra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val raiz = LinearLayout(act)
        raiz.setBackgroundColor(FUNDO)
        if (dm.widthPixels > dm.heightPixels) {
            raiz.orientation = LinearLayout.HORIZONTAL
            raiz.addView(preview, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            raiz.addView(coluna, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.4f))
        } else {
            raiz.orientation = LinearLayout.VERTICAL
            raiz.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (dm.heightPixels * 0.34f).toInt()))
            raiz.addView(coluna, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        dlg.setContentView(raiz)
        dlg.setOnDismissListener {
            preview.parar()
            if (!confirmou) HeroLook.aplicar(Herois.perfil(classe))
        }
        refresh()
        dlg.show()
    }

    /** Mostra o herói de verdade (o mesmo modelo 3D do jogo), girando devagar. */
    private class PreviewView(ctx: Context, private val classe: Classe) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var ang = 0.6f
        private var arrastando = false
        private var ultimoX = 0f
        private var vivo = true

        fun parar() {
            vivo = false
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            p.reset()
            p.isAntiAlias = true
            p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF2E261B.toInt(), 0xFF14110D.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
            p.reset()
            p.isAntiAlias = true
            p.color = 0x66000000
            c.drawOval(RectF(w * 0.22f, h * 0.80f, w * 0.78f, h * 0.91f), p)
            if (!arrastando) ang += 0.012f
            val z0 = VoxelArt.zoom
            val l0 = VoxelArt.bufLimit
            VoxelArt.zoom = 1f
            VoxelArt.bufLimit = 150000
            try {
                Sprites.drawHero(c, p, classe, w / 2f, h * 0.84f, h / 105f, 0f, false, sin(ang), cos(ang), 0f)
            } finally {
                VoxelArt.zoom = z0
                VoxelArt.bufLimit = l0
            }
            if (vivo) postInvalidateOnAnimation()
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    arrastando = true
                    ultimoX = e.x
                }
                MotionEvent.ACTION_MOVE -> {
                    ang -= (e.x - ultimoX) * 0.012f
                    ultimoX = e.x
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> arrastando = false
            }
            return true
        }
    }
}
