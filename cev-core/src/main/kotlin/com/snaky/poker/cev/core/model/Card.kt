package com.snaky.poker.cev.core.model

@JvmInline
@Suppress("NOTHING_TO_INLINE")
value class Card(val index: Int) {

    inline fun rank() = Rank.entries[index % Rank.entries.size]
    inline fun suit() = Suit.entries[index / Rank.entries.size]
    override fun toString(): String = "${rank().ch}${suit().ch}"

    companion object {
        fun of(rank : Rank, suit: Suit) = Card(rank.ordinal + suit.ordinal * Rank.entries.size)
    }

}

val CARDS = Suit.entries.size * Rank.entries.size

enum class Rank(val ch: Char) {
    TWO('2'),
    THREE('3'),
    FOUR('4'),
    FIVE('5'),
    SIX('6'),
    SEVEN('7'),
    EIGHT('8'),
    NINE('9'),
    TEN('T'),
    JACK('J'),
    QUEEN('Q'),
    KING('K'),
    ACE('A');

    companion object {
        private val MAP = entries.associateBy(Rank::ch)
        fun valueOf(ch: Char): Rank = MAP[ch] ?: throw IllegalArgumentException()
    }

}

enum class Suit(val ch: Char) {
    SPADE('s'),
    HEART('h'),
    CLUB('c'),
    DIAMOND('d');

    companion object {
        private val MAP = entries.associateBy(Suit::ch)
        fun valueOf(ch: Char): Suit = MAP[ch] ?: throw IllegalArgumentException()
    }
}
