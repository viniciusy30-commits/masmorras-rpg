package com.viniciusy.masmorras

enum class Classe(
    val nome: String,
    val descricao: String,
    val cor: Int,
    val maxHp: Float,
    val maxMp: Float,
    val velocidade: Float,
    val danoBase: Float,
    val alcance: Float,
    val cooldownAtaque: Long, // ms
    val isRanged: Boolean
) {
    GUERREIRO(
        "Guerreiro",
        "Tanque de linha de frente. Alta vida e ataque corpo a corpo poderoso.",
        0xFFC62828.toInt(),
        180f, 0f, 3.2f, 22f, 55f, 450L, false
    ),
    MAGO(
        "Mago",
        "Lança projéteis mágicos de longa distância. Baixa vida, alto dano.",
        0xFF1565C0.toInt(),
        90f, 100f, 2.8f, 28f, 220f, 550L, true
    ),
    ARQUEIRO(
        "Arqueiro",
        "Dispara flechas rápidas. Bom equilíbrio entre dano e mobilidade.",
        0xFF2E7D32.toInt(),
        110f, 0f, 3.6f, 18f, 200f, 380L, true
    ),
    BARBARO(
        "Bárbaro",
        "Fúria selvagem. Dano massivo em área curta, vida alta.",
        0xFFE65100.toInt(),
        200f, 0f, 3.0f, 32f, 70f, 600L, false
    )
}
