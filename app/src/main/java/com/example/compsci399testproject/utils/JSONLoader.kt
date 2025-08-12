package com.example.compsci399testproject.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context

import com.example.compsci399testproject.R as r

data class NodeJson(
    val id: String,
    val x: Int,
    val y: Int,
    val floor: Int,
    val type: String,
    val edges: List<EdgeJson>?
)
data class EdgeJson(
    val to: String,
    val weight: Float
)
fun loadNodesFromJson(context: Context): List<NodeJson> {
    val inputStream = context.resources.openRawResource(r.raw.nodes)
    val jsonString = inputStream.bufferedReader().use { it.readText() }
    val type = object : TypeToken<List<NodeJson>>() {}.type
    return Gson().fromJson(jsonString, type)
}
fun loadTestNodes(): List<NodeJson> {
    return listOf(
        NodeJson("302 G40", -196, 244, 0, "ROOM", listOf(
            EdgeJson("302 G40 D2", 78.08969f),
            EdgeJson("302 G40 D1", 84.64632f)
        )),
        NodeJson("302 G40 D1", -175, 162, 0, "TRAVEL", listOf(
            EdgeJson("302 G40", 84.64632f),
            EdgeJson("302 G40 O1", 18.43909f)
        )),
        NodeJson("302 G40 O1", -161, 150, 0, "TRAVEL", listOf(
            EdgeJson("302 G40 D1", 18.43909f),
            EdgeJson("302 G40 O2", 89.20202f),
            EdgeJson("302 foyer1", 46.8615f)
        )),
        NodeJson("302 G40 D2", -119, 231, 0, "TRAVEL", listOf(
            EdgeJson("302 G40", 78.08969f),
            EdgeJson("302 G40 O2", 15.6205f)
        )),
        NodeJson("302 G40 O2", -107, 221, 0, "TRAVEL", listOf(
            EdgeJson("302 G40 D2", 15.6205f),
            EdgeJson("302 G40 O1", 89.20202f),
            EdgeJson("302 foyer2", 63.89053f),
            EdgeJson("302 G20 O1", 52.00961f)
        )),
        NodeJson("302 G20", -116, 338, 0, "ROOM", listOf(
            EdgeJson("302 G20 D1", 75.15318f),
            EdgeJson("302 G20 D2", 86.0f)
        )),
        NodeJson("302 G20 D1", -84, 270, 0, "TRAVEL", listOf(
            EdgeJson("302 G20", 75.15318f),
            EdgeJson("302 G20 O1", 12.04159f)
        )),
        NodeJson("302 G20 O1", -75, 262, 0, "TRAVEL", listOf(
            EdgeJson("302 G40 O2", 52.00961f),
            EdgeJson("302 G20 D1", 12.04159f),
            EdgeJson("302 G20 O2", 87.9659f),
            EdgeJson("302 foyer3", 69.2026f)
        )),
        NodeJson("302 G20 D2", -30, 338, 0, "TRAVEL", listOf(
            EdgeJson("302 G20", 86.0f),
            EdgeJson("302 G20 O2", 15.0f)
        )),
        NodeJson("302 G20 O2", -18, 329, 0, "TRAVEL", listOf(
            EdgeJson("302 G20 D2", 15.0f),
            EdgeJson("302 G20 O1", 87.9659f),
            EdgeJson("302 foyer4", 71.8401f)
        )),
        NodeJson("302 entranceAlbertSymonds", -86, 20, 0, "TRAVEL", listOf(
            EdgeJson("302 entranceAlbertSymondsStairs", 51.08816f)
        )),
        NodeJson("302 entranceAlbertSymondsStairs", -83, 71, 0, "STAIRS", listOf(
            EdgeJson("302 entranceAlbertSymonds", 51.08816f),
            EdgeJson("302 entranceAlbertSymondsInner", 18.0f)
        )),
        NodeJson("302 entranceAlbertSymondsInner", -83, 89, 0, "TRAVEL", listOf(
            EdgeJson("302 entranceAlbertSymondsStairs", 18.0f),
            EdgeJson("302 foyer1", 52.20153f)
        )),
        NodeJson("301 entranceSymonds", 234, 357, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer8", 43.86342f)
        )),
        NodeJson("302 foyer1", -125, 120, 0, "TRAVEL", listOf(
            EdgeJson("302 entranceAlbertSymondsInner", 52.20153f),
            EdgeJson("302 foyer2", 89.93887f),
            EdgeJson("302 G40 O1", 46.8615f)
        )),
        NodeJson("302 foyer2", -58, 180, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer1", 89.93887f),
            EdgeJson("302 foyer3", 55.17246f),
            EdgeJson("302 G40 O2", 63.89053f)
        )),
        NodeJson("302 foyer3", -20, 220, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer2", 55.17246f),
            EdgeJson("302 foyer4", 86.37129f),
            EdgeJson("302 G20 O1", 69.2026f)
        )),
        NodeJson("302 foyer4", 38, 284, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer3", 86.37129f),
            EdgeJson("302 foyer5", 68.88396f),
            EdgeJson("302 G20 O2", 71.8401f)
        )),
        NodeJson("302 foyer5", 82, 337, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer4", 68.88396f),
            EdgeJson("302 foyer6", 52.61179f),
            EdgeJson("302 mainStairs-F0", 48.83646f),
            EdgeJson("302 eleEnt1", 25.0f)
        )),
        NodeJson("302 foyer6", 90, 389, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer5", 52.61179f),
            EdgeJson("302 foyer7", 81.34494f),
            EdgeJson("302 foyer9", 51.89412f)
        )),
        NodeJson("302 foyer7", 166, 360, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer6", 81.34494f),
            EdgeJson("302 foyer8", 51.66237f),
            EdgeJson("301 GL2/2", 98.47842f)
        )),
        NodeJson("302 foyer8", 204, 325, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer7", 51.66237f),
            EdgeJson("301 entranceSymonds", 43.86342f)
        )),
        NodeJson("302 foyer9", 68, 436, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer6", 51.89412f),
            EdgeJson("302 mainStairs-F0", 69.57011f),
            EdgeJson("302-301 a", 24.16609f)
        )),
        NodeJson("302 mainStairs-F0", 46, 370, 0, "STAIRS", listOf(
            EdgeJson("302 foyer9", 69.57011f),
            EdgeJson("302 foyer5", 48.83646f),
            EdgeJson("302 eleEnt2", 28.84441f),
            EdgeJson("302 mainStairs-F1", 87.20092f)
        )),
        NodeJson("302 eleEnt1", 102, 322, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer5", 25.0f),
            EdgeJson("302 eleB1-F0", 67.00746f)
        )),
        NodeJson("302 eleB1-F0", 155, 281, 0, "ELEVATOR", listOf(
            EdgeJson("302 eleEnt1", 67.00746f)
        )),
        NodeJson("302 eleEnt2", 22, 386, 0, "TRAVEL", listOf(
            EdgeJson("302 mainStairs-F0", 28.84441f),
            EdgeJson("302 eleB2-F0", 49.81967f)
        )),
        NodeJson("302 eleB2-F0", -17, 417, 0, "ELEVATOR", listOf(
            EdgeJson("302 eleEnt2", 49.81967f),
            EdgeJson("302 eleEnt3", 71.44928f)
        )),
        NodeJson("302 eleEnt3", -69, 466, 0, "TRAVEL", listOf(
            EdgeJson("302 eleB2-F0", 71.44928f),
            EdgeJson("catwalk1", 93.8616f)
        )),
        NodeJson("catwalk1", -142, 525, 0, "TRAVEL", listOf(
            EdgeJson("302 eleEnt3", 93.8616f)
        )),
        NodeJson("302-301 a", 78, 458, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer9", 24.16609f),
            EdgeJson("301 hw", 69.2026f)
        )),
        NodeJson("301 hw", 120, 513, 0, "TRAVEL", listOf(
            EdgeJson("302-301 a", 69.2026f),
            EdgeJson("301 entranceSide", 85.0f),
            EdgeJson("301 GL2/1", 13.89244f)
        )),
        NodeJson("301 entranceSide", 171, 581, 0, "TRAVEL", listOf(
            EdgeJson("301 hw", 85.0f)
        )),
        NodeJson("301 GL2/2", 183, 457, 0, "TRAVEL", listOf(
            EdgeJson("302 foyer7", 98.47842f),
            EdgeJson("301 GL2", 32.75668f)
        )),
        NodeJson("301 GL2", 155, 474, 0, "TRAVEL", listOf(
            EdgeJson("301 GL2/2", 32.75668f),
            EdgeJson("301 GL2/1", 38.8973f),
            EdgeJson("301 stairs1-F0", 36.22154f),
            EdgeJson("301 eleB1-F0", 37.48333f)
        )),
        NodeJson("301 GL2/1", 127, 501, 0, "TRAVEL", listOf(
            EdgeJson("301 hw", 13.89244f),
            EdgeJson("301 GL2", 38.8973f)
        )),
        NodeJson("301 stairs1-F0", 151, 438, 0, "STAIRS", listOf(
            EdgeJson("301 GL2", 36.22154f)
        )),
        NodeJson("301 eleB1-F0", 118, 468, 0, "ELEVATOR", listOf(
            EdgeJson("301 GL2", 37.48333f)
        )),
        NodeJson("302 mainStairs-F1", -6, 300, 1, "STAIRS", listOf(
            EdgeJson("302 mainStairs-F0", 87.20092f),
            EdgeJson("302 a1", 26.0f),
            EdgeJson("302 mainStairs-F2", 86.02325f)
        )),
        NodeJson("302 a1", -30, 310, 1, "TRAVEL", listOf(
            EdgeJson("302 mainStairs-F1", 26.0f),
            EdgeJson("302 a2", 94.04786f),
            EdgeJson("302 a9", 90.97252f),
            EdgeJson("302 130", 34.43835f)
        )),
        NodeJson("302 a2", -84, 233, 1, "TRAVEL", listOf(
            EdgeJson("302 a1", 94.04786f),
            EdgeJson("302 a3", 54.56189f),
            EdgeJson("302 a6", 78.77182f),
            EdgeJson("302 a4", 65.27634f)
        )),
        NodeJson("302 a3", -120, 192, 1, "TRAVEL", listOf(
            EdgeJson("302 a2", 54.56189f),
            EdgeJson("302 a4", 38.41875f),
            EdgeJson("302 140", 34.98571f)
        )),
        NodeJson("302 a4", -90, 168, 1, "TRAVEL", listOf(
            EdgeJson("302 a2", 65.27634f),
            EdgeJson("302 a3", 38.41875f),
            EdgeJson("302 a5", 38.41875f),
            EdgeJson("302 150", 34.9285f),
            EdgeJson("302 160", 28.23119f)
        )),
        NodeJson("302 a5", -60, 144, 1, "TRAVEL", listOf(
            EdgeJson("302 a4", 38.41875f),
            EdgeJson("302 a6", 62.28965f),
            EdgeJson("302 170", 41.03657f),
            EdgeJson("302 cornerStairsEnt", 32.01562f)
        )),
        NodeJson("302 a6", -18, 190, 1, "TRAVEL", listOf(
            EdgeJson("302 a2", 78.77182f),
            EdgeJson("302 a5", 62.28965f),
            EdgeJson("302 a7", 32.01562f)
        )),
        NodeJson("302 a7", 7, 210, 1, "TRAVEL", listOf(
            EdgeJson("302 a6", 32.01562f),
            EdgeJson("302 180", 17.46425f),
            EdgeJson("302 190", 19.0263f),
            EdgeJson("302 a8", 146.64924f)
        )),
        NodeJson("302 a8", 98, 325, 1, "TRAVEL", listOf(
            EdgeJson("302 a7", 146.64924f),
            EdgeJson("302 eleB1-F1", 99.04544f),
            EdgeJson("302-301 c", 95.80188f)
        )),
        NodeJson("302 a9", 20, 386, 1, "TRAVEL", listOf(
            EdgeJson("302 a1", 90.97252f),
            EdgeJson("302 eleB2-F1", 45.96738f),
            EdgeJson("302-301 b", 96.33276f)
        )),
        NodeJson("302-301 b", 84, 458, 1, "TRAVEL", listOf(
            EdgeJson("302 a9", 96.33276f)
        )),
        NodeJson("302-301 c", 155, 402, 1, "TRAVEL", listOf(
            EdgeJson("302 a8", 95.80188f)
        )),
        NodeJson("302 eleB1-F1", 179, 268, 1, "ELEVATOR", listOf(
            EdgeJson("302 a8", 99.04544f)
        )),
        NodeJson("302 eleB2-F1", -13, 418, 1, "ELEVATOR", listOf(
            EdgeJson("302 a9", 45.96738f)
        )),
        NodeJson("301 eleB1-F1", 118, 468, 1, "ELEVATOR", listOf()),
        NodeJson("302 130", -61, 325, 1, "ROOM", listOf(
            EdgeJson("302 a1", 34.43835f)
        )),
        NodeJson("302 140", -150, 210, 1, "ROOM", listOf(
            EdgeJson("302 a3", 34.98571f)
        )),
        NodeJson("302 150", -122, 154, 1, "ROOM", listOf(
            EdgeJson("302 a4", 34.9285f)
        )),
        NodeJson("302 160", -101, 142, 1, "ROOM", listOf(
            EdgeJson("302 a4", 28.23119f)
        )),
        NodeJson("302 170", -32, 114, 1, "ROOM", listOf(
            EdgeJson("302 a5", 41.03657f)
        )),
        NodeJson("302 180", 11, 193, 1, "ROOM", listOf(
            EdgeJson("302 a7", 17.46425f)
        )),
        NodeJson("302 190", 26, 209, 1, "ROOM", listOf(
            EdgeJson("302 a7", 19.0263f)
        )),
        NodeJson("302 cornerStairsEnt", -52, 113, 1, "TRAVEL", listOf(
            EdgeJson("302 a5", 32.01562f),
            EdgeJson("302 cornerStairs", 54.03702f)
        )),
        NodeJson("302 cornerStairs", -86, 71, 1, "STAIRS", listOf(
            EdgeJson("302 entranceAlbertSymondsStairs", 3.0f),
            EdgeJson("302 cornerStairsEnt", 54.03702f)
        )),
        NodeJson("302 mainStairs-F2", -56, 230, 2, "STAIRS", listOf(
            EdgeJson("302 mainStairs-F1", 86.02325f),
            EdgeJson("302 b1", 16.97056f)
        )),
        NodeJson("302 b1", -68, 218, 2, "TRAVEL", listOf(
            EdgeJson("302 mainStairs-F2", 16.97056f),
            EdgeJson("302 b2", 36.76955f),
            EdgeJson("302 b5", 54.91812f)
        )),
        NodeJson("302 b2", -82, 252, 2, "TRAVEL", listOf(
            EdgeJson("302 b1", 36.76955f),
            EdgeJson("302 b3", 15.81139f)
        )),
        NodeJson("302 b3", -73, 265, 2, "TRAVEL", listOf(
            EdgeJson("302 b2", 15.81139f),
            EdgeJson("302 b4", 55.22681f),
            EdgeJson("302 232", 14.21267f)
        )),
        NodeJson("302 b4", -36, 306, 2, "TRAVEL", listOf(
            EdgeJson("302 b3", 55.22681f),
            EdgeJson("302 234", 17.69181f)
        )),
        NodeJson("302 232", -84, 274, 2, "ROOM", listOf(
            EdgeJson("302 b3", 14.21267f)
        )),
        NodeJson("302 234", -49, 318, 2, "ROOM", listOf(
            EdgeJson("302 b4", 17.69181f)
        )),
        NodeJson("302 b5", -22, 188, 2, "TRAVEL", listOf(
            EdgeJson("302 b1", 54.91812f),
            EdgeJson("302 b6", 42.42641f)
        )),
        NodeJson("302 b6", 8, 218, 2, "TRAVEL", listOf(
            EdgeJson("302 b5", 42.42641f),
            EdgeJson("302 280", 12.52996f)
        )),
        NodeJson("302 280", 19, 212, 2, "ROOM", listOf(
            EdgeJson("302 b6", 12.52996f)
        ))
    )
}