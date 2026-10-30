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
        "Sylvarion",
        "O Arqueiro do Vendaval. Ágil, atira de longe e domina o vento: lâminas, tornados e dashes. Monte sua árvore de talentos para ter mais dano, mais agilidade ou controle.",
        0xFF4DD0E1.toInt(),
        105f, 0f, 3.8f, 19f, 210f, 380L, true
    ),
    BARBARO(
        "Bárbaro",
        "Fúria selvagem. Dano massivo em área curta, vida alta.",
        0xFFE65100.toInt(),
        200f, 0f, 3.0f, 32f, 70f, 600L, false
    ),
    NINJA(
        "Ninja",
        "Assassino das sombras. Corta rápido, deixa uma Sombra Viva e marca alvos para a morte.",
        0xFFB71C1C.toInt(),
        120f, 100f, 3.5f, 24f, 64f, 330L, false
    ),
    PIKACHU(
        "Pikachu",
        "Rápido e elétrico! Choque do Trovão, Trovoada, Cauda de Ferro, Investida Trovão, Trovão e Ataque Rápido.",
        0xFFF9C900.toInt(),
        120f, 100f, 3.9f, 20f, 60f, 300L, true
    ),
    ICHIGO(
        "Ichigo",
        "Shinigami Substituto da luta contra o Aizen. Getsuga Tenshou, Shunpo e Redemoinho; a transformação em Vasto Lorde troca tudo por Cero, Sonído, Grito Hollow e Mugetsu.",
        0xFFE8541A.toInt(),
        170f, 100f, 3.6f, 27f, 72f, 320L, false
    ),
    LUFFY(
        "Luffy",
        "Capitão dos Chapéus de Palha depois do salto de tempo. Combo de socos de borracha que esticam o braço (direita, esquerda e os dois punhos juntos) e dash estilingue.",
        0xFFD42A2E.toInt(),
        190f, 0f, 3.5f, 26f, 62f, 300L, false
    )
}
