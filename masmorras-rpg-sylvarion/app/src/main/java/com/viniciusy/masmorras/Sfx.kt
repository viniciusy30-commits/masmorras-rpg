package com.viniciusy.masmorras

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

/**
 * Todos os sons do jogo. Os arquivos ficam em res/raw (gerados por tools/gen_audio.py).
 *  - efeitos: SoundPool (curtos, vários ao mesmo tempo, com volume e rapidez variáveis)
 *  - música: MediaPlayer em loop (menu e jogo)
 * Volumes de efeitos e música são guardados no aparelho e ajustados em Configurações.
 */
object Sfx {
    private val NAMES = arrayOf(
        "ui_click", "ui_confirm", "ui_back", "ui_error", "ui_ping", "place_block", "erase_block",
        "step1", "step2", "step3", "jump", "jump_air", "land_soft", "land_hard", "roll", "hop", "dash",
        "leap_charge", "leap_launch", "leap_land",
        "sword1", "sword2", "sword3", "spear_thrust", "spear_sweep", "heavy_swing", "spear_throw",
        "spear_wall", "spear_pickup", "weapon_swap", "axe1", "axe_slam",
        "hit_blade", "hit_heavy", "hit_crit", "hit_arrow", "hit_magic", "arrow_wall", "spell_wall",
        "player_hurt", "heartbeat", "enemy_die", "enemy_alert", "enemy_swing",
        "spell_charge", "spell_cast", "teleport_out", "teleport_in", "fly_start", "fly_loop", "fly_end",
        "bow_draw", "bow_shot", "bow_shot_strong", "rain_cast", "rain_release", "rain_land",
        "thunder_cast", "thunder_fire", "thunder_hit", "trap_throw", "trap_land", "trap_snap",
        "victory", "defeat"
    )

    /** Intervalo mínimo (ms) entre dois disparos do mesmo som, para não virar barulheira. */
    private val GAPS = hashMapOf(
        "ui_click" to 40L, "ui_back" to 40L, "ui_error" to 350L, "ui_ping" to 300L,
        "step1" to 90L, "step2" to 90L, "step3" to 90L,
        "hit_blade" to 45L, "hit_heavy" to 45L, "hit_crit" to 60L, "hit_arrow" to 40L, "hit_magic" to 45L,
        "rain_land" to 38L, "thunder_hit" to 70L, "enemy_alert" to 260L, "enemy_swing" to 160L,
        "enemy_die" to 90L, "heartbeat" to 500L, "arrow_wall" to 60L, "spell_wall" to 60L,
        "player_hurt" to 160L, "land_soft" to 70L, "land_hard" to 90L
    )

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val ready: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>()
    private val lastAt = HashMap<String, Long>()
    private val loops = HashMap<String, Int>()
    private var appCtx: Context? = null

    var sfxVol = 0.85f
        private set
    var musicVol = 0.5f
        private set

    private var music: MediaPlayer? = null
    private var musicName: String? = null
    private var ducked = false
    private var paused = false

    fun init(ctx: Context) {
        if (pool != null) return
        val app = ctx.applicationContext
        appCtx = app
        val pr = app.getSharedPreferences("audio", Context.MODE_PRIVATE)
        sfxVol = pr.getFloat("sfx", 0.85f).coerceIn(0f, 1f)
        musicVol = pr.getFloat("music", 0.5f).coerceIn(0f, 1f)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attrs).build()
        sp.setOnLoadCompleteListener { _, sid, status -> if (status == 0) ready.add(sid) }
        for (n in NAMES) {
            val rid = app.resources.getIdentifier(n, "raw", app.packageName)
            if (rid != 0) ids[n] = sp.load(app, rid, 1)
        }
        pool = sp
    }

    private fun save() {
        val app = appCtx ?: return
        app.getSharedPreferences("audio", Context.MODE_PRIVATE).edit()
            .putFloat("sfx", sfxVol).putFloat("music", musicVol).apply()
    }

    fun setSfxVolume(v: Float) {
        sfxVol = v.coerceIn(0f, 1f)
        save()
        // atualiza os loops que estão tocando (ex.: voo do mago)
        val sp = pool ?: return
        for ((_, s) in loops) sp.setVolume(s, sfxVol * 0.5f, sfxVol * 0.5f)
    }

    fun setMusicVolume(v: Float) {
        musicVol = v.coerceIn(0f, 1f)
        save()
        applyMusicVolume()
    }

    /**
     * Toca um efeito. vol 0..1, rate 0.5..2 (muda o tom e a duração), pan -1 (esquerda) .. 1 (direita).
     * Devolve o id do fluxo (0 se não tocou).
     */
    fun play(name: String, vol: Float = 1f, rate: Float = 1f, pan: Float = 0f): Int {
        val sp = pool ?: return 0
        if (sfxVol <= 0.001f || paused) return 0
        val sid = ids[name] ?: return 0
        if (!ready.contains(sid)) return 0
        val now = SystemClock.uptimeMillis()
        val gap = GAPS[name] ?: 30L
        val last = lastAt[name] ?: 0L
        if (now - last < gap) return 0
        lastAt[name] = now
        val v = (vol * sfxVol).coerceIn(0f, 1f)
        val pn = pan.coerceIn(-1f, 1f)
        val l = v * (if (pn > 0f) 1f - pn * 0.7f else 1f)
        val r = v * (if (pn < 0f) 1f + pn * 0.7f else 1f)
        return sp.play(sid, l, r, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    /** Som no mundo: dx/dy = posição da fonte em relação ao jogador. Fica mais baixo e mais para o lado conforme a distância. */
    fun playAt(name: String, dx: Float, dy: Float, vol: Float = 1f, rate: Float = 1f, range: Float = 640f): Int {
        val dist = hypot(dx, dy)
        if (dist > range) return 0
        val att = 1f - (dist / range) * 0.75f
        val pan = (dx / (range * 0.6f)).coerceIn(-1f, 1f)
        return play(name, vol * att, rate, pan)
    }

    fun startLoop(name: String, vol: Float = 0.5f) {
        val sp = pool ?: return
        if (loops.containsKey(name) || sfxVol <= 0.001f || paused) return
        val sid = ids[name] ?: return
        if (!ready.contains(sid)) return
        val v = (vol * sfxVol).coerceIn(0f, 1f)
        val s = sp.play(sid, v, v, 1, -1, 1f)
        if (s != 0) loops[name] = s
    }

    fun stopLoop(name: String) {
        val s = loops.remove(name) ?: return
        pool?.stop(s)
    }

    fun stopLoops() {
        for (n in loops.keys.toList()) stopLoop(n)
    }

    // ───────────────────────── música ─────────────────────────

    private fun trackGain(name: String?): Float = if (name == "music_game") 0.8f else 1f

    private fun applyMusicVolume() {
        val mp = music ?: return
        val v = (musicVol * trackGain(musicName) * (if (ducked) 0.25f else 1f)).coerceIn(0f, 1f)
        try {
            mp.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    /** Troca a música ("music_menu", "music_game" ou null para silêncio). Se já é a mesma, não reinicia. */
    fun playMusic(name: String?) {
        if (name == musicName && (music != null || name == null)) return
        stopMusic()
        musicName = name
        if (name == null) return
        val app = appCtx ?: return
        val rid = app.resources.getIdentifier(name, "raw", app.packageName)
        if (rid == 0) return
        try {
            val afd = app.resources.openRawResourceFd(rid) ?: return
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = true
            mp.setOnPreparedListener { m ->
                if (music === m && !paused) {
                    applyMusicVolume()
                    m.start()
                }
            }
            mp.setOnErrorListener { m, _, _ ->
                if (music === m) music = null
                try {
                    m.release()
                } catch (_: Exception) {
                }
                true
            }
            music = mp
            applyMusicVolume()
            mp.prepareAsync()
        } catch (_: Exception) {
            music = null
        }
    }

    fun stopMusic() {
        val mp = music
        music = null
        musicName = null
        if (mp != null) {
            try {
                mp.stop()
            } catch (_: Exception) {
            }
            try {
                mp.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Abaixa a música (vitória/derrota) para o jingle aparecer. */
    fun duck(on: Boolean) {
        ducked = on
        applyMusicVolume()
    }

    fun pauseAll() {
        paused = true
        stopLoops()
        try {
            music?.let { if (it.isPlaying) it.pause() }
        } catch (_: Exception) {
        }
    }

    fun resumeAll() {
        paused = false
        try {
            music?.let { if (!it.isPlaying) it.start() }
        } catch (_: Exception) {
        }
    }
}
