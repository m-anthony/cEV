package com.snaky.poker.cev.core

@JvmInline
@Suppress("NOTHING_TO_INLINE")
value class CardSet (val cards : Long = 0L){

    inline fun isEmpty() = cards == 0L
    inline fun addCards(cards : CardSet) = CardSet(this.cards or cards.cards)
    inline fun addCard(card : Card) = CardSet(cards or (1L shl card.index))
    inline fun removeCard(card : Card) = CardSet(cards and (1L shl card.index).inv())
    inline fun removeCards(cards : CardSet) = CardSet(this.cards and cards.cards.inv())
    inline fun firstCard() = Card(cards.countTrailingZeroBits())
    inline fun removeFirstCard() = CardSet(cards and (cards - 1))
    inline fun size() = cards.countOneBits()
    inline fun contains(card : Card) = cards and (1L shl card.index) != 0L

    override fun toString(): String {
        var copy = this
        return buildString {
            append('[')
            while (!copy.isEmpty()) {
                if(copy != this@CardSet) append(' ')
                append(copy.firstCard().toString())
                copy = copy.removeFirstCard()
            }
            append(']')
        }
    }

    companion object {
        fun all() = CardSet((1L shl 52) - 1)

        /**
         * @param cards the String representation of the cards, with a single char delimiter between each card
         */
        fun parse(cards: String): CardSet {
            var set = CardSet()
            for (i in 0 until cards.length - 1 step 3) {
                val rank = Rank.valueOf(cards[i])
                val suit = Suit.valueOf(cards[i + 1])
                set = set.addCard(Card.of(rank, suit))
            }
            return set
        }
    }
}
