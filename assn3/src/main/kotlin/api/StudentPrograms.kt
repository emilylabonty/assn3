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

private class DriveCommandFactory(private val robot: RobotApi) {
    fun setSpeeds(left: Double, right: Double): Command =
        SetTrackVelocitiesCommand(robot.actuator, left, right)

    fun forward(speed: Double): Command =
        setSpeeds(speed, speed)

    fun stop(): Command =
        setSpeeds(0.0, 0.0)

    fun spinLeft(speed: Double): Command =
        setSpeeds(speed, -speed)

    fun spinRight(speed: Double): Command =
        setSpeeds(-speed, speed)

    fun veerLeft(innerSpeed: Double, outerSpeed: Double): Command =
        setSpeeds(innerSpeed, outerSpeed)

    fun veerRight(outerSpeed: Double, innerSpeed: Double): Command =
        setSpeeds(outerSpeed, innerSpeed)

    fun backAndTurn(left: Double, right: Double): Command =
        setSpeeds(left, right)
}

private class ProgramDrive {
    private var lastLeft: Double? = null
    private var lastRight: Double? = null

    fun setSpeeds(robot: RobotApi, left: Double, right: Double) {
        if (lastLeft == left && lastRight == right) return

        lastLeft = left
        lastRight = right
        robot.perform(DriveCommandFactory(robot).setSpeeds(left, right))
    }

    fun forward(robot: RobotApi, speed: Double) {
        setSpeeds(robot, speed, speed)
    }

    fun stop(robot: RobotApi) {
        setSpeeds(robot, 0.0, 0.0)
    }

    fun spinLeft(robot: RobotApi, speed: Double) {
        setSpeeds(robot, speed, -speed)
    }

    fun spinRight(robot: RobotApi, speed: Double) {
        setSpeeds(robot, -speed, speed)
    }

    fun veerLeft(robot: RobotApi, innerSpeed: Double, outerSpeed: Double) {
        setSpeeds(robot, innerSpeed, outerSpeed)
    }

    fun veerRight(robot: RobotApi, outerSpeed: Double, innerSpeed: Double) {
        setSpeeds(robot, outerSpeed, innerSpeed)
    }

    fun backAndTurn(robot: RobotApi, left: Double, right: Double) {
        setSpeeds(robot, left, right)
    }

    fun reset() {
        lastLeft = null
        lastRight = null
    }
}

private class LineMazeProgram : RobotProgram {
    override val name = "Line Maze Follower"

    private val driver = ProgramDrive()

    private var robot: RobotApi? = null
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
        this.robot = robot
        driver.reset()
        robot.sensors.lineLeft.subscribe(leftObserver)
        robot.sensors.lineCenter.subscribe(centerObserver)
        robot.sensors.lineRight.subscribe(rightObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.lineLeft.unsubscribe(leftObserver)
        robot.sensors.lineCenter.unsubscribe(centerObserver)
        robot.sensors.lineRight.unsubscribe(rightObserver)
        driver.stop(robot)
        this.robot = null
    }

    private fun drive() {
        val robot = robot ?: return

        when {
            centerOnLine -> driver.forward(robot, 95.0)
            leftOnLine -> driver.veerLeft(robot, innerSpeed = 45.0, outerSpeed = 110.0)
            rightOnLine -> driver.veerRight(robot, outerSpeed = 110.0, innerSpeed = 45.0)
            else -> driver.spinLeft(robot, 65.0)
        }
    }
}

private class TemperatureGradientProgram : RobotProgram {
    override val name = "Temperature Seeker"

    private val driver = ProgramDrive()

    private var robot: RobotApi? = null
    private var lastTemperature: Double? = null
    private var turningTicks = 0

    private val temperatureObserver = Observer<Double> { temperature ->
        val robot = robot ?: return@Observer

        if (temperature >= 92.0) {
            driver.stop(robot)
            return@Observer
        }

        val previous = lastTemperature
        lastTemperature = temperature

        when {
            previous == null -> driver.forward(robot, 100.0)

            temperature >= previous - 0.1 -> {
                turningTicks = 0
                driver.forward(robot, 110.0)
            }

            turningTicks < 18 -> {
                turningTicks++
                driver.spinLeft(robot, 75.0)
            }

            else -> {
                turningTicks = 0
                driver.forward(robot, 90.0)
            }
        }
    }

    override fun startProgram(robot: RobotApi) {
        this.robot = robot
        lastTemperature = null
        turningTicks = 0
        driver.reset()
        robot.sensors.temperature.subscribe(temperatureObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.temperature.unsubscribe(temperatureObserver)
        driver.stop(robot)
        this.robot = null
    }
}

private class RedBallFinderProgram : RobotProgram {
    override val name = "Red Ball Finder"

    private val driver = ProgramDrive()

    private var robot: RobotApi? = null
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
        this.robot = robot
        driver.reset()
        robot.sensors.vision.subscribe(visionObserver)
        robot.sensors.sonar.subscribe(sonarObserver)
        robot.sensors.collision.subscribe(collisionObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.vision.unsubscribe(visionObserver)
        robot.sensors.sonar.unsubscribe(sonarObserver)
        robot.sensors.collision.unsubscribe(collisionObserver)
        driver.stop(robot)
        this.robot = null
    }

    private fun drive() {
        val robot = robot ?: return

        when {
            seesRed -> driver.forward(robot, 100.0)

            colliding -> {
                avoidTicks = 18
                driver.backAndTurn(robot, left = -80.0, right = 90.0)
            }

            avoidTicks > 0 -> {
                avoidTicks--
                driver.spinLeft(robot, 75.0)
            }

            sonarDistance < 55.0 -> {
                avoidTicks = 14
                driver.spinLeft(robot, 80.0)
            }

            sonarDistance < 120.0 -> driver.veerLeft(robot, innerSpeed = 55.0, outerSpeed = 105.0)

            else -> driver.veerRight(robot, outerSpeed = 115.0, innerSpeed = 80.0)
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