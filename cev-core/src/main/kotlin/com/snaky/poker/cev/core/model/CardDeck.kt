package com.snaky.poker.cev.core.model

@JvmInline
@Suppress("NOTHING_TO_INLINE")
/**
 * An ordered version of a CardSet
 */
value class CardDeck(val storage: IntArray) {

    val size get() = storage.size

    operator fun get(index: Int): Card = Card(storage[index])
    operator fun set(index: Int, card: Card) {
        storage[index] = card.index
    }

    companion object {
        fun from(cards : CardSet): CardDeck {
            val storage = IntArray(cards.size())
            var i = 0
            var copyCards = cards
            while(!copyCards.isEmpty()){
                storage[i++] = copyCards.firstCard().index
                copyCards = copyCards.removeFirstCard()
            }
            return CardDeck(storage)
        }
    }

}