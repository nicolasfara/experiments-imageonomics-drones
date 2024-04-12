package it.unibo.alchemist.boundary.extractors

import it.unibo.alchemist.model.*
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.experiment.toBoolean
import kotlin.math.*

class NoisePerceived<T>(
    override val node: Node<T>,
    private val environment: Environment<T, *>,
    // Parameters for sound computation
    private val decibelEmitted: Double,
    private val distanceMeasurementFromSource: Double,
    private val droneHeight: Double,
    private val hearingThreshold: Double,
): NodeProperty<T> {

    companion object {
        val noisePerceivedMolecule = SimpleMolecule("NoisePerceived")
    }

    private val droneMolecule = SimpleMolecule("drone")
    private val soundMetricCalculator = SoundMetricCalculator()

    fun computeSoundMetric(): Double {
        val drones = environment.nodes.filter { it.contains(droneMolecule) }

        val pressures = drones.map { drone ->
            val distanceHypoten = environment.distanceCameraToZebra(node, drone, droneHeight)
            soundMetricCalculator.dispersionOfSoundDecibel(
                decibelEmitted,
                distanceMeasurementFromSource,
                distanceHypoten,
            )
        }
        return soundMetricCalculator.perceivedSoundLevelForZebras(
            soundMetricCalculator.sumOfSoundPressures(pressures),
            hearingThreshold,
        )
    }

    private fun <T> Environment<T, *>.distanceCameraToZebra(camera: Node<T>, zebra: Node<T>, height: Double): Double {
        val onGroundDistance = getDistanceBetweenNodes(camera, zebra)
        return hypot(onGroundDistance, height)
    }

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = this
}
