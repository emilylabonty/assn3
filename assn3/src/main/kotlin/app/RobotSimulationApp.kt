package app

import api.DefaultProgramRegistry
import api.DefaultRobotApi
import api.StudentPrograms
import command.CommandInvoker
import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import sim.EnvironmentCatalog
import sim.ProgramRunner
import sim.Simulation
import ui.ControlPanel
import ui.ProgramPanel
import ui.SimulationCanvas
import ui.TelemetryPanel

/** Wires the model, the application interface, and the JavaFX UI together and runs the sim loop. */
class RobotSimulationApp : Application() {

    private val worldWidth = 880.0
    private val worldHeight = 600.0

    private val simulation = Simulation(EnvironmentCatalog.all().first())
    private val invoker = CommandInvoker()
    private val api = DefaultRobotApi(
        invoker = invoker,
        actuatorProvider = { simulation.robot },
        sensorsProvider = { simulation.robot },
    )
    private val programRunner = ProgramRunner(api)

    private val telemetry = TelemetryPanel()
    private lateinit var canvas: SimulationCanvas

    private var lastNanos = -1L

    override fun start(stage: Stage) {
        val registry = DefaultProgramRegistry()
        StudentPrograms.registerAll(registry)

        canvas = SimulationCanvas(simulation, programRunner, worldWidth, worldHeight)
        telemetry.bindTo(simulation.robot)

        val controlPanel = ControlPanel(
            api = api,
            environments = EnvironmentCatalog.all(),
            onSelectEnvironment = { env -> switchEnvironment(env.javaClass) },
            onReset = { resetRobot() },
        )
        val programPanel = ProgramPanel(registry, programRunner)

        val root = BorderPane().apply {
            center = canvas
            right = telemetry
            bottom = VBox(programPanel, controlPanel)
        }

        val scene = Scene(root)
        installKeyboardControls(scene)
        stage.title = "Skid-Steer Robot Simulation"
        stage.scene = scene
        stage.show()

        object : AnimationTimer() {
            override fun handle(now: Long) {
                val dt = if (lastNanos < 0) 0.0 else ((now - lastNanos) / 1_000_000_000.0).coerceAtMost(0.033)
                lastNanos = now
                if (dt > 0.0) {
                    // A running program reacts through its sensor subscriptions, which fire from
                    // simulation.step -> robot.updateSensors below. No explicit program tick needed.
                    simulation.step(dt)
                }
                canvas.render()
            }
        }.start()
    }

    private fun installKeyboardControls(scene: Scene) {
        val speed = 120.0
        val turn = 90.0
        scene.setOnKeyPressed { e ->
            when (e.code) {
                KeyCode.UP -> drive(speed, speed)
                KeyCode.DOWN -> drive(-speed, -speed)
                KeyCode.LEFT -> drive(turn, -turn)
                KeyCode.RIGHT -> drive(-turn, turn)
                KeyCode.SPACE -> drive(0.0, 0.0)
                else -> {}
            }
        }
    }

    private fun drive(left: Double, right: Double) {
        // TODO(student): keyboard control — build one of your commands and run it via the API:
        //     api.perform(MySetVelocityCommand(api.actuator, left, right))
        // (Same idea as the on-screen buttons in ControlPanel.)
    }

    private fun switchEnvironment(envClass: Class<*>) {
        val env = EnvironmentCatalog.all().first { it.javaClass == envClass }
        programRunner.stop()
        simulation.loadEnvironment(env)
        telemetry.bindTo(simulation.robot)
    }

    private fun resetRobot() {
        programRunner.stop()
        simulation.resetRobot()
        telemetry.bindTo(simulation.robot)
    }
}
