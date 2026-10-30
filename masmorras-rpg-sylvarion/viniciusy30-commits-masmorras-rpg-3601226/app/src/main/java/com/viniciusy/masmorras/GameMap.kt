package com.viniciusy.masmorras

import android.graphics.Canvas
import android.graphics.Paint

/** Contrato que o GameView usa para qualquer mapa (mundo aberto procedural ou mapa feito no editor). */
interface GameMap {
    val width: Int
    val height: Int
    val tileSize: Float

    fun heightAt(wx: Float, wy: Float): Float
    fun isWall(wx: Float, wy: Float): Boolean
    fun inSafeZone(wx: Float, wy: Float): Boolean
    fun worldWidth(): Float
    fun worldHeight(): Float
    fun getPlayerSpawn(): Pair<Float, Float>
    fun getBossSpawn(): Pair<Float, Float>
    fun getEnemySpawns(count: Int): List<Pair<Float, Float>>
    fun createEnemies(): List<Enemy>
    fun prewarm(wx: Float, wy: Float)
    fun drawFloor(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float)
    fun drawRow(canvas: Canvas, paint: Paint, ty: Int, camX: Float, camY: Float, viewW: Float)
}
