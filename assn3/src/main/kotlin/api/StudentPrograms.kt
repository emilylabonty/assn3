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
import command.RobotActuator
import javafx.scene.paint.Color
import observer.Observer

object StudentPrograms {
    fun registerAll(registry: ProgramRegistry) {
        registry.register(LineMazeProgram())
        registry.register(TemperatureGradientProgram())
        registry.register(RedBallFinderProgram())
    }
}


private class LineMazeProgram : RobotProgram {
    override val name = "Line Maze Follower"

    private val drive = ProgramDrive()
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
        drive.reset()
        robot.sensors.lineLeft.subscribe(leftObserver)
        robot.sensors.lineCenter.subscribe(centerObserver)
        robot.sensors.lineRight.subscribe(rightObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.lineLeft.unsubscribe(leftObserver)
        robot.sensors.lineCenter.unsubscribe(centerObserver)
        robot.sensors.lineRight.unsubscribe(rightObserver)
        drive.stop(robot)
        this.robot = null
    }

    private fun drive() {
        val robot = robot ?: return
        val speeds = when {
            centerOnLine -> 95.0 to 95.0
            leftOnLine -> 45.0 to 110.0
            rightOnLine -> 110.0 to 45.0
            else -> 65.0 to -65.0
        }

        drive.set(robot, speeds.first, speeds.second)
    }
}

private class TemperatureGradientProgram : RobotProgram {
    override val name = "Temperature Seeker"

    private val drive = ProgramDrive()
    private var robot: RobotApi? = null
    private var lastTemperature: Double? = null
    private var turningTicks = 0

    private val temperatureObserver = Observer<Double> { temperature ->
        val robot = robot ?: return@Observer

        if (temperature >= 92.0) {
            drive.stop(robot)
            this.robot = null
            return@Observer
        }

        val previous = lastTemperature
        lastTemperature = temperature

        val speeds = when {
            previous == null -> 100.0 to 100.0
            temperature >= previous - 0.1 -> {
                turningTicks = 0
                110.0 to 110.0
            }
            turningTicks < 18 -> {
                turningTicks++
                75.0 to -75.0
            }
            else -> {
                turningTicks = 0
                90.0 to 90.0
            }
        }

        robot.perform(SetTrackVelocitiesCommand(robot.actuator, speeds.first, speeds.second))
    }

    override fun startProgram(robot: RobotApi) {
        this.robot = robot
        lastTemperature = null
        turningTicks = 0
        robot.sensors.temperature.subscribe(temperatureObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.temperature.unsubscribe(temperatureObserver)
        drive.stop(robot)
        this.robot = null
    }
}

private class RedBallFinderProgram : RobotProgram {
    override val name = "Red Ball Finder"

    private val drive = ProgramDrive()
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
        robot.sensors.vision.subscribe(visionObserver)
        robot.sensors.sonar.subscribe(sonarObserver)
        robot.sensors.collision.subscribe(collisionObserver)
    }

    override fun stopProgram(robot: RobotApi) {
        robot.sensors.vision.unsubscribe(visionObserver)
        robot.sensors.sonar.unsubscribe(sonarObserver)
        robot.sensors.collision.unsubscribe(collisionObserver)
        drive.stop(robot)
        this.robot = null
    }

    private fun drive() {
        val robot = robot ?: return
        val speeds = when {
            seesRed -> 100.0 to 100.0
            colliding -> {
                avoidTicks = 18
                -80.0 to 90.0
            }
            avoidTicks > 0 -> {
                avoidTicks--
                75.0 to -75.0
            }
            sonarDistance < 55.0 -> {
                avoidTicks = 14
                80.0 to -80.0
            }
            sonarDistance < 120.0 -> 55.0 to 105.0
            else -> 115.0 to 80.0
        }

        drive.set(robot, speeds.first, speeds.second)
    }

    private fun isRed(color: Color): Boolean =
        color.red > 0.70 && color.green < 0.35 && color.blue < 0.35
}

private class ProgramDrive {
    private var lastLeft: Double? = null
    private var lastRight: Double? = null

    fun set(robot: RobotApi, left: Double, right: Double) {
        if (lastLeft == left && lastRight == right) return

        lastLeft = left
        lastRight = right
        drive.set(robot, speeds.first, speeds.second)
    }

    fun stop(robot: RobotApi) {
        set(robot, 0.0, 0.0)
    }

    fun reset() {
        lastLeft = null
        lastRight = null
    }
}

