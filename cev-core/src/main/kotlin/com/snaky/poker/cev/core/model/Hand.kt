package com.snaky.poker.cev.core.model

class Hand(
    val id: String,
    val spin: Spin
) {
    var timestamp: Long = 0L
    var cev = 0.0
    var blind = 0
    lateinit var position: Position
    val players: List<Player> get() = _players
    val rounds: List<Round> get() = _rounds
    var equity = Double.NaN
    lateinit var hero: Player
    val heroDetected get() = this::hero.isInitialized

    private val _players = ArrayList<Player>()
    private val _rounds = ArrayList<Round>().apply { add(Round(Street.Preflop)) }

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

    fun playerFinished(playerName: String, winCents: Int) {
        if (playerName == hero.name) spin.winCents = winCents
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Hand
        return id == other.id
    }

    override fun hashCode(): Int  = id.hashCode()

    fun compact() {
        _players.trimToSize()
        _rounds.trimToSize()
        _rounds.forEach { it.compact() }
    }
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
    private val _actions = ArrayList<Action>()

    fun addAction(action: Action) = _actions.add(action)
    fun compact() {
        _actions.trimToSize()
    }
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