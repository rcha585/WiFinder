package com.example.compsci399testproject.utils

import java.util.PriorityQueue

fun getClosestNodes(location: Node, navigationGraph: NavigationGraph, n: Int = 3) : List<Pair<Node, Float>> {
    return navigationGraph.graph.values
        .filter {it.floor == location.floor} // Only nodes on the same floor
        .map { node ->
            Pair(node, calculateDistance(location.x, location.y, node.x, node.y))
        }
        .sortedBy { it.second } // Sort by distance
        .take(n) // Take the closest n nodes
}
private fun calculateDistance(x1: Int, y1: Int, x2: Int, y2: Int): Float {
    return kotlin.math.sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toFloat())
}
fun getRoomNodes(navigationGraph: NavigationGraph): List<Node> {
    return navigationGraph.graph.values
        .filter { it.type == NodeType.ROOM }
        .toList()
}
fun getPath(startNode: Node, goalNode: Node, navigationGraph: NavigationGraph): MutableList<Node> {

    // link location to the graph
    val closestNodes = getClosestNodes(startNode, navigationGraph)
    // print(closestNodes)
    for (edge in closestNodes) {
        startNode.edges.add(Edge(edge.first, edge.second))
    }

    // Get the shortest path
    val shortestPath = dijkstra(navigationGraph.graph, startNode, goalNode)

    return (shortestPath)
}
fun dijkstra(graph: MutableMap<String, Node>, start: Node, goal: Node): MutableList<Node> {

    // Map to store distances from start to each node
    val distances = mutableMapOf<Node, Float>().withDefault { Float.POSITIVE_INFINITY }
    distances[start] = 0f

    // Prio queue to store nodes still to visit
    val queue = PriorityQueue<Pair<Node, Float>>(compareBy { it.second })
    queue.offer(Pair(start, 0f))

    // Map to store predecessors for path reconstruction
    val predecessors = mutableMapOf<Node, Node>()

    // Set to keep track of visited nodes
    val visited = mutableSetOf<Node>()

    while (queue.isNotEmpty()) {
        val (current, currentDistance) = queue.poll()!!

        //if (current == goal) { break } This sacrifices completeness for speed
        if (current in visited) { continue }

        visited.add(current)

        // Process each edge from current node
        for (edge in current.edges) {
            if (edge.to in visited) continue

            val newDistance = currentDistance + edge.weight
            if (newDistance < distances.getValue(edge.to)) {
                distances[edge.to] = newDistance
                predecessors[edge.to] = current
                queue.offer(Pair(edge.to, newDistance))
            }
        }
    }

    // Reconstruct the path
    val path = mutableListOf<Node>()
    var current: Node? = goal

    while (current != null) {
        path.add(0, current)
        current = predecessors[current]
    }

    return if (path.first() == start) path else mutableListOf()
}