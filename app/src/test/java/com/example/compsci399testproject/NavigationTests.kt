package com.example.compsci399testproject
import com.example.compsci399testproject.utils.Node
import com.example.compsci399testproject.utils.NodeType
import com.example.compsci399testproject.utils.getPath
import com.example.compsci399testproject.utils.getRoomNodes
import com.example.compsci399testproject.utils.initialiseTestGraph
import org.junit.Test
import org.junit.Assert.*
class NavigationTest {

    @Test
    fun testGetRoomNodes() {

        val navigationGraph = initialiseTestGraph()

        // Test the function
        val roomNodes = getRoomNodes(navigationGraph)

        // Assert results
        assertEquals(1, roomNodes.size)
        assertEquals(NodeType.ROOM, roomNodes[0].type)
        assertEquals("room1", roomNodes[0].id)
    }

    @Test
    fun testGetPath() {

        // Create test navigation graph with test data
        val navigationGraph = initialiseTestGraph()

        // Create a starting location node
        val startNode = Node(
            id = "test_location",
            x = (-198..47).random(),
            y = (-2..464).random(),
            floor = (0..2).random(),
            type = NodeType.TRAVEL,
            edges = mutableListOf()
        )
        val goalNode = getRoomNodes(navigationGraph).random().id

        val startTime = System.currentTimeMillis()

        val targetNode = navigationGraph.findNode(goalNode)

        val path = getPath(startNode, targetNode, navigationGraph)
        //println(path)
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        println("getPath took $duration ms")

        // Assertions
        assertNotNull(path)
        assertTrue(path.isNotEmpty())
        assertEquals("test_location", path.first().id)
        assertEquals(goalNode, path.last().id)

        // Verify path connectivity
        for (i in 0 until path.size - 1) {
            val currentNode = path[i]
            val nextNode = path[i + 1]
            assertTrue(currentNode.edges.any { it.to == nextNode })
        }
    }

    @Test
    fun testGetPathx1000() {
        var totalRuntime = 0f
        val tests = mutableMapOf<List<Node>, String>()

        for (i in 0..999) {


            // Create test navigation graph with test data
            val navigationGraph = initialiseTestGraph()

            // Create a starting location node
            val startNode = Node(
                id = "test_location",
                x = (-198..47).random(),
                y = (-2..464).random().toInt(),
                floor = (0..2).random(),
                type = NodeType.TRAVEL,
                edges = mutableListOf()
            )

            val startTime = System.currentTimeMillis()

            // Get the target node
            val goalNode = getRoomNodes(navigationGraph).random().id
            val targetNode = navigationGraph.findNode(goalNode)

            // Get the path
            val path = getPath(startNode, targetNode, navigationGraph)
            tests[path] = goalNode
            // println(path)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            // println(duration)
            totalRuntime += duration
            // println(totalRuntime)

            // println("getPath took $duration ms")

            // Verify path connectivity
            for (i in 0 until path.size - 1) {
                val currentNode = path[i]
                val nextNode = path[i + 1]
                assertTrue(currentNode.edges.any { it.to == nextNode })
            }


        }
        tests.forEach { test ->
            val path = test.key
            val goalNode = test.value
            assertTrue(path.isNotEmpty())
            assertEquals("test_location", path.first().id)
            assertEquals(goalNode, path.last().id)
        }
        println("Average runtime for getPath: ${(totalRuntime / tests.size).toFloat()} ms from 1000 runs")
    }

}