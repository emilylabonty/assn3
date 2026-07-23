package command

class SetTrackVelocitiesCommand(
    private val actuator: RobotActuator,
    private val left: Double,
    private val right: Double,
) : Command {
    private var previousLeft = 0.0
    private var previousRight = 0.0

    override fun execute() {
        previousLeft = actuator.leftTrackVelocity
        previousRight = actuator.rightTrackVelocity
        actuator.setTrackVelocities(left, right)
    }

    override fun undo() {
        actuator.setTrackVelocities(previousLeft, previousRight)
    }
}