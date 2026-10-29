package com.viniciusy.masmorras

/**
 * Catálogo de penteados. Cada um diz para quem é (masculino, feminino ou ambos)
 * e em qual grupo aparece na tela de criação. O número (id) é o mesmo usado pelo HeroKit.
 */
object Penteados {
    class Estilo(val id: Int, val nome: String, val grupo: String, val sexo: Int) // sexo: 0 masculino, 1 feminino, 2 ambos

    val GRUPOS = listOf("Básico", "Curtos", "Médios", "Longos", "Presos e tranças", "Ousados")

    val TODOS: List<Estilo> = listOf(
        Estilo(0, "Padrão da classe", "Básico", 2),
        Estilo(1, "Careca", "Básico", 2),

        Estilo(2, "Curto", "Curtos", 2),
        Estilo(33, "Social com risca", "Curtos", 0),
        Estilo(16, "Raspado dos lados", "Curtos", 0),
        Estilo(14, "Pompadour", "Curtos", 0),
        Estilo(8, "Espetado", "Curtos", 0),
        Estilo(27, "Pixie repicado", "Curtos", 1),

        Estilo(12, "Chanel", "Médios", 1),
        Estilo(26, "Chanel assimétrico", "Médios", 1),
        Estilo(34, "Bagunçado", "Médios", 0),
        Estilo(9, "Franja longa", "Médios", 2),

        Estilo(3, "Longo liso", "Longos", 2),
        Estilo(24, "Franja cortina", "Longos", 1),
        Estilo(10, "Ondulado", "Longos", 2),
        Estilo(11, "Cacheado", "Longos", 2),
        Estilo(25, "Cachos longos", "Longos", 1),
        Estilo(31, "Em camadas", "Longos", 1),
        Estilo(21, "Hime", "Longos", 1),
        Estilo(30, "Princesa com laço", "Longos", 1),
        Estilo(35, "Juba selvagem", "Longos", 0),
        Estilo(23, "Ao vento", "Longos", 2),

        Estilo(5, "Rabo de cavalo", "Presos e tranças", 2),
        Estilo(32, "Rabo lateral", "Presos e tranças", 1),
        Estilo(22, "Meia-cauda", "Presos e tranças", 2),
        Estilo(6, "Coque", "Presos e tranças", 2),
        Estilo(29, "Coque de bailarina", "Presos e tranças", 1),
        Estilo(20, "Dois coques", "Presos e tranças", 1),
        Estilo(13, "Marias-chiquinhas", "Presos e tranças", 1),
        Estilo(7, "Trança", "Presos e tranças", 2),
        Estilo(19, "Trança lateral", "Presos e tranças", 1),
        Estilo(28, "Tranças duplas", "Presos e tranças", 1),
        Estilo(15, "Rabo de samurai", "Presos e tranças", 0),

        Estilo(4, "Moicano", "Ousados", 0),
        Estilo(17, "Black power", "Ousados", 2),
        Estilo(18, "Dreadlocks", "Ousados", 2),
        Estilo(36, "Tonsura", "Ousados", 0)
    )

    /** Penteados que aparecem para o sexo escolhido (0 masculino, 1 feminino). */
    fun para(sexo: Int): List<Estilo> = TODOS.filter { it.sexo == 2 || it.sexo == sexo }

    fun ok(id: Int, sexo: Int): Boolean = TODOS.any { it.id == id && (it.sexo == 2 || it.sexo == sexo) }

    fun nome(id: Int): String = TODOS.firstOrNull { it.id == id }?.nome ?: "Padrão da classe"

    /** Penteado que entra quando se troca de sexo e o atual não existe para o novo. */
    fun inicial(sexo: Int): Int = if (sexo == 1) 24 else 0
}
