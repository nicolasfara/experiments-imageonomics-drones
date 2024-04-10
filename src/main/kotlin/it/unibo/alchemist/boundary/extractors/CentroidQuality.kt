package it.unibo.alchemist.boundary.extractors

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.VisibleNode
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.physics.environments.Physics2DEnvironment
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.experiment.toBoolean
import org.locationtech.jts.algorithm.Centroid
import org.locationtech.jts.awt.ShapeReader
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.math.Vector2D

class CentroidQuality<T>(
    private val environment: Physics2DEnvironment<T>,
    override val node: Node<T>,
    visionMoleculeName: String,
    targetMoleculeName: String,
): NodeProperty<T> {
    private val visionMolecule by lazy { SimpleMolecule(visionMoleculeName) }
    private val targetMolecule by lazy { SimpleMolecule(targetMoleculeName) }
    private val geometry by lazy { GeometryFactory() }
    private val metricCalculator = CentroidQualityMetricCalculator()

    companion object {
        val bodyCoverageMolecule = SimpleMolecule("CentroidQuality")
        private val flatness = 1.0
    }

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = CentroidQuality(environment, node, visionMolecule.name, targetMolecule.name)

    fun computeCentroidQuality(): Double {
        val nodes = environment.nodes
        val visibleCameras = nodes.filter { n -> n.isCamera() && n.getVisibleTargets().map { it.node }.contains(node) }
        return if(node.isTarget()) {
            metricCalculator.computeQualityMetric(
                environment.getPosition(node).asCoordinate(),
                visibleCameras.map { it.properties.filterIsInstance<CameraWithBlindSpot<Any>>().firstOrNull()
                    ?.asCameraQualityInformation()
                    ?: error("Property ${CameraWithBlindSpot::class} not found.") }
            )
        } else {
            Double.NaN
        }
    }
    private fun Node<*>.isTarget() = contains(targetMolecule) && getConcentration(targetMolecule).toBoolean()

    private fun Node<*>.isCamera() = contains(visionMolecule)

    private fun Node<T>.getVisibleTargets() =
        with(getConcentration(visionMolecule)) {
            require(this is List<*>) { "Expected a List but got $this" }
            if (isNotEmpty()) {
                get(0)?.also {
                    require(it is VisibleNode<*, *>) {
                        "Expected a List<VisibleNode> but got List<${it::class}> = $this"
                    }
                }
            }
            @Suppress("UNCHECKED_CAST")
            (this as Iterable<VisibleNode<T, *>>).filter { it.node.isTarget() }
        }

    private fun Euclidean2DPosition.asCoordinate(): Coordinate = Coordinate(x, y)
    private fun CameraWithBlindSpot<*>.geometryRepresentation(): Geometry =
        ShapeReader.read(transformShapeToEnvironmentPosition(), flatness, geometry)

    private fun CameraWithBlindSpot<*>.centroid(): Coordinate =
        Centroid.getCentroid(geometryRepresentation())
    private fun CameraWithBlindSpot<*>.worstCaseCoordinateVector(): Coordinate {
        val cameraShape = this.geometryRepresentation()
        val centroid = this.centroid()
        return cameraShape.coordinates.map { it to it.distance(centroid) }
            .maxByOrNull { it.second }
            ?.let { (coordinate, _) -> coordinate }
            ?: error("It should have at least one coordinate")
    }
    private fun CameraWithBlindSpot<Any>.asCameraQualityInformation(): CameraQualityInformation = this.centroid() to this.worstCaseCoordinateVector()
}