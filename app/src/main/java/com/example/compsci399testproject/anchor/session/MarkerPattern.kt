package com.example.compsci399testproject.anchor.session

object MarkerPattern {
    const val GRID_SIZE = 31
    val anchorIds = listOf("A", "B", "C", "D")

    fun normalizeSessionId(value: String): String = value
        .uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(16)

    fun fnv1a(value: String): UInt {
        var hash = 0x811c9dc5u
        for (character in value) {
            hash = hash xor character.code.toUInt()
            hash *= 0x01000193u
        }
        return hash
    }

    fun bits(sessionId: String, anchorId: String): BooleanArray {
        var state = fnv1a("${normalizeSessionId(sessionId)}:$anchorId").toInt()
        if (state == 0) state = 0x6d2b79f5
        val bits = BooleanArray(GRID_SIZE * GRID_SIZE)
        fun nextBit(): Boolean {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return state and 1 == 1
        }
        for (y in 2 until GRID_SIZE - 2) {
            for (x in 2 until GRID_SIZE - 2) bits[y * GRID_SIZE + x] = nextBit()
        }
        stampFinder(bits, 4, 4, 7)
        stampFinder(bits, GRID_SIZE - 11, 4, 7)
        stampFinder(bits, 4, GRID_SIZE - 11, 7)
        stampOrientation(bits, anchorId)
        return bits
    }

    private fun stampFinder(bits: BooleanArray, left: Int, top: Int, size: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val edge = x == 0 || y == 0 || x == size - 1 || y == size - 1
                val centre = x in 2..size - 3 && y in 2..size - 3
                bits[(top + y) * GRID_SIZE + left + x] = edge || centre
            }
        }
    }

    private fun stampOrientation(bits: BooleanArray, anchorId: String) {
        val code = anchorIds.indexOf(anchorId).coerceAtLeast(0)
        val top = GRID_SIZE - 10
        val left = GRID_SIZE - 10
        for (y in 0 until 6) {
            for (x in 0 until 6) {
                val diagonal = x == y || x + y == 5
                val encoded = y == 5 && (code shr (x % 2)) and 1 == 1
                bits[(top + y) * GRID_SIZE + left + x] = diagonal || encoded
            }
        }
    }
}
