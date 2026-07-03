package com.alisia.focuslock

import kotlin.random.Random

/**
 * A quick multiple-choice puzzle (solvable in well under a minute).
 * No typing needed: the user taps one of four options.
 */
data class Puzzle(val question: String, val options: List<Int>, val answer: Int)

object PuzzleFactory {

    fun next(): Puzzle = when (Random.nextInt(3)) {
        0 -> addition()
        1 -> multiplication()
        else -> sequence()
    }

    private fun addition(): Puzzle {
        val a = Random.nextInt(13, 68)
        val b = Random.nextInt(14, 79)
        return build("$a + $b = ?", a + b)
    }

    private fun multiplication(): Puzzle {
        val a = Random.nextInt(4, 13)
        val b = Random.nextInt(3, 10)
        return build("$a × $b = ?", a * b)
    }

    private fun sequence(): Puzzle {
        val start = Random.nextInt(2, 12)
        val step = Random.nextInt(3, 9)
        val terms = (0..4).map { start + it * step }
        val hiddenIndex = Random.nextInt(2, 5)
        val shown = terms.mapIndexed { i, t -> if (i == hiddenIndex) "?" else t.toString() }
        return build("What comes next: ${shown.joinToString(", ")}", terms[hiddenIndex])
    }

    private fun build(question: String, answer: Int): Puzzle {
        val options = mutableSetOf(answer)
        while (options.size < 4) {
            val offset = Random.nextInt(1, 10) * (if (Random.nextBoolean()) 1 else -1)
            val candidate = answer + offset
            if (candidate > 0) options.add(candidate)
        }
        return Puzzle(question, options.shuffled(), answer)
    }
}
