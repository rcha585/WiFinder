package com.example.compsci399testproject.utils

data class Node(
    val id: String,
    val x: Int,
    val y: Int,
    val floor: Int,
    val type: NodeType,
    var edges: MutableList<Edge>
) {
    override fun toString(): String {
        return "Node(id='$id', edges=${edges.map { it.to.id }})"
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false
        return id == other.id
    }
}
enum class NodeType {
    ROOM,
    TRAVEL,
    STAIRS,
    ELEVATOR,
    NULL
}
