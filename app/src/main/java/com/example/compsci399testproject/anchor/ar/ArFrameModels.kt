package com.example.compsci399testproject.anchor.ar

import com.example.compsci399testproject.anchor.spatial.AnchorObservation
import com.example.compsci399testproject.anchor.spatial.DepthObservation
import com.example.compsci399testproject.anchor.spatial.PoseSample
import com.example.compsci399testproject.anchor.spatial.WallSegment

data class PlaneWallObservation(val planeId: String, val segment: WallSegment)

data class ArFramePacket(
    val cameraPose: PoseSample,
    val anchors: List<AnchorObservation>,
    val depthObservations: List<DepthObservation>,
    val planeWalls: List<PlaneWallObservation>,
)
