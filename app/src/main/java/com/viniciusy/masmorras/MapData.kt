package com.viniciusy.masmorras

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Entidade colocada no mapa. type: 0 = inimigo comum, 1 = inimigo forte, 2 = chefe. */
data class MapEnt(val x: Int, val y: Int, val type: Int)

/** Cópia do estado do mapa (usada pelo desfazer/refazer). */
class MapSnap(val floor: IntArray, val obj: IntArray, val hgt: IntArray, val cap: IntArray, val ents: List<MapEnt>, val sx: Int, val sy: Int)

/** Mapa montado pelo jogador. Duas camadas de células (chão e objetos) + entidades. */
class MapData(var name: String, val w: Int, val h: Int, var id: String = "") {
    val floor = IntArray(w * h) { MT.F_STONE }
    val obj = IntArray(w * h)
    /** Altura das paredes em andares (0 = 1 andar). */
    val hgt = IntArray(w * h)
    /** Material dos andares de cima (0 = igual ao de baixo). */
    val cap = IntArray(w * h)
    val ents = ArrayList<MapEnt>()
    var spawnX = 2
    var spawnY = 2

    fun inB(x: Int, y: Int) = x in 0 until w && y in 0 until h

    fun snapshot() = MapSnap(floor.copyOf(), obj.copyOf(), hgt.copyOf(), cap.copyOf(), ents.toList(), spawnX, spawnY)

    fun restore(s: MapSnap) {
        s.floor.copyInto(floor)
        s.obj.copyInto(obj)
        s.hgt.copyInto(hgt)
        s.cap.copyInto(cap)
        ents.clear()
        ents.addAll(s.ents)
        spawnX = s.sx
        spawnY = s.sy
    }

    fun sameAs(s: MapSnap): Boolean =
        floor.contentEquals(s.floor) && obj.contentEquals(s.obj) && hgt.contentEquals(s.hgt) && cap.contentEquals(s.cap) && ents == s.ents && spawnX == s.sx && spawnY == s.sy

    /** Mapa novo: chão de pedra com paredes na borda. */
    fun fillDefault() {
        for (y in 0 until h) for (x in 0 until w) {
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) obj[y * w + x] = MT.W_STONE
        }
        spawnX = w / 2
        spawnY = h / 2
    }

    /** Formato v2: 2 caracteres hexadecimais por célula (valores 0..255). */
    private fun encode(a: IntArray): String {
        val sb = StringBuilder(a.size * 2)
        val hex = "0123456789abcdef"
        for (v in a) {
            val x = v.coerceIn(0, 255)
            sb.append(hex[x shr 4]).append(hex[x and 15])
        }
        return sb.toString()
    }

    private fun decode(s: String, a: IntArray, v2: Boolean) {
        if (v2) {
            for (i in a.indices) if (i * 2 + 1 < s.length) a[i] = s.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0
        } else {
            for (i in a.indices) if (i < s.length) a[i] = (s[i].code - 48).coerceIn(0, 78)
        }
    }

    /** Altura (andares) da parede na célula; 0 se não for parede. */
    fun wallH(x: Int, y: Int): Int {
        if (!inB(x, y)) return 0
        val i = y * w + x
        return if (MT.isWall(obj[i])) hgt[i].coerceIn(1, 4) else 0
    }

    /** Máscara de vizinhos que emendam (bits 0..3) + andares escondidos pela parede da frente (bits 4..6). */
    fun wallGeom(x: Int, y: Int): Int = wallGeomOf(w, h, x, y) { xx, yy -> wallH(xx, yy) }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("v", 2)
        o.put("name", name)
        o.put("w", w)
        o.put("h", h)
        o.put("sx", spawnX)
        o.put("sy", spawnY)
        o.put("floor", encode(floor))
        o.put("obj", encode(obj))
        o.put("hgt", encode(hgt))
        o.put("cap", encode(cap))
        val arr = JSONArray()
        for (e in ents) arr.put(JSONArray().put(e.x).put(e.y).put(e.type))
        o.put("ents", arr)
        return o
    }

    companion object {
        fun fromJson(id: String, o: JSONObject): MapData {
            val m = MapData(o.optString("name", "Mapa"), o.getInt("w"), o.getInt("h"), id)
            m.spawnX = o.optInt("sx", 1)
            m.spawnY = o.optInt("sy", 1)
            m.decodeInto(o.optInt("v", 1) >= 2, o.optString("floor", ""), o.optString("obj", ""), o.optString("hgt", ""), o.optString("cap", ""))
            val arr = o.optJSONArray("ents")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.getJSONArray(i)
                m.ents.add(MapEnt(e.getInt(0), e.getInt(1), e.getInt(2)))
            }
            return m
        }
    }

    private fun decodeInto(v2: Boolean, f: String, o: String, hg: String, cp: String) {
        decode(f, floor, v2)
        decode(o, obj, v2)
        decode(hg, hgt, true)
        decode(cp, cap, true)
    }
}

class MapInfo(val id: String, val name: String, val w: Int, val h: Int, val modified: Long)

/** Salva/carrega mapas em arquivos JSON dentro de filesDir/maps. */
object MapStore {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "maps").also { if (!it.exists()) it.mkdirs() }

    fun newId(): String = "m" + System.currentTimeMillis().toString(36) + (0..999).random()

    fun list(ctx: Context): List<MapInfo> {
        val out = ArrayList<MapInfo>()
        val files = dir(ctx).listFiles() ?: return out
        for (f in files) {
            if (!f.name.endsWith(".json")) continue
            try {
                val o = JSONObject(f.readText())
                out.add(MapInfo(f.name.removeSuffix(".json"), o.optString("name", "Mapa"), o.getInt("w"), o.getInt("h"), f.lastModified()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        out.sortByDescending { it.modified }
        return out
    }

    fun load(ctx: Context, id: String): MapData? = try {
        MapData.fromJson(id, JSONObject(File(dir(ctx), "$id.json").readText()))
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    fun save(ctx: Context, m: MapData) {
        if (m.id.isEmpty()) m.id = newId()
        File(dir(ctx), "${m.id}.json").writeText(m.toJson().toString())
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete()
    }

    fun duplicate(ctx: Context, id: String): MapData? {
        val m = load(ctx, id) ?: return null
        m.id = newId()
        m.name = m.name + " (cópia)"
        save(ctx, m)
        return m
    }
}

/** Geometria de uma parede empilhada: wh(x,y) = andares da parede na célula (0 = não é parede). */
fun wallGeomOf(w: Int, h: Int, x: Int, y: Int, wh: (Int, Int) -> Int): Int {
    val hg = wh(x, y).coerceAtLeast(1)
    var m = 0
    if (y - 1 < 0 || wh(x, y - 1) >= hg) m = m or MapTex.M_N
    if (x + 1 >= w || wh(x + 1, y) >= hg) m = m or MapTex.M_E
    if (y + 1 < h && wh(x, y + 1) >= hg) m = m or MapTex.M_S
    if (x - 1 < 0 || wh(x - 1, y) >= hg) m = m or MapTex.M_W
    val lo = if (y + 1 < h) minOf(wh(x, y + 1), hg) else 0
    return m or (lo shl 4)
}
