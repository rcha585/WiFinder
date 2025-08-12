package com.example.compsci399testproject.viewmodel

import androidx.compose.ui.graphics.Path
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compsci399testproject.machinelearning.LocationPredictor
import com.example.compsci399testproject.utils.NavigationGraph
import com.example.compsci399testproject.utils.Node
import com.example.compsci399testproject.utils.NodeType
import com.example.compsci399testproject.utils.getPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class UIState {
    MAIN,
    NAVIGATION_PREVIEW,
    NAVIGATING
}

enum class CameraLockState {
    LOCKED_ON_USER_POSITION,
    LOCKED_ON_CUSTOM_POSITION,
    FREE
}

class MapViewModel(wifiViewModel: WifiViewModel) : ViewModel() {

    var currentFloor by mutableStateOf(0)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    var zoom by mutableStateOf(3.5f)
        private set

    var angle by mutableStateOf(0f)
        private set

    var cameraLockState by mutableStateOf(CameraLockState.LOCKED_ON_USER_POSITION)
        private set

    var uiState by mutableStateOf(UIState.MAIN)

    // Screen Size
    var screenSizeWidth by mutableStateOf(0f)
    var screenSizeHeight by mutableStateOf(0f)

    // Map Image Size
    var mapImageSizeWidth by mutableStateOf(0f)
    var mapImageSizeHeight by mutableStateOf(0f)

    // Actual Map Image Size in Pixels
    var actualImageSizeWidth by mutableStateOf(1536f)
    var actualImageSizeHeight by mutableStateOf(1536f)

    var origin_x by mutableStateOf(754f);
    var origin_y by mutableStateOf(1330f);

    // Navigation
    var navigationGraph: NavigationGraph = NavigationGraph()
    var currentNavDestinationNode by mutableStateOf(Node("", 0, 0, 0, NodeType.ROOM, mutableListOf()))
    var navigationNodeList : List<Node> = ArrayList<Node>()
    var navigationPath by mutableStateOf(Path())
    var currentFloorPathEndNode by mutableStateOf(Node("", 0, 0, 0, NodeType.NULL, mutableListOf()))
    var nextFloorPathEndNode by mutableStateOf(Node("", 0, 0, 0, NodeType.NULL, mutableListOf()))

    // Position
    private var rawPositionX: Float by mutableFloatStateOf(0f)
    private var rawPositionY: Float by mutableFloatStateOf(0f)

    // Position X and Y take percentage values
    // This is because the image scaling is different and can't use the raw pixel values
    private val _positionX = MutableStateFlow((origin_x) / actualImageSizeWidth)
    val positionX: StateFlow<Float> = _positionX.asStateFlow()

    private val _positionY = MutableStateFlow((origin_y) / actualImageSizeHeight)
    val positionY: StateFlow<Float> = _positionY.asStateFlow()

    private val _positionFloor = MutableStateFlow(0)
    val positionFloor: StateFlow<Int> = _positionFloor.asStateFlow()

    private val _rotation = MutableStateFlow(180f)
    val rotation: StateFlow<Float> = _rotation.asStateFlow()

    private val _wifiViewModel = wifiViewModel

    private val _wifiScanRate : Long = 1_000 // Amount of time to wait before next WiFi scan, in milliseconds.

    init {
        startPredictingLocation()
        loopFunction()
    }

    fun setFloor(floor: Int){
        currentFloor = floor
    }

    fun getFloor(): Int {
        return currentFloor
    }

    fun updateOffset(newOffset : Offset){
        offset = newOffset
    }

    fun updateZoom(newZoom : Float){
        zoom = newZoom
    }

    fun updateAngle(newAngle : Float){
        angle = newAngle
    }

    fun updateCameraLockState(value: CameraLockState) {
        cameraLockState = value
    }

    fun updateUiState(state: UIState) {
        uiState = state
    }

    // Screen Size Functions
    fun updateScreenSize(width: Float, height: Float) {
        screenSizeWidth = width
        screenSizeHeight = height
    }

    fun updateMapImageSize(width: Float, height: Float) {
        mapImageSizeWidth = width
        mapImageSizeHeight = height
    }

    fun updateMapOffset(x:Float, y: Float, zoom: Float) {
        val newZoom = zoom

        val widthOffset = (screenSizeWidth / 2) / newZoom
        val heightOffset = (screenSizeHeight / 2) / newZoom

        val xPos = x - widthOffset
        val yPos = y - heightOffset

        val localOffset = Offset(xPos, yPos)
        val localZoom = newZoom
        val localAngle = 0f

        updateOffset(localOffset)
        updateZoom(localZoom)
        updateAngle(localAngle)
    }

    // Navigation functions
    fun updateNavigationGraph(ng: NavigationGraph) {
        navigationGraph = ng
    }


    fun updateNavDestinationNode(n: Node) {
        currentNavDestinationNode = n
    }

    fun viewDestinationNode(node: Node) {
        updateCameraLockState(CameraLockState.LOCKED_ON_CUSTOM_POSITION)
        updateUiState(UIState.NAVIGATION_PREVIEW)
        updateNavDestinationNode(node)
        setFloor(node.floor)

        updateMapOffset((((origin_x + node.x) / actualImageSizeWidth) * mapImageSizeWidth), (((origin_y - node.y) / actualImageSizeHeight) * mapImageSizeHeight), 6f)

        Log.d("MAP VIEWMODEL", "VIEW DESTINATION ${offset} ${zoom} ${angle} | NODE ${node.id} ${node.x}, ${node.y}")
    }

    fun createNavPathList() {
        val currentPositionNode = Node(id = "Start Node", x = rawPositionX.toInt(), y = rawPositionY.toInt(),
            floor = positionFloor.value, type = NodeType.TRAVEL, mutableListOf()
        )
        Log.d("MAP VIEWMODEL", "NAV CURRENT POSITION NODE ${rawPositionX.toInt()}, ${rawPositionY.toInt()}| FLOOR ${positionFloor.value}")
        Log.d("MAP VIEWMODEL", "NAV DESTINATION NODE ${currentNavDestinationNode.x} ${currentNavDestinationNode.y}, ${currentNavDestinationNode.floor}")

        val pathNodeList: List<Node> = getPath(currentPositionNode, currentNavDestinationNode, navigationGraph)
        // val pathNodeList: List<Node> = createCustomNavNodeList()
        navigationNodeList = pathNodeList

        //for (node in navigationNodeList) {
        //    Log.d("NAVIGATION START", "${node.x}, ${node.y}, ${node.floor}, ${node.type}")
        //}
    }

    fun startNavigation() {
        updateUiState(UIState.NAVIGATING)
        createNavPathList()
        setFloor(positionFloor.value)
        updateCameraLockState(CameraLockState.LOCKED_ON_USER_POSITION)
    }

    fun drawNavPath(floor: Int): Path {
        val path = Path()
        var index = 0

        var startPositionSet: Boolean = false;
        // Set starting position
        for (node in navigationNodeList) {
            index += 1
            if (node.floor == floor) {

                val startX = (((origin_x + node.x) / actualImageSizeWidth) * mapImageSizeWidth)
                val startY = (((origin_y - node.y) / actualImageSizeWidth) * mapImageSizeWidth)
                path.moveTo(startX, startY)
                currentFloorPathEndNode = node
                startPositionSet = true
                break
            }
        }

        //Log.d("DRAW NAV PATH", "START POSITION SET ${startPositionSet}")

        if (!startPositionSet) {
            navigationPath = Path()
            currentFloorPathEndNode = Node("", 0, 0, 0, NodeType.NULL, mutableListOf())
            nextFloorPathEndNode = Node("", 0, 0, 0, NodeType.NULL, mutableListOf())
            return navigationPath
        }

        // Loop through nodes on current floor to create Path UI
        for (i: Int in index..<navigationNodeList.size) {
            val node = navigationNodeList.get(i)

            if (node.floor != floor) {
                nextFloorPathEndNode = node
                break
            }

            val x = (((origin_x + node.x) / actualImageSizeWidth) * mapImageSizeWidth)
            val y = (((origin_y - node.y) / actualImageSizeHeight) * mapImageSizeHeight)
            path.lineTo(x, y)

            currentFloorPathEndNode = node
            nextFloorPathEndNode = Node("", 0, 0, 0, NodeType.NULL, mutableListOf())
        }

        navigationPath = path
        return navigationPath
    }

    fun createCustomNavNodeList() : List<Node> { // For testing the path UI
        var arrayList: ArrayList<Node> = ArrayList<Node>()

        arrayList.add(Node("0T1", 10, 66, 0, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("0T2", 16, 264, 0, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("0T3", 82, 371, 0, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("0T3", 62, 388, 0, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("0S1", 18, 330, 0, NodeType.STAIRS, mutableListOf()))

        arrayList.add(Node("1T1", 47, 352, 1, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("1T2", 31, 363, 1, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("1T3", 10, 313, 1, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("1S1", -22, 270, 1, NodeType.STAIRS, mutableListOf()))

        arrayList.add(Node("2T1", -34, 252, 2, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("2T2", -75, 196, 2, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("2T3", -70, 148, 2, NodeType.TRAVEL, mutableListOf()))
        arrayList.add(Node("Room 1", -56, 116, 2, NodeType.ROOM, mutableListOf()))

        return arrayList
    }

    private fun loopFunction() {
        viewModelScope.launch {
            while (true) {
                if (uiState == UIState.NAVIGATING) {
                    createNavPathList()
                }
                delay(1_000)
            }
        }
    }

    //////////////////////////////////////////////////////////////////
    //                      YOUR CHANGES BELOW                      //
    //////////////////////////////////////////////////////////////////
    //
    // startPredictingLocation() begins the loop for continually scanning
    // for WiFi signals and using the machine learning models for predicting
    // the user location.
    private fun startPredictingLocation() {
        viewModelScope.launch {
            while (true) {
                _wifiViewModel.scan()

                val success = withTimeoutOrNull(10_000) {
                    _wifiViewModel.scanResults.firstOrNull { results ->
                        if (results.isNotEmpty()) {
                            _wifiViewModel.updateScanResults()

                            val strengthArray = _wifiViewModel.getStrengthArray()
                            val floor = LocationPredictor.predictFloor(strengthArray.toFloatArray())
                            val x = LocationPredictor.predictX(strengthArray.toFloatArray())
                            val y = LocationPredictor.predictY(strengthArray.toFloatArray())

                            // FOR DEBUG PURPOSES
                            //val floor = 0
                            //val x = (rawPositionX + 10f) % 100f
                            //val y = (rawPositionY + 10f) % 100f

                            rawPositionX = x
                            rawPositionY = y

                            Log.d("predictor", "Predicted X: $x, Y: $y, Floor: $floor")

                            _positionX.value = (origin_x + x) / actualImageSizeWidth
                            _positionY.value = (origin_y - y) / actualImageSizeHeight
                            _positionFloor.value = floor

                            if (cameraLockState == CameraLockState.LOCKED_ON_USER_POSITION) {
                                setFloor(floor)
                            }

                            true
                        } else false
                    }
                }

                delay(_wifiScanRate)
            }
        }
    }
    //////////////////////////////////////////////////////////////////
    //                      YOUR CHANGES ABOVE                      //
    //////////////////////////////////////////////////////////////////
}