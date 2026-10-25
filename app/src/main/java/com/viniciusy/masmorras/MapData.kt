package com.viniciusy.masmorras

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Entidade colocada no mapa. type: 0 = inimigo comum, 1 = inimigo forte, 2 = chefe. */
data class MapEnt(val x: Int, val y: Int, val type: Int)

/** Cópia do estado do mapa (usada pelo desfazer/refazer). */
class MapSnap(val floor: IntArray, val obj: IntArray, val ents: List<MapEnt>, val sx: Int, val sy: Int)

/** Mapa montado pelo jogador. Duas camadas de células (chão e objetos) + entidades. */
class MapData(var name: String, val w: Int, val h: Int, var id: String = "") {
    val floor = IntArray(w * h) { MT.F_STONE }
    val obj = IntArray(w * h)
    val ents = ArrayList<MapEnt>()
    var spawnX = 2
    var spawnY = 2

    fun inB(x: Int, y: Int) = x in 0 until w && y in 0 until h

    fun snapshot() = MapSnap(floor.copyOf(), obj.copyOf(), ents.toList(), spawnX, spawnY)

    fun restore(s: MapSnap) {
        s.floor.copyInto(floor)
        s.obj.copyInto(obj)
        ents.clear()
        ents.addAll(s.ents)
        spawnX = s.sx
        spawnY = s.sy
    }

    fun sameAs(s: MapSnap): Boolean =
        floor.contentEquals(s.floor) && obj.contentEquals(s.obj) && ents == s.ents && spawnX == s.sx && spawnY == s.sy

    /** Mapa novo: chão de pedra com paredes na borda. */
    fun fillDefault() {
        for (y in 0 until h) for (x in 0 until w) {
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) obj[y * w + x] = MT.W_STONE
        }
        spawnX = w / 2
        spawnY = h / 2
    }

    private fun encode(a: IntArray): String {
        val sb = StringBuilder(a.size)
        for (v in a) sb.append((48 + v.coerceIn(0, 78)).toChar())
        return sb.toString()
    }

    private fun decode(s: String, a: IntArray) {
        for (i in a.indices) if (i < s.length) a[i] = (s[i].code - 48).coerceIn(0, 78)
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("v", 1)
        o.put("name", name)
        o.put("w", w)
        o.put("h", h)
        o.put("sx", spawnX)
        o.put("sy", spawnY)
        o.put("floor", encode(floor))
        o.put("obj", encode(obj))
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
            m.decodeInto(o.optString("floor", ""), o.optString("obj", ""))
            val arr = o.optJSONArray("ents")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.getJSONArray(i)
                m.ents.add(MapEnt(e.getInt(0), e.getInt(1), e.getInt(2)))
            }
            return m
        }
    }

    private fun decodeInto(f: String, o: String) {
        decode(f, floor)
        decode(o, obj)
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
