package com.example.compsci399testproject.machinelearning

/**
 *  A model that predicts floor level, x coordinate and y coordinate.
 *  These predictions are based on wifi signal strength readings.
 * */

import com.example.compsci399testproject.machinelearning.models.FloorRandomForest
import com.example.compsci399testproject.machinelearning.models.XRandomForest
import com.example.compsci399testproject.machinelearning.models.YRandomForest


//////////////////////////////////////////////////////////////////
//                      YOUR CHANGES BELOW                      //
//////////////////////////////////////////////////////////////////
//
// LocationPredictor() class uses the random forest models to find the
// floor, x, and y positions of the user.
class LocationPredictor() {
    companion object{

        //把 FloatArray 转成 DoubleArray
        private fun FloatArray.toDoubleArray(): DoubleArray =
            DoubleArray(size) { this[it].toDouble() }

        //把楼层+特征拼成 DoubleArray（楼层在最前）
        private fun prependFloor(floor: Int, features: FloatArray): DoubleArray {
            val out = DoubleArray(features.size + 1)
            out[0] = floor.toDouble()
            for (i in features.indices) out[i + 1] = features[i].toDouble()
            return out
        }

        fun  predictFloor(input: FloatArray) : Int {
            val predictionScores: DoubleArray = FloorRandomForest.score(input)
            var predictedClassIndex = -1
            var maxScore = Double.NEGATIVE_INFINITY

            predictionScores.forEachIndexed { index, score ->
                if (score > maxScore) {
                    maxScore = score
                    predictedClassIndex = index
                }
            }

            return predictedClassIndex
        }

        fun predictX(input: FloatArray) : Float {
            val floor = predictFloor(input)
            val newInput: DoubleArray = prependFloor(floor, input)
            val prediction : Double = XRandomForest.score(newInput)
            return prediction.toFloat()
        }

        fun predictY(input: FloatArray) : Float {
            val floor = predictFloor(input)
            val newInput: DoubleArray = prependFloor(floor, input)
            val prediction : Double = YRandomForest.score(newInput)
            return prediction.toFloat()
        }
    }
    //////////////////////////////////////////////////////////////////
    //                      YOUR CHANGES ABOVE                      //
    //////////////////////////////////////////////////////////////////

}