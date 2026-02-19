package com.snaky.poker.cev.core

class Hand(
    val id: String,
    val spin: Spin
) {
    var timestamp: Long = 0L
    val chips get() = hero.let { it.remaining - it.stack }
    var cev = 0.0
    var blind = 0
    lateinit var position: Position
    val players: List<Player> get() = _players
    val rounds: List<Round> get() = _rounds
    lateinit var hero: Player

    private val _players = mutableListOf<Player>()
    private val _rounds = mutableListOf(Round(Street.Preflop))

    enum class Position {
        BU,
        SB,
        BB,
        HUSB,
        HUBB,
    }

    fun nextRound(board : CardSet) = currentRound().street.next()?.also {_rounds.add(Round(it, board))}
    fun currentRound() = rounds.last()

    fun addPlayer(name: String, stack: Int) : Player {
        val player = Player(name, stack, players.size)
        _players.add(player)
        return player
    }

    fun findPlayer(name: String): Player {
        return players.first { it.name == name }
    }

    fun holeCards(playerName: String, cards: CardSet) {
        findPlayer(playerName).cards = cards
    }

    fun winPot(playerName: String, chips: Int) {
        findPlayer(playerName).remaining += chips
    }

    fun playerFinished(playerName: String, wins: Double) {
        if (playerName == hero.name) spin.wins = wins
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Hand
        return id == other.id
    }

    override fun hashCode(): Int  = id.hashCode()
}

data class Player(
    val name: String,
    val stack: Int,
    val seatId: Int
) {
    var cards = CardSet()
    var remaining = stack
}

enum class Street {
    Preflop, Flop, Turn, River;
    fun next() = if(this == River) null else entries[ordinal + 1]
}

data class Round(
    val street: Street,
    val board: CardSet = CardSet(),
) {
    val actions: List<Action> get() = _actions
    private val _actions: MutableList<Action> = mutableListOf()

    fun addAction(action: Action) = _actions.add(action)
}

enum class ActionType {
    Fold, Check, Call, Bet, Blind
}

data class Action(
    val player: Player,
    val type: ActionType,
    val allIn: Boolean = false,
    val amount: Int = 0
)