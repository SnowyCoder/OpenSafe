package xyz.upperlevel.snowy.opensafe

import kotlin.math.pow
import kotlin.math.sqrt

object Calculator {
    const val SQRT = '√'


    private fun parseScalar(tki: TokenList): Double? {
        if (tki.current() == SQRT) {
            if (tki.next() != '(') return null
            val li = tki.findNext(')')
            if (li == -1) return null

            return parseScalar(tki.sublist(li))?.let { sqrt(it) }
        }

        val scalarToken = StringBuilder()

        if ("+-".indexOf(tki.current()) > 0) {
            scalarToken.append(tki.current())
            if (tki.next() == null) return null
        }

        while ("0123456789.".indexOf(tki.current()) >= 0) {
            scalarToken.append(tki.current())
            if (tki.next() == null) break
        }

        var parsedScalar = scalarToken.toString().toDoubleOrNull() ?: return null

        if (tki.hasCurrent() && tki.current() == '%') {
            parsedScalar /= 100
            tki.next()
        }

        return parsedScalar
    }

    private fun parse0(tki: TokenList): Double? {
        val x = parseScalar(tki) ?: return null
        if (!tki.hasCurrent()) return x

        if (tki.current() == '^') {
            if (tki.next() == null) return null
            return x.pow(parse0(tki) ?: return null)
        }
        return x
    }

    private fun parse1(tki: TokenList): Double? {
        var x = parse0(tki) ?: return null

        while (tki.hasCurrent() && (tki.current() == '*' || tki.current() == '/')) {
            val op = tki.current()
            tki.next() ?: return null
            val b = parse0(tki) ?: return null

            if (op == '*') x *= b else x /= b
        }

        return x
    }

    private fun parse2(tki: TokenList): Double? {
        var x = parse1(tki) ?: return null

        while (tki.hasCurrent() && (tki.current() == '+' || tki.current() == '-')) {
            val op = tki.current()
            tki.next() ?: return null
            val b = parse1(tki) ?: return null

            if (op == '+') x += b else x -= b
        }

        return x
    }

    private fun parse(tki: TokenList): Double? {
        return parse2(tki)
    }

    fun calc(str: String): Double? {
        if (str == "") return 0.0
        return parse(TokenList(str))
    }


    class TokenList(val handle: CharSequence) {
        private var i = 0

        fun current(): Char {
            if (!hasCurrent()) {
                throw RuntimeException("Cannot get current token")
            }
            if (i >= handle.length) throw IndexOutOfBoundsException()
            return handle[i]
        }

        fun next(): Char? {
            i++
            return if (hasCurrent()) current() else null
        }

        fun findNext(target: Char): Int {
            return handle.indexOf(target, i)
        }

        fun sublist(start: Int, stop: Int): TokenList {
            return TokenList(handle.subSequence(start, stop))
        }

        fun sublist(stop: Int): TokenList {
            return sublist(i, stop)
        }

        fun hasNext(): Boolean {
            return i + 1 < handle.length
        }

        fun hasCurrent(): Boolean {
            return i < handle.length
        }

        fun peek(offset: Int = 1): Char? {
            if (i + offset >= handle.length) {
                throw RuntimeException("Cannot peek token")
            }
            return handle[i + offset]
        }
    }
}