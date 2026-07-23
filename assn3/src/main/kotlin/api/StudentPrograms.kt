package api

/**
 * The one place programs are registered with the system. Each program you register shows up in the
 * "Program" dropdown and can be launched with "Run Program".
 *
 * TODO(student): write one or more [RobotProgram] implementations. Each one, in `startProgram`,
 * subscribes to the sensors it needs (`robot.sensors.…`) and issues commands in the observer
 * callbacks; in `stopProgram` it unsubscribes and stops the robot. Then register them here, e.g.:
 *
 *     registry.register(MyLineFollowerProgram())
 *     registry.register(MyBallFinderProgram())
 *
 * Until you register a program, the dropdown shows "(no programs registered)".
 */

import command.Command
import command.SetTrackVelocitiesCommand
import javafx.scene.paint.Color
import observer.Observer

package api

import command.Command
import command.SetTrackVelocitiesCommand
import javafx.scene.paint.Color
import observer.Observer

private data class DriveAction(
    val left: Double,
    val right: Double,
)

private class DriveController(private val robot: RobotApi) {
    private var lastAction: DriveAction? = null

    fun setSpeeds(left: Double, right: Double) {
        perform(DriveAction(left, right))
    }

    fun forward(speed: Double) {
        setSpeeds(speed, speed)
    }

    fun stop() {
        setSpeeds(0.0, 0.0)
    }

    fun spinLeft(speed: Double) {
        setSpeeds(speed, -speed)
    }

    fun veerLeft(innerSpeed: Double, outerSpeed: Double) {
        setSpeeds(innerSpeed, outerSpeed)
    }

    fun veerRight(outerSpeed: Double, innerSpeed: Double) {
        setSpeeds(outerSpeed, innerSpeed)
    }

    fun backAndTurn(left: Double, right: Double) {
        setSpeeds(left, right)
    }

    private fun perform(action: DriveAction) {
        if (action == lastAction) return

        lastAction = action
        robot.perform(commandFor(action))
    }

    private fun commandFor(action: DriveAction): Command =
        SetTrackVelocitiesCommand(robot.actuator, action.left, action.right)
}

private class LineMazeProgram : RobotProgram {
    override val name = "Line Maze Follower"

    private var driver: DriveController? = null
    private var leftOnLine = false
    private var centerOnLine = false
    private var rightOnLine = false

    private val leftObserver = Observer<Boolean> {
        leftOnLine = it
        drive()
    }

    private val centerObserver = Observer<Boolean> {
        centerOnLine = it
        drive()
    }

    private val rightObserver = Observer<Boolean> {
        rightOnLine = it
        drive()
    }

    override fun startProgram(robot: RobotApi) {
        driver = DriveController(robot)
        robot.sensors.lineLeft.subscribe(leftObserver)
        robot.sensors.lineCenter.subscribe(centerObserver)
        robot.sensors.lineRight.subscribe(rightObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.lineLeft.unsubscribe(leftObserver)
        robot.sensors.lineCenter.unsubscribe(centerObserver)
        robot.sensors.lineRight.unsubscribe(rightObserver)
        driver?.stop()
        driver = null
    }

    private fun drive() {
        val driver = driver ?: return

        when {
            centerOnLine -> driver.forward(95.0)
            leftOnLine -> driver.veerLeft(innerSpeed = 45.0, outerSpeed = 110.0)
            rightOnLine -> driver.veerRight(outerSpeed = 110.0, innerSpeed = 45.0)
            else -> driver.spinLeft(65.0)
        }
    }
}

private class TemperatureGradientProgram : RobotProgram {
    override val name = "Temperature Seeker"

    private var driver: DriveController? = null
    private var lastTemperature: Double? = null
    private var turningTicks = 0

    private val temperatureObserver = Observer<Double> { temperature ->
        val driver = driver ?: return@Observer

        if (temperature >= 92.0) {
            driver.stop()
            return@Observer
        }

        val previous = lastTemperature
        lastTemperature = temperature

        when {
            previous == null -> driver.forward(100.0)

            temperature >= previous - 0.1 -> {
                turningTicks = 0
                driver.forward(110.0)
            }

            turningTicks < 18 -> {
                turningTicks++
                driver.spinLeft(75.0)
            }

            else -> {
                turningTicks = 0
                driver.forward(90.0)
            }
        }
    }

    override fun startProgram(robot: RobotApi) {
        driver = DriveController(robot)
        lastTemperature = null
        turningTicks = 0
        robot.sensors.temperature.subscribe(temperatureObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.temperature.unsubscribe(temperatureObserver)
        driver?.stop()
        driver = null
    }
}

private class RedBallFinderProgram : RobotProgram {
    override val name = "Red Ball Finder"

    private var driver: DriveController? = null
    private var seesRed = false
    private var sonarDistance = 320.0
    private var colliding = false
    private var avoidTicks = 0

    private val visionObserver = Observer<Color> {
        seesRed = isRed(it)
        drive()
    }

    private val sonarObserver = Observer<Double> {
        sonarDistance = it
        drive()
    }

    private val collisionObserver = Observer<Boolean> {
        colliding = it
        drive()
    }

    override fun startProgram(robot: RobotApi) {
        driver = DriveController(robot)
        robot.sensors.vision.subscribe(visionObserver)
        robot.sensors.sonar.subscribe(sonarObserver)
        robot.sensors.collision.subscribe(collisionObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.vision.unsubscribe(visionObserver)
        robot.sensors.sonar.unsubscribe(sonarObserver)
        robot.sensors.collision.unsubscribe(collisionObserver)
        driver?.stop()
        driver = null
    }

    private fun drive() {
        val driver = driver ?: return

        when {
            seesRed -> driver.forward(100.0)

            colliding -> {
                avoidTicks = 18
                driver.backAndTurn(left = -80.0, right = 90.0)
            }

            avoidTicks > 0 -> {
                avoidTicks--
                driver.spinLeft(75.0)
            }

            sonarDistance < 55.0 -> {
                avoidTicks = 14
                driver.spinLeft(80.0)
            }

            sonarDistance < 120.0 -> driver.veerLeft(innerSpeed = 55.0, outerSpeed = 105.0)

            else -> driver.veerRight(outerSpeed = 115.0, innerSpeed = 80.0)
        }
    }

    private fun isRed(color: Color): Boolean =
        color.red > 0.70 && color.green < 0.35 && color.blue < 0.35
}

object StudentPrograms {
    fun registerAll(registry: ProgramRegistry) {
        registry.register(LineMazeProgram())
        registry.register(TemperatureGradientProgram())
        registry.register(RedBallFinderProgram())
    }
}

