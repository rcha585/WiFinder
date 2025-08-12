package com.example.compsci399testproject.utils

data class Edge(
    val to: Node,
    val weight: Float
) {
    override fun toString(): String {
        return "Edge(to=${to.id}, weight=$weight)"
    }

    override fun hashCode(): Int {
        return to.id.hashCode() + weight.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Edge) return false
        return to.id == other.to.id && weight == other.weight
    }
}