package com.example.compsci399testproject.utils

import android.content.Context


class NavigationGraph {
    internal val graph = mutableMapOf<String, Node>()

    fun addNodeToGraph(node: Node) {
        graph[node.id] = node
    }

    fun findNode(id: String): Node {
        var retNode: Node? = null

        for (node in graph) {
            if (node.key == id) {
                retNode = node.value
                break
            }
        }
        return retNode ?: throw NoSuchElementException("Node with id $id not found")
    }
}
fun initialiseGraph(context: Context): NavigationGraph {
    val navigationGraph = NavigationGraph()
    val nodesJson = loadNodesFromJson(context)

    val nodes = nodesJson.map { nodeJson ->
        Node(
            id = nodeJson.id,
            x = nodeJson.x,
            y = nodeJson.y,
            floor = nodeJson.floor,
            type = when (nodeJson.type) {
                "ROOM" -> NodeType.ROOM
                "TRAVEL" -> NodeType.TRAVEL
                "STAIRS" -> NodeType.STAIRS
                "ELEVATOR" -> NodeType.ELEVATOR
                else -> throw IllegalArgumentException("Unknown node type: ${nodeJson.type}")
            },
            edges = mutableListOf()
        )
    }

    // Add nodes to the graph and edges to the nodes
    for (node in nodes) {
        navigationGraph.addNodeToGraph(node)

        val nodeJson = nodesJson.find { it.id == node.id }
        nodeJson?.edges?.forEach { edgeJson ->
            val toNode = nodes.find { it.id == edgeJson.to }
            if (toNode != null) {
                val edge = Edge(toNode, edgeJson.weight)
                node.edges.add(edge)
            }
        }
    }
    return navigationGraph
}

fun initialiseTestGraph(): NavigationGraph {
    val navigationGraph = NavigationGraph()
    val nodesJson = loadTestNodes()

    val nodes = nodesJson.map { nodeJson ->
        Node(
            id = nodeJson.id,
            x = nodeJson.x,
            y = nodeJson.y,
            floor = nodeJson.floor,
            type = when (nodeJson.type) {
                "ROOM" -> NodeType.ROOM
                "TRAVEL" -> NodeType.TRAVEL
                "STAIRS" -> NodeType.STAIRS
                "ELEVATOR" -> NodeType.ELEVATOR
                else -> throw IllegalArgumentException("Unknown node type: ${nodeJson.type}")
            },
            edges = mutableListOf()
        )
    }

    // Add nodes to the graph and edges to the nodes
    for (node in nodes) {
        navigationGraph.addNodeToGraph(node)

        val nodeJson = nodesJson.find { it.id == node.id }
        nodeJson?.edges?.forEach { edgeJson ->
            val toNode = nodes.find { it.id == edgeJson.to }
            if (toNode != null) {
                val edge = Edge(toNode, edgeJson.weight)
                node.edges.add(edge)
            }
        }
    }
    return navigationGraph
}