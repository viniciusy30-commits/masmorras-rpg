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
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

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
        val btnRaca = ArrayList<TextView>()
        val tvNota = ArrayList<TextView>()
        val tvBase = ArrayList<TextView>()
        val btnMais = ArrayList<Button>()
        val btnMenos = ArrayList<Button>()

        // ───────────── peças de interface ─────────────

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

        fun marcar(b: TextView, sel: Boolean) {
            b.background = fundo(
                if (sel) 0xFF5A4318.toInt() else 0xFF2F271C.toInt(), dp(10f).toFloat(),
                if (sel) OURO else 0xFF4A3D2A.toInt(), if (sel) dp(2f) else dp(1f)
            )
            b.setTextColor(if (sel) OURO else TEXTO)
        }

        fun ficha(t: String): TextView {
            val b = TextView(act)
            b.text = t
            b.gravity = Gravity.CENTER
            b.textSize = 13f
            b.minHeight = dp(44f)
            b.setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            b.isClickable = true
            marcar(b, false)
            return b
        }

        fun secao(p: LinearLayout, t: String) {
            val v = TextView(act)
            v.text = t.uppercase(Locale.getDefault())
            v.setTextColor(OURO)
            v.textSize = 13f
            v.typeface = Typeface.DEFAULT_BOLD
            v.letterSpacing = 0.08f
            v.setPadding(0, dp(16f), 0, dp(5f))
            p.addView(v)
            val linha = View(act)
            linha.setBackgroundColor(0x55F2B63D)
            p.addView(linha, LinearLayout.LayoutParams(MATCH, dp(1f)))
        }

        fun rotulo(p: LinearLayout, t: String) {
            val v = TextView(act)
            v.text = t
            v.setTextColor(TEXTO)
            v.textSize = 13f
            v.setPadding(dp(2f), dp(10f), 0, dp(4f))
            p.addView(v)
        }

        fun nota(p: LinearLayout, t: String): TextView {
            val v = TextView(act)
            v.text = t
            v.setTextColor(TEXTO_FRACO)
            v.textSize = 12f
            v.setPadding(dp(2f), dp(2f), 0, dp(2f))
            p.addView(v)
            return v
        }

        fun vazio(): View = View(act)

        // refresh() é declarado mais abaixo: as peças usam este gancho
        var aoMudar: () -> Unit = {}

        fun cell(): LinearLayout.LayoutParams {
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(dp(3f), dp(3f), dp(3f), dp(3f))
            return lp
        }

        /** Fichas em grade (sem rolar para o lado): uma opção marcada por vez. */
        fun grade(p: LinearLayout, nomes: List<String>, colunas: Int, atual: () -> Int, mudar: (Int) -> Unit) {
            var linha: LinearLayout? = null
            for (i in nomes.indices) {
                if (i % colunas == 0) {
                    val nova = LinearLayout(act)
                    nova.orientation = LinearLayout.HORIZONTAL
                    linha = nova
                    p.addView(nova, LinearLayout.LayoutParams(MATCH, WRAP))
                }
                val b = ficha(nomes[i])
                linha!!.addView(b, cell())
                b.setOnClickListener {
                    mudar(i)
                    aoMudar()
                }
                atualizadores.add { marcar(b, atual() == i) }
            }
            val sobra = (colunas - nomes.size % colunas) % colunas
            for (n in 0 until sobra) linha?.addView(vazio(), cell())
        }

        /** Amostras de cor em grade, com a opção "Padrão". */
        fun cores(p: LinearLayout, nome: String, lista: IntArray, atual: () -> Int?, mudar: (Int?) -> Unit) {
            rotulo(p, nome)
            val padrao = ficha("Padrão")
            padrao.minHeight = dp(36f)
            padrao.setPadding(dp(18f), dp(4f), dp(18f), dp(4f))
            padrao.setOnClickListener {
                mudar(null)
                aoMudar()
            }
            p.addView(padrao, LinearLayout.LayoutParams(WRAP, WRAP))
            atualizadores.add { marcar(padrao, atual() == null) }
            val porLinha = 7
            var linha: LinearLayout? = null
            for (i in lista.indices) {
                if (i % porLinha == 0) {
                    val nova = LinearLayout(act)
                    nova.orientation = LinearLayout.HORIZONTAL
                    linha = nova
                    p.addView(nova, LinearLayout.LayoutParams(MATCH, WRAP))
                }
                val cor = lista[i]
                val v = View(act)
                val lp = LinearLayout.LayoutParams(0, dp(38f), 1f)
                lp.setMargins(dp(3f), dp(4f), dp(3f), dp(4f))
                linha!!.addView(v, lp)
                v.setOnClickListener {
                    mudar(cor)
                    aoMudar()
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
            val sobra = (porLinha - lista.size % porLinha) % porLinha
            for (n in 0 until sobra) {
                val lp = LinearLayout.LayoutParams(0, dp(38f), 1f)
                lp.setMargins(dp(3f), dp(4f), dp(3f), dp(4f))
                linha?.addView(vazio(), lp)
            }
        }

        // ───────────── abas ─────────────
        val rolagem = ScrollView(act)
        val conteudo = LinearLayout(act)
        conteudo.orientation = LinearLayout.VERTICAL
        rolagem.addView(conteudo, ViewGroup.LayoutParams(MATCH, WRAP))

        val nomesAbas = listOf("Raça", "Atributos", "Corpo", "Cabelo", "Rosto", "Roupas")
        val paineis = ArrayList<LinearLayout>()
        val btnAbas = ArrayList<TextView>()

        fun novoPainel(): LinearLayout {
            val l = LinearLayout(act)
            l.orientation = LinearLayout.VERTICAL
            l.setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
            paineis.add(l)
            conteudo.addView(l, LinearLayout.LayoutParams(MATCH, WRAP))
            return l
        }

        fun abrirAba(n: Int) {
            for (i in paineis.indices) paineis[i].visibility = if (i == n) View.VISIBLE else View.GONE
            for (i in btnAbas.indices) {
                val sel = i == n
                btnAbas[i].setTextColor(if (sel) OURO else TEXTO_FRACO)
                btnAbas[i].background = fundo(
                    if (sel) 0xFF3A2D14.toInt() else 0xFF241E16.toInt(), dp(10f).toFloat(),
                    if (sel) OURO else 0xFF241E16.toInt(), dp(2f)
                )
            }
            rolagem.scrollTo(0, 0)
        }

        fun refresh() {
            HeroLook.aplicar(ed)
            for (i in racas.indices) marcar(btnRaca[i], racas[i] == ed.raca)
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

        aoMudar = { refresh() }

        // ── aba 1: raça ──
        val pRaca = novoPainel()
        secao(pRaca, "Escolha a raça")
        var linhaR: LinearLayout? = null
        for (i in racas.indices) {
            if (i % 3 == 0) {
                val nova = LinearLayout(act)
                nova.orientation = LinearLayout.HORIZONTAL
                linhaR = nova
                pRaca.addView(nova, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            val b = ficha(racas[i].nome)
            linhaR!!.addView(b, cell())
            b.setOnClickListener {
                ed.raca = racas[i]
                ed.skin = null
                refresh()
            }
            btnRaca.add(b)
        }
        val sobraR = (3 - racas.size % 3) % 3
        for (n in 0 until sobraR) linhaR?.addView(vazio(), cell())
        tvRacaDesc.setTextColor(TEXTO)
        tvRacaDesc.textSize = 13.5f
        tvRacaDesc.background = fundo(PAINEL, dp(10f).toFloat())
        tvRacaDesc.setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        val lpDesc = LinearLayout.LayoutParams(MATCH, WRAP)
        lpDesc.topMargin = dp(10f)
        pRaca.addView(tvRacaDesc, lpDesc)

        // ── aba 2: atributos ──
        val pAtr = novoPainel()
        secao(pAtr, "Atributos")
        tvPontos.setTextColor(OURO)
        tvPontos.textSize = 14f
        tvPontos.typeface = Typeface.DEFAULT_BOLD
        tvPontos.setPadding(dp(2f), dp(8f), 0, dp(4f))
        pAtr.addView(tvPontos)
        for (i in atribs.indices) {
            val a = atribs[i]
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = fundo(if (i % 2 == 0) PAINEL else FUNDO, dp(8f).toFloat())
            row.setPadding(dp(8f), dp(5f), dp(8f), dp(5f))

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
            row.addView(nome, LinearLayout.LayoutParams(0, WRAP, 1f))

            val menos = botao("−", false)
            val mais = botao("+", false)
            val nt = TextView(act)
            nt.setTextColor(OURO)
            nt.textSize = 18f
            nt.typeface = Typeface.DEFAULT_BOLD
            nt.gravity = Gravity.CENTER
            val base = TextView(act)
            base.setTextColor(TEXTO_FRACO)
            base.textSize = 11f
            base.gravity = Gravity.CENTER

            menos.setOnClickListener { if (ed.mudar(a, -1)) refresh() }
            mais.setOnClickListener { if (ed.mudar(a, 1)) refresh() }
            tvNota.add(nt)
            tvBase.add(base)
            btnMais.add(mais)
            btnMenos.add(menos)

            row.addView(menos, LinearLayout.LayoutParams(dp(40f), dp(38f)))
            row.addView(nt, LinearLayout.LayoutParams(dp(38f), WRAP))
            row.addView(mais, LinearLayout.LayoutParams(dp(40f), dp(38f)))
            row.addView(base, LinearLayout.LayoutParams(dp(74f), WRAP))
            val lpRow = LinearLayout.LayoutParams(MATCH, WRAP)
            lpRow.setMargins(0, dp(2f), 0, dp(2f))
            pAtr.addView(row, lpRow)
        }
        tvStats.setTextColor(TEXTO)
        tvStats.textSize = 12.5f
        tvStats.background = fundo(PAINEL, dp(10f).toFloat(), 0xFF4A3D2A.toInt(), dp(1f))
        tvStats.setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        val lpStats = LinearLayout.LayoutParams(MATCH, WRAP)
        lpStats.topMargin = dp(10f)
        pAtr.addView(tvStats, lpStats)

        // ── aba 3: corpo ──
        val pCorpo = novoPainel()
        secao(pCorpo, "Corpo")
        rotulo(pCorpo, "Sexo")
        grade(pCorpo, listOf("Masculino", "Feminino"), 2, { ed.sexo }, {
            ed.sexo = it
            if (!Penteados.ok(ed.cabelo, it)) ed.cabelo = Penteados.inicial(it)
        })
        val tvCorpo = nota(pCorpo, "")
        atualizadores.add { tvCorpo.text = "Tipo de corpo da raça: " + Roupas.tipoCorpo(ed.raca, ed.sexo == 1) }
        val boxBusto = LinearLayout(act)
        boxBusto.orientation = LinearLayout.VERTICAL
        rotulo(boxBusto, "Busto")
        grade(boxBusto, listOf("Pequeno", "Médio", "Grande"), 3, { ed.busto }, { ed.busto = it })
        pCorpo.addView(boxBusto, LinearLayout.LayoutParams(MATCH, WRAP))
        atualizadores.add { boxBusto.visibility = if (ed.sexo == 1) View.VISIBLE else View.GONE }
        rotulo(pCorpo, "Altura")
        grade(pCorpo, listOf("Muito baixo", "Baixo", "Normal", "Alto", "Muito alto"), 3, { ed.altura }, { ed.altura = it })
        secao(pCorpo, "Cores")
        cores(pCorpo, "Pele", PELES, { ed.skin }, { ed.skin = it })
        cores(pCorpo, "Olhos", OLHOS, { ed.eye }, { ed.eye = it })

        // ── aba 4: cabelo (só os penteados do sexo escolhido, em grupos) ──
        val pCab = novoPainel()
        secao(pCab, "Penteado")
        val tvCabAtual = nota(pCab, "")
        val boxCab = LinearLayout(act)
        boxCab.orientation = LinearLayout.VERTICAL
        pCab.addView(boxCab, LinearLayout.LayoutParams(MATCH, WRAP))
        val btnsCab = ArrayList<Pair<Int, TextView>>()
        var sexoCab = -1
        fun montarCabelos() {
            boxCab.removeAllViews()
            btnsCab.clear()
            val lista = Penteados.para(ed.sexo)
            for (gr in Penteados.GRUPOS) {
                val itens = lista.filter { it.grupo == gr }
                if (itens.isEmpty()) continue
                rotulo(boxCab, gr)
                var linha: LinearLayout? = null
                for (n in itens.indices) {
                    if (n % 2 == 0) {
                        val nova = LinearLayout(act)
                        nova.orientation = LinearLayout.HORIZONTAL
                        linha = nova
                        boxCab.addView(nova, LinearLayout.LayoutParams(MATCH, WRAP))
                    }
                    val e = itens[n]
                    val b = ficha(e.nome)
                    linha!!.addView(b, cell())
                    b.setOnClickListener {
                        ed.cabelo = e.id
                        refresh()
                    }
                    btnsCab.add(Pair(e.id, b))
                }
                if (itens.size % 2 == 1) linha?.addView(vazio(), cell())
            }
        }
        atualizadores.add {
            if (sexoCab != ed.sexo) {
                sexoCab = ed.sexo
                montarCabelos()
            }
            for (pr in btnsCab) marcar(pr.second, pr.first == ed.cabelo)
            tvCabAtual.text = "Estilo atual: " + Penteados.nome(ed.cabelo) + (if (ed.sexo == 1) "  ·  mostrando penteados femininos" else "  ·  mostrando penteados masculinos")
        }
        secao(pCab, "Cor do cabelo")
        cores(pCab, "Cabelo", CABELOS, { ed.hair }, { ed.hair = it })
        val boxBarba = LinearLayout(act)
        boxBarba.orientation = LinearLayout.VERTICAL
        secao(boxBarba, "Barba")
        grade(boxBarba, listOf("Padrão", "Sem barba", "Curta", "Cavanhaque", "Bigode"), 3, { ed.barba }, { ed.barba = it })
        pCab.addView(boxBarba, LinearLayout.LayoutParams(MATCH, WRAP))
        atualizadores.add { boxBarba.visibility = if (ed.sexo == 0) View.VISIBLE else View.GONE }

        // ── aba 5: rosto ──
        val pRosto = novoPainel()
        secao(pRosto, "Rosto")
        rotulo(pRosto, "Cicatriz")
        grade(pRosto, listOf("Nenhuma", "No olho", "Na bochecha", "No lábio", "Garras"), 3, { ed.cicatriz }, { ed.cicatriz = it })
        rotulo(pRosto, "Pintura")
        grade(pRosto, listOf("Nenhuma", "Listras de guerra", "Máscara", "Lágrima dourada", "Tigre"), 2, { ed.pintura }, { ed.pintura = it })
        rotulo(pRosto, "Acessório")
        grade(pRosto, listOf("Nenhum", "Tapa-olho", "Brincos", "Aro na sobrancelha", "Aro no nariz", "Óculos", "Monóculo"), 2, { ed.acess }, { ed.acess = it })

        // ── aba 6: roupas e armadura ──
        val pRoupa = novoPainel()
        secao(pRoupa, "Cabeça")
        val nomeElmo = when (classe) {
            Classe.MAGO -> "Chapéu"
            Classe.NINJA, Classe.PIKACHU -> "Máscara"
            else -> "Capacete"
        }
        if (classe != Classe.ARQUEIRO) {
            rotulo(pRoupa, nomeElmo)
            grade(pRoupa, listOf("Usar", "Tirar"), 2, { ed.elmo }, { ed.elmo = it })
        }
        rotulo(pRoupa, "Na cabeça")
        grade(pRoupa, listOf("Nada", "Bandana", "Tiara", "Coroa", "Casquete de aço"), 2, { ed.topo }, { ed.topo = it })
        secao(pRoupa, "Corpo")
        rotulo(pRoupa, "Roupa do peito")
        grade(pRoupa, Roupas.nomesPeito(classe), 2, { ed.peito }, { ed.peito = it })
        rotulo(pRoupa, "Roupa das pernas")
        grade(pRoupa, Roupas.nomesPernas(classe), 2, { ed.pernas }, { ed.pernas = it })
        secao(pRoupa, "Capa e ombreiras")
        rotulo(pRoupa, "Capa")
        grade(pRoupa, Roupas.nomesCapa(classe), 1, { ed.capa }, { ed.capa = it })
        rotulo(pRoupa, "Ombreiras")
        grade(pRoupa, Roupas.nomesOmbro(classe), 2, { ed.ombro }, { ed.ombro = it })
        secao(pRoupa, "Cores")
        cores(pRoupa, "Armadura (metal)", METAIS, { ed.metal }, { ed.metal = it })
        cores(pRoupa, "Roupa (tecido)", TECIDOS, { ed.tecido }, { ed.tecido = it })
        conteudo.addView(View(act), LinearLayout.LayoutParams(1, dp(16f)))

        // barra de abas
        val abasRolar = HorizontalScrollView(act)
        abasRolar.isHorizontalScrollBarEnabled = false
        val abasFila = LinearLayout(act)
        abasFila.orientation = LinearLayout.HORIZONTAL
        abasFila.setPadding(dp(8f), dp(8f), dp(8f), dp(6f))
        abasRolar.addView(abasFila)
        for (i in nomesAbas.indices) {
            val t = TextView(act)
            t.text = nomesAbas[i]
            t.textSize = 14f
            t.typeface = Typeface.DEFAULT_BOLD
            t.gravity = Gravity.CENTER
            t.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
            t.isClickable = true
            t.setOnClickListener { abrirAba(i) }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.setMargins(dp(3f), 0, dp(3f), 0)
            abasFila.addView(t, lp)
            btnAbas.add(t)
        }

        // cabeçalho
        val cab = LinearLayout(act)
        cab.orientation = LinearLayout.VERTICAL
        cab.setBackgroundColor(PAINEL)
        val tit = TextView(act)
        tit.text = "Criar herói — " + classe.nome
        tit.setTextColor(OURO)
        tit.textSize = 18f
        tit.typeface = Typeface.DEFAULT_BOLD
        tit.setPadding(dp(14f), dp(8f), dp(14f), 0)
        cab.addView(tit)
        val dica = TextView(act)
        dica.text = "Arraste o herói para girar. O que você muda aparece na hora."
        dica.setTextColor(TEXTO_FRACO)
        dica.textSize = 11.5f
        dica.setPadding(dp(14f), 0, dp(14f), 0)
        cab.addView(dica)
        cab.addView(abasRolar, LinearLayout.LayoutParams(MATCH, WRAP))

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
            val l = LinearLayout.LayoutParams(0, WRAP, w)
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
        coluna.addView(cab, LinearLayout.LayoutParams(MATCH, WRAP))
        coluna.addView(rolagem, LinearLayout.LayoutParams(MATCH, 0, 1f))
        coluna.addView(barra, LinearLayout.LayoutParams(MATCH, WRAP))

        val raiz = LinearLayout(act)
        raiz.setBackgroundColor(FUNDO)
        if (dm.widthPixels > dm.heightPixels) {
            raiz.orientation = LinearLayout.HORIZONTAL
            raiz.addView(preview, LinearLayout.LayoutParams(0, MATCH, 1f))
            raiz.addView(coluna, LinearLayout.LayoutParams(0, MATCH, 1.35f))
        } else {
            raiz.orientation = LinearLayout.VERTICAL
            raiz.addView(preview, LinearLayout.LayoutParams(MATCH, (dm.heightPixels * 0.36f).toInt()))
            raiz.addView(coluna, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }

        dlg.setContentView(raiz)
        dlg.setOnDismissListener {
            preview.parar()
            if (!confirmou) HeroLook.aplicar(Herois.perfil(classe))
        }
        refresh()
        abrirAba(0)
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
