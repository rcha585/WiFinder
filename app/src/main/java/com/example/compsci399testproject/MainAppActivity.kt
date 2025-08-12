package com.example.compsci399testproject

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Path
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.times

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compsci399testproject.utils.NavigationGraph
import com.example.compsci399testproject.utils.Node
import com.example.compsci399testproject.utils.NodeType
import com.example.compsci399testproject.utils.getRoomNodes
import com.example.compsci399testproject.utils.initialiseGraph
import com.example.compsci399testproject.viewmodel.CameraLockState

import com.example.compsci399testproject.viewmodel.MapViewModel
import com.example.compsci399testproject.viewmodel.UIState
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapImageView(
    floor: Int,
    offset: Offset,
    zoom: Float,
    angle: Float,
    updateOffset: (Offset) -> Unit,
    updateZoom: (Float) -> Unit,
    updateAngle: (Float) -> Unit,
    positionXPercentage: Float,
    positionYPercentage: Float,
    positionFloor: Int,
    rotation: Float,
    cameraLockState: CameraLockState,
    updateCameraLockState: (CameraLockState) -> Unit,
    uiState: UIState,
    navigationNode: Node,
    navigationPath: Path,
    currentFloorPathEndNode: Node,
    nextFloorPathEndNode: Node,
    drawNavPath: (Int) -> Path,
    mapImageSizeWidth: Dp,
    mapImageSizeHeight: Dp
) {
    val context = LocalContext.current
    val imageBitmap = remember(floor) {
        try {
            val assetManager = context.assets
            val path = "Building 302/Tiles/Floor ${floor}/Floor${floor}.png"
            val inputStream = assetManager.open(path)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not find floor image", Toast.LENGTH_SHORT).show()
            val assetManager = context.assets
            val path = "Building 302/image_not_found.png"
            val inputStream = assetManager.open(path)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            bitmap.asImageBitmap()
        }
    }

    fun Offset.rotateBy(angle: Float): Offset {
        val angleInRadians = angle * (PI / 180)
        val cos = cos(angleInRadians)
        val sin = sin(angleInRadians)
        return Offset((x * cos - y * sin).toFloat(), (x * sin + y * cos).toFloat())
    }

    // Had to create local variables as there was some weird cache issue with the gesture code using old values
    // This meant that the variables were never updated and always used the default values below
    var localOffset:Offset by remember { mutableStateOf(Offset.Zero) }
    var localZoom:Float by remember { mutableFloatStateOf(3.5f) }
    var localAngle:Float by remember { mutableFloatStateOf(0f) }
    var madeGesture:Boolean by remember { mutableStateOf(false) }

    val floorImageSizeWidth : Dp  = mapImageSizeWidth
    val floorImageSizeHeight : Dp = mapImageSizeHeight

    val rawPositionX: Dp = (floorImageSizeWidth * positionXPercentage)
    val rawPositionY: Dp = (floorImageSizeHeight * positionYPercentage)

    val positionIconSizeX : Dp  = 4.dp
    val positionIconSizeY : Dp = 4.dp
    val positionIconPosX : Dp = rawPositionX - (positionIconSizeX / 2)
    val positionIconPosY : Dp = rawPositionY - (positionIconSizeY / 2)


    Box(modifier = Modifier
        .pointerInput(Unit) {
            detectTransformGestures(
                panZoomLock = true,
                onGesture = { centroid, pan, gestureZoom, gestureRotate ->
                    val oldScale = localZoom
                    val newScale = Math.max(localZoom * gestureZoom, 1.5f)

                    if (gestureRotate == 0f && gestureZoom == 1f && (pan.x.absoluteValue > 10f || pan.y.absoluteValue > 10f)) {
                        updateCameraLockState(CameraLockState.FREE)
                    }

                    // For natural zooming and rotating, the centroid of the gesture should
                    // be the fixed point where zooming and rotating occurs.
                    // We compute where the centroid was (in the pre-transformed coordinate
                    // space), and then compute where it will be after this delta.
                    // We then compute what the new offset should be to keep the centroid
                    // visually stationary for rotating and zooming, and also apply the pan.
                    localOffset =
                        (localOffset + centroid / oldScale).rotateBy(gestureRotate) -
                                (centroid / newScale + pan / oldScale)
                    localZoom = newScale
                    localAngle += gestureRotate

                    madeGesture = true
                }
            )
        }
        .graphicsLayer {
            if (cameraLockState == CameraLockState.LOCKED_ON_USER_POSITION) {
                val width = this.size.width
                val height = this.size.height

                val widthOffset = (width / 2) / localZoom
                val heightOffset = (height / 2) / localZoom

                val x = rawPositionX.toPx() - widthOffset
                val y = rawPositionY.toPx() - heightOffset

                localOffset = Offset(x, y)
                localAngle = 0f

                updateOffset(localOffset)
                updateZoom(localZoom)
                updateAngle(localAngle)
                //Log.d("MAP", "Pixel position of User | ${x}, ${y} | Pos ${width} ${height}")
                madeGesture = false
            } else if (cameraLockState == CameraLockState.FREE) {
                updateOffset(localOffset)
                updateZoom(localZoom)
                updateAngle(localAngle)
                madeGesture = false
            } else {
                // This is a very hacky method to update the local position and should probably be changed later
                // This is mainly here because the local positions needs to be updated when updated from the viewmodel

                if (madeGesture) {
                    updateOffset(localOffset)
                    updateZoom(localZoom)
                    updateAngle(localAngle)

                    updateCameraLockState(CameraLockState.FREE)
                    madeGesture = false
                } else {
                    localOffset = offset
                    localZoom = zoom
                    localAngle = angle
                }
            }

            translationX = -offset.x * zoom
            translationY = -offset.y * zoom
            scaleX = zoom
            scaleY = zoom
            rotationZ = angle
            transformOrigin = TransformOrigin(0f, 0f)


        }
        .fillMaxSize()
    ) {

        //Log.d("MAP", "Zoom ${zoom} | Rotation ${angle} | Offset ${offset},  ")

        Image(bitmap = imageBitmap,
            contentDescription = "${floor} image",
            modifier = Modifier
                .size(width = floorImageSizeWidth, height = floorImageSizeHeight)
                //.background(color = Color.Blue)
        )

        // Navigation path
        val pathColor = colorResource(id = R.color.dark_blue)

        Spacer(modifier = Modifier.fillMaxSize().drawWithContent {
            if (uiState.equals(UIState.NAVIGATING)) {
                drawPath(path = drawNavPath(floor), color = pathColor, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(2.dp.toPx())))
            }
        })

        // Navigation end path icon, shows arrow if user has to change floors
        Box(modifier = Modifier
            .width(4.dp)
            .height(if (uiState.equals(UIState.NAVIGATING) && currentFloorPathEndNode.type != NodeType.NULL) 4.dp else 0.dp)
            .offset(x = (((754f + currentFloorPathEndNode.x) / 1536f) * floorImageSizeWidth) - 2.dp,
                y = (((1330f - currentFloorPathEndNode.y) / 1536f) * floorImageSizeHeight) - 2.dp)
            .background(color = colorResource(id = R.color.dark_blue), shape = RoundedCornerShape(6.dp))
        ) {

            val arrowColor = colorResource(id = R.color.darker_white)

            Box(modifier = Modifier.alpha(if (uiState.equals(UIState.NAVIGATING)) 1f else 0f).drawBehind {
                val sideWidthOffset = 2f
                val sideHeightOffset = 2f
                val topOffset = 3f

                if (!nextFloorPathEndNode.type.equals(NodeType.NULL)) {
                    val path = Path()

                    if (nextFloorPathEndNode.floor < currentFloorPathEndNode.floor) { // Going down a floor
                        path.moveTo(sideWidthOffset, (size.height / 2) - sideHeightOffset)
                        path.lineTo((size.width / 2), size.height - topOffset)
                        path.lineTo((size.width) - sideWidthOffset, (size.height / 2) - sideHeightOffset)
                    } else { // Going up a floor
                        path.moveTo(sideWidthOffset, (size.height / 2) + sideHeightOffset)
                        path.lineTo((size.width / 2), topOffset)
                        path.lineTo((size.width) - sideWidthOffset, (size.height / 2) + sideHeightOffset)
                    }

                    drawPath(path, arrowColor, style = Stroke(width = 1f))
                }


            }.fillMaxSize())
        }

        if (floor == positionFloor) {
            Image(painter = painterResource(id = R.drawable.position_icon),
                contentDescription = "${floor} image",
                modifier = Modifier
                    .size(positionIconSizeX, positionIconSizeY)
                    .offset(positionIconPosX, positionIconPosY)
                    .graphicsLayer {
                        rotationZ = rotation
                    }
            )
        }

        // Shows the location of the node when user wants to preview a room to go to
        if (uiState == UIState.NAVIGATION_PREVIEW && floor == navigationNode.floor) {
            Box(modifier = Modifier
                .width(4.dp)
                .height(4.dp)
                .offset(x = (((754f + navigationNode.x) / 1536f) * floorImageSizeWidth) - 2.dp,
                    y = (((1330f - navigationNode.y) / 1536f) * floorImageSizeHeight) - 2.dp)
                .background(color = colorResource(id = R.color.light_blue), shape = RoundedCornerShape(6.dp))
            )
        }
    }
}

// For Eric - this is a placeholder, feel free to overwrite however you see fit.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloorSelectorList(selectedFloor : Int, onSelect: (Int) -> Unit, visible: Boolean, changeFloorVisibility: (Boolean) -> Unit,
                      updateCameraLockState: (CameraLockState) -> Unit, modifier: Modifier){
    val state : ScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        if (!visible) state.animateScrollTo(900); Log.d("FLOOR SELECTOR", "Scroll function")
    }

    Column(modifier = modifier
        .offset(x = -90.dp, y = -200.dp)
        .animateContentSize()
        .background(
            color = colorResource(id = R.color.darker_white),
            shape = RoundedCornerShape(6.dp)
        )
        .border(
            width = 2.dp,
            color = colorResource(id = R.color.light_blue),
            shape = RoundedCornerShape(6.dp)
        )
        .width(60.dp)
        .height(if (visible) 180.dp else 0.dp)
        .clip(shape = RoundedCornerShape(6.dp))
        .verticalScroll(state, visible),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        for (floor in 5 downTo 0) {
            Button(
                onClick = { onSelect(floor); updateCameraLockState(CameraLockState.FREE) },
                modifier = Modifier
                    .padding(0.dp)
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedFloor == floor && visible) colorResource(id = R.color.dark_grey) else colorResource(id = R.color.darker_white)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("$floor",
                    color = if (selectedFloor != floor || !visible) colorResource(id = R.color.light_blue) else colorResource(R.color.darker_white),
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentHeight(align = Alignment.CenterVertically),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FloorSelectorButton(selectedFloor : Int, visible: Boolean, changeFloorVisibility: (Boolean) -> Unit, modifier: Modifier, uiState: UIState) {
    Button(
        onClick = {changeFloorVisibility(!visible)},
        modifier = modifier
            .offset(x = -20.dp, y = -260.dp)
            .border(
                width = 2.dp,
                color = colorResource(id = R.color.light_blue),
                shape = RoundedCornerShape(6.dp)
            )
            .width(60.dp)
            .height(if (uiState.equals(UIState.MAIN) || uiState.equals(UIState.NAVIGATING)) 60.dp else 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (visible) colorResource(id = R.color.light_blue) else colorResource(id = R.color.darker_white)
        ),
        shape = RoundedCornerShape(6.dp)

    ) {
        Text("$selectedFloor",
            color = if (visible) colorResource(id = R.color.darker_white) else colorResource(id = R.color.light_blue),
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(align = Alignment.CenterVertically),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ResetPositionButton(selectedFloor: Int, positionFloor: Int, modifier: Modifier, changeFloor: (Int) -> Unit,
                        cameraLockState: CameraLockState, updateCameraLockState: (CameraLockState) -> Unit,
                        uiState: UIState
) {
    if ((uiState.equals(UIState.MAIN) || uiState.equals(UIState.NAVIGATING)) && (selectedFloor != positionFloor || cameraLockState != CameraLockState.LOCKED_ON_USER_POSITION)) {
        Box(
            modifier = modifier
                .width(60.dp)
                .height(60.dp)
                .offset(x = -20.dp, y = -180.dp)
                .background(
                    color = colorResource(id = R.color.darker_white),
                    shape = RoundedCornerShape(60.dp)
                )
                .border(
                    width = 2.dp,
                    color = colorResource(id = R.color.light_blue),
                    shape = RoundedCornerShape(60.dp)
                )
                .clickable(indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                ) {
                    changeFloor(positionFloor)
                    updateCameraLockState(CameraLockState.LOCKED_ON_USER_POSITION)
                }
        )
        {
            Image(modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .offset(x = 12.dp, y = 12.dp),
                painter = painterResource(id = R.drawable.show_location_icon),
                contentDescription = "")

            // If the user is looking at a floor different to their current floor then show the user's position floor number
            if (selectedFloor != positionFloor) {
                Box(modifier = modifier
                    .width(24.dp)
                    .height(24.dp)
                    .offset(x = 10.dp, y = -40.dp)
                    .background(
                        color = colorResource(id = R.color.light_blue),
                        shape = RoundedCornerShape(60.dp)
                    )
                ) {
                    Text(text = positionFloor.toString(),
                        color = colorResource(R.color.darker_white),
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentHeight(align = Alignment.CenterVertically),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBar(modifier: Modifier, searchText: String, updateSearchText: (String) -> Unit, searchResults : List<Node>,
              uiState: UIState,
              viewDestinationNode: (Node) -> Unit){
    val singleSearchResultHeight: Dp = 40.dp
    val totalSearchHeight = singleSearchResultHeight * 4

    val focusManager = LocalFocusManager.current

    var searchFocused by remember { mutableStateOf(false) }

    Box(modifier = modifier
        .offset(x = 0.dp, y = 30.dp)
        .width(if (uiState.equals(UIState.MAIN)) 280.dp else 0.dp)
    ) {
        OutlinedTextField(value = searchText,
            onValueChange = { updateSearchText(it) },
            placeholder = { Text("Search") },
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .onFocusChanged {
                    searchFocused = it.isFocused
                },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorResource(id = R.color.light_blue),
                unfocusedBorderColor = colorResource(id = R.color.light_blue),
                cursorColor = colorResource(id = R.color.light_blue),
                focusedContainerColor = colorResource(id = R.color.darker_white),
                unfocusedContainerColor = colorResource(id = R.color.darker_white)
            ),
            trailingIcon = {Icon(imageVector = Icons.Filled.Search, contentDescription = "")},
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        LazyColumn (modifier = Modifier.fillMaxWidth()
            .heightIn(0.dp, totalSearchHeight + (singleSearchResultHeight / 1.5f))
            .height(if (uiState.equals(UIState.MAIN) && searchFocused) singleSearchResultHeight * searchResults.size else 0.dp)
            .offset(x = 0.dp, y = 62.dp)
            .clip(shape = RoundedCornerShape(8.dp))
            .background(color = colorResource(id = R.color.darker_white), shape = RoundedCornerShape(8.dp))
            .border(2.dp, color = colorResource(id = R.color.light_blue), shape = RoundedCornerShape(8.dp)),
        ) {

            items(searchResults) { node ->
                Box(modifier = Modifier.fillMaxWidth().height(singleSearchResultHeight)
                    .clickable {
                        viewDestinationNode(node)
                        focusManager.clearFocus()
                    }) {
                    Text(text = node.id, modifier = Modifier.fillMaxSize()
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .padding(horizontal = 12.dp),
                        color = colorResource(R.color.light_blue),
                        fontWeight = FontWeight(500)
                    )
                }
            }
        }
    }
}

@Composable
fun PreviewNavigationSearchBar(modifier: Modifier, destinationNode: Node,
                               uiState: UIState, updateUiState: (UIState) -> Unit,
                               startNavigation: () -> Unit) {

    var destinationSearchTextFocused: Boolean by remember { mutableStateOf(false) }

    Column(modifier = modifier
        .offset(x = 0.dp, y = 30.dp)
        .width(if (uiState.equals(UIState.NAVIGATION_PREVIEW)) 280.dp else 0.dp)
        .height(120.dp)
        .background(color = colorResource(id = R.color.darker_white), shape = RoundedCornerShape(6.dp))
        .border(color = colorResource(id = R.color.light_blue), width = 2.dp, shape = RoundedCornerShape(6.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("From Current Location", modifier.fillMaxWidth().offset(x = 0.dp, y = 10.dp),
            textAlign = TextAlign.Center,
            color = colorResource(R.color.light_blue),
            fontWeight = FontWeight(500)
        )
        Text("To " + destinationNode.id, modifier.fillMaxWidth().offset(x = 0.dp, y = 20.dp),
            textAlign = TextAlign.Center,
            color = colorResource(R.color.light_blue),
            fontWeight = FontWeight(500)
        )

        Row(modifier = Modifier.offset(0.dp, 40.dp)) {
            Button(onClick = {updateUiState(UIState.MAIN)},
                modifier = Modifier.width(120.dp).height(36.dp).padding(0.dp, 0.dp, 10.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.darker_white)
                ),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, colorResource(id = R.color.red_cancel))
            ) {
                Text("Cancel", color = colorResource(id = R.color.red_cancel))
            }

            Button(onClick = {startNavigation()}, modifier = Modifier.width(120.dp).height(36.dp).padding(10.dp, 0.dp, 0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.light_blue)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Navigate")
            }
        }
    }
}

@Composable
fun NavigationTopBar(modifier: Modifier, destinationNode: Node,
                               uiState: UIState, updateUiState: (UIState) -> Unit) {
    Column(modifier = modifier
        .offset(x = 0.dp, y = 30.dp)
        .width(if (uiState.equals(UIState.NAVIGATING)) 280.dp else 0.dp)
        .height(100.dp)
        .background(color = colorResource(id = R.color.darker_white), shape = RoundedCornerShape(6.dp))
        .border(color = colorResource(id = R.color.light_blue), width = 2.dp, shape = RoundedCornerShape(6.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Navigating to " + destinationNode.id, modifier.fillMaxWidth().offset(x = 0.dp, y = 20.dp),
            textAlign = TextAlign.Center,
            color = colorResource(R.color.light_blue),
            fontWeight = FontWeight(500)
        )

        Button(onClick = {updateUiState(UIState.MAIN)},
            modifier = Modifier.width(120.dp).height(36.dp).offset(x = 0.dp, y = 30.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.darker_white)
            ),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(2.dp, colorResource(id = R.color.red_cancel))
        ) {
            Text("Cancel", color = colorResource(id = R.color.red_cancel))
        }
    }
}

//Main screen for map view.
@Composable
fun MapView(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val currentFocusManager = LocalFocusManager.current
    val displayMetrics = context.resources.displayMetrics

    viewModel.updateScreenSize(displayMetrics.widthPixels.toFloat(), displayMetrics.heightPixels.toFloat())

    val floorImageSizeWidth = 316.dp
    val floorImageSizeHeight = 316.dp

    viewModel.updateMapImageSize(with(LocalDensity.current) {floorImageSizeWidth.toPx()}, with(LocalDensity.current) {floorImageSizeHeight.toPx()})


    var floorSelectorVisible:Boolean by remember { mutableStateOf(false) }

    val positionX by viewModel.positionX.collectAsState()
    val positionY by viewModel.positionY.collectAsState()
    val positionFloor by viewModel.positionFloor.collectAsState()
    val rotation by viewModel.rotation.collectAsState()

    val navigationGraph: NavigationGraph = remember {initialiseGraph(context)}
    val rooms: List<Node> = remember { getRoomNodes(navigationGraph) }

    viewModel.updateNavigationGraph(navigationGraph)

    var searchText: String by remember { mutableStateOf("") }
    var searchResults = remember { mutableStateListOf<Node>() }

    fun getSearchResults(query: String) {
        searchText = query
        searchResults.clear()

        if (searchText.trim() == "") return

        for (room: Node in rooms) {
            if (room.id.lowercase().contains(query.lowercase())) {
                searchResults.add(room)
            }
        }
        Log.d("MAP", searchResults.toString())
    }


    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(onTap = {
                floorSelectorVisible = false
                currentFocusManager.clearFocus()
            })
        }) {

        MapImageView(
            floor = viewModel.currentFloor,
            offset = viewModel.offset,
            zoom = viewModel.zoom,
            angle = viewModel.angle,
            updateOffset = {viewModel.updateOffset(it)},
            updateZoom = {viewModel.updateZoom(it)},
            updateAngle = {viewModel.updateAngle(it)},
            positionXPercentage = positionX,
            positionYPercentage = positionY,
            positionFloor = positionFloor,
            rotation = rotation,
            cameraLockState = viewModel.cameraLockState,
            updateCameraLockState = {viewModel.updateCameraLockState(it)},
            uiState = viewModel.uiState,
            navigationNode = viewModel.currentNavDestinationNode,
            navigationPath = viewModel.navigationPath,
            currentFloorPathEndNode = viewModel.currentFloorPathEndNode,
            nextFloorPathEndNode = viewModel.nextFloorPathEndNode,
            drawNavPath = {viewModel.drawNavPath(it)},
            mapImageSizeWidth = floorImageSizeWidth,
            mapImageSizeHeight = floorImageSizeHeight
        )

        SearchBar(
            modifier = Modifier.align(Alignment.TopCenter),
            searchText = searchText,
            updateSearchText = {getSearchResults(it)},
            searchResults = searchResults,
            uiState = viewModel.uiState,
            viewDestinationNode = {viewModel.viewDestinationNode(it)}
        )

        PreviewNavigationSearchBar(modifier = Modifier.align(Alignment.TopCenter),
            destinationNode = viewModel.currentNavDestinationNode,
            uiState = viewModel.uiState,
            updateUiState = {viewModel.updateUiState(it)},
            startNavigation = {viewModel.startNavigation()}
        )

        NavigationTopBar (modifier = Modifier.align(Alignment.TopCenter),
            destinationNode = viewModel.currentNavDestinationNode,
            uiState = viewModel.uiState,
            updateUiState = {viewModel.updateUiState(it)}
        )

        ResetPositionButton(
            selectedFloor = viewModel.currentFloor,
            positionFloor = positionFloor,
            modifier = Modifier.align(Alignment.BottomEnd),
            changeFloor = { viewModel.setFloor(it)},
            cameraLockState = viewModel.cameraLockState,
            updateCameraLockState = {viewModel.updateCameraLockState(it)},
            uiState = viewModel.uiState
        )

        FloorSelectorButton(
            selectedFloor = viewModel.currentFloor,
            visible = floorSelectorVisible,
            changeFloorVisibility = {floorSelectorVisible = it},
            modifier = Modifier.align(Alignment.BottomEnd),
            uiState = viewModel.uiState
        )

        FloorSelectorList(
            selectedFloor = viewModel.currentFloor,
            onSelect = { viewModel.setFloor(it)},
            visible = floorSelectorVisible,
            changeFloorVisibility = {floorSelectorVisible = it},
            updateCameraLockState = {viewModel.updateCameraLockState(it)},
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
@Preview
fun MapPreviewFun() {
    var arrayList: ArrayList<Node> = ArrayList<Node>()

    arrayList.add(Node("Room 1", 30, 30, 0, NodeType.ROOM, mutableListOf()))
    arrayList.add(Node("Room 2", 30, 30, 0, NodeType.ROOM, mutableListOf()))
    arrayList.add(Node("Room 3", 30, 30, 0, NodeType.ROOM, mutableListOf()))
    arrayList.add(Node("Room 4", 30, 30, 0, NodeType.ROOM, mutableListOf()))
    arrayList.add(Node("Room 5", 30, 30, 0, NodeType.ROOM, mutableListOf()))


    //SearchBar(Modifier, "", {}, arrayList)

    PreviewNavigationSearchBar(Modifier,
        Node("Room 1", 30, 30, 0, NodeType.ROOM, mutableListOf()),
        UIState.NAVIGATION_PREVIEW,
        {},
        {}
    )

    //NavigationTopBar (Modifier,
    //    Node("Room 1", 30, 30, 0, NodeType.ROOM, mutableListOf()),
    //    UIState.NAVIGATING,
    //    {}
    //)
}

