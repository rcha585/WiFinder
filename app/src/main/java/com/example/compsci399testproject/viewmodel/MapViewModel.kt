package com.example.compsci399testproject.viewmodel

import androidx.compose.ui.graphics.Path
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compsci399testproject.utils.NavigationGraph
import com.example.compsci399testproject.utils.Node
import com.example.compsci399testproject.utils.NodeType
import com.example.compsci399testproject.utils.getPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class UIState {
    MAIN, NAVIGATION_PREVIEW, NAVIGATING
}

enum class CameraLockState {
    LOCKED_ON_USER_POSITION, LOCKED_ON_CUSTOM_POSITION, FREE
}

class MapViewModel(private val wifiViewModel: WifiViewModel) : ViewModel() {

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

    // Map Image Size (当前控件显示尺寸，像素)
    var mapImageSizeWidth by mutableStateOf(0f)
    var mapImageSizeHeight by mutableStateOf(0f)

    // Actual Map Image Size in Pixels（底图原始像素）
    var actualImageSizeWidth by mutableStateOf(1536f)
    var actualImageSizeHeight by mutableStateOf(1536f)

    // 你量到的像素原点
    var origin_x by mutableStateOf(885f)
    var origin_y by mutableStateOf(972f)

    // Navigation
    var navigationGraph: NavigationGraph = NavigationGraph()
    var currentNavDestinationNode by mutableStateOf(Node("", 0, 0, 0, NodeType.ROOM, mutableListOf()))
    var navigationNodeList: List<Node> = ArrayList()
    var navigationPath by mutableStateOf(Path())
    var currentFloorPathEndNode by mutableStateOf(Node("", 0, 0, 0, NodeType.NULL, mutableListOf()))
    var nextFloorPathEndNode by mutableStateOf(Node("", 0, 0, 0, NodeType.NULL, mutableListOf()))

    // Position（配准后的地图像素坐标，用于导航起点）
    private var rawPositionX: Float by mutableFloatStateOf(origin_x)
    private var rawPositionY: Float by mutableFloatStateOf(origin_y)

    // 百分比（相对底图尺寸，用于绘制蓝点）
    private val _positionX = MutableStateFlow(origin_x / actualImageSizeWidth)
    val positionX: StateFlow<Float> = _positionX.asStateFlow()

    private val _positionY = MutableStateFlow(origin_y / actualImageSizeHeight)
    val positionY: StateFlow<Float> = _positionY.asStateFlow()

    private val _positionFloor = MutableStateFlow(0)
    val positionFloor: StateFlow<Int> = _positionFloor.asStateFlow()

    private val _rotation = MutableStateFlow(180f)
    val rotation: StateFlow<Float> = _rotation.asStateFlow()

    private val _wifiScanRate: Long = 3_000 // 扫描节奏（毫秒）

    init {
        // 初始蓝点 = 原点百分比
        _positionX.value = origin_x / actualImageSizeWidth
        _positionY.value = origin_y / actualImageSizeHeight
        _positionFloor.value = 0

        startPredictingLocation()
        loopFunction()
    }

    fun setFloor(floor: Int) { currentFloor = floor }
    fun getFloor(): Int = currentFloor

    fun updateOffset(newOffset: Offset) { offset = newOffset }
    fun updateZoom(newZoom: Float) { zoom = newZoom }
    fun updateAngle(newAngle: Float) { angle = newAngle }
    fun updateCameraLockState(value: CameraLockState) { cameraLockState = value }
    fun updateUiState(state: UIState) { uiState = state }

    fun updateScreenSize(width: Float, height: Float) {
        screenSizeWidth = width
        screenSizeHeight = height
    }

    fun updateMapImageSize(width: Float, height: Float) {
        mapImageSizeWidth = width
        mapImageSizeHeight = height
    }

    fun updateMapOffset(x: Float, y: Float, zoom: Float) {
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

    fun updateNavigationGraph(ng: NavigationGraph) { navigationGraph = ng }
    fun updateNavDestinationNode(n: Node) { currentNavDestinationNode = n }

    fun viewDestinationNode(node: Node) {
        updateCameraLockState(CameraLockState.LOCKED_ON_CUSTOM_POSITION)
        updateUiState(UIState.NAVIGATION_PREVIEW)
        updateNavDestinationNode(node)
        setFloor(node.floor)
        updateMapOffset(
            (((origin_x + node.x) / actualImageSizeWidth) * mapImageSizeWidth),
            (((origin_y - node.y) / actualImageSizeHeight) * mapImageSizeHeight),
            6f
        )
        Log.d("MAP VIEWMODEL", "VIEW DESTINATION $offset $zoom $angle | NODE ${node.id} ${node.x}, ${node.y}")
    }

    fun createNavPathList() {
        val currentPositionNode = Node(
            id = "Start Node",
            x = rawPositionX.toInt(),
            y = rawPositionY.toInt(),
            floor = positionFloor.value,
            type = NodeType.TRAVEL,
            mutableListOf()
        )
        Log.d("MAP VIEWMODEL", "NAV CURRENT POSITION NODE ${rawPositionX.toInt()}, ${rawPositionY.toInt()} | FLOOR ${positionFloor.value}")
        Log.d("MAP VIEWMODEL", "NAV DESTINATION NODE ${currentNavDestinationNode.x} ${currentNavDestinationNode.y}, ${currentNavDestinationNode.floor}")

        val pathNodeList: List<Node> = getPath(currentPositionNode, currentNavDestinationNode, navigationGraph)
        navigationNodeList = pathNodeList
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

        var startPositionSet = false
        for (node in navigationNodeList) {
            index += 1
            if (node.floor == floor) {
                val startX = (((origin_x + node.x) / actualImageSizeWidth) * mapImageSizeWidth)
                val startY = (((origin_y - node.y) / actualImageSizeHeight) * mapImageSizeHeight)
                path.moveTo(startX, startY)
                currentFloorPathEndNode = node
                startPositionSet = true
                break
            }
        }

        if (!startPositionSet) {
            navigationPath = Path()
            currentFloorPathEndNode = Node("", 0, 0, 0, NodeType.NULL, mutableListOf())
            nextFloorPathEndNode = Node("", 0, 0, 0, NodeType.NULL, mutableListOf())
            return navigationPath
        }

        for (i: Int in index..<navigationNodeList.size) {
            val node = navigationNodeList[i]
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

    // ---------------------- Wi-Fi → 地图蓝点同步 ----------------------
    private fun startPredictingLocation() {
        // 定时触发扫描
        viewModelScope.launch {
            while (true) {
                try { wifiViewModel.scan() } catch (t: Throwable) {
                    Log.e("MapVM", "scan() failed", t)
                }
                delay(_wifiScanRate)
            }
        }

        // 收集扫描结果 → 刷新蓝点（像素百分比）
        viewModelScope.launch {
            wifiViewModel.scanResults.collectLatest { results ->
                if (results.isEmpty()) return@collectLatest

                val (dx, dy) = wifiViewModel.displayXY.value  // 已平滑 & 仿射后的像素
                rawPositionX = dx
                rawPositionY = dy

                _positionX.value = dx / actualImageSizeWidth
                _positionY.value = dy / actualImageSizeHeight
                _positionFloor.value = wifiViewModel.stableFloor.value

                if (cameraLockState == CameraLockState.LOCKED_ON_USER_POSITION) {
                    setFloor(_positionFloor.value)
                }
            }
        }
    }
}