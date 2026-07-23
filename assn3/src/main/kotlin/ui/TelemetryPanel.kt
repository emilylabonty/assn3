package ui

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import model.Robot
import observer.Observer

/**
 * A live readout of the sensor values — the *consumer* side of the Observer pattern.
 *
 * The layout (labels) is provided. Making it live is your job: in [bindTo] you subscribe an
 * observer to each sensor so the matching label updates when the sensor reports a reading.
 */
class TelemetryPanel : VBox(6.0) {

    private val title = styledLabel("Telemetry", 15.0, bold = true)
    private val sonar = valueLabel()
    private val temperature = valueLabel()
    private val vision = valueLabel()
    private val line = valueLabel()
    private val collision = valueLabel()
    private var boundRobot: Robot? = null

    private val sonarObserver = Observer<Double> { value ->
        sonar.text = "%.0f".format(value)
    }

    private val temperatureObserver = Observer<Double> { value ->
        temperature.text = "%.1f°".format(value)
    }

    private val visionObserver = Observer<Color> { value ->
        vision.text = formatColor(value)
    }

    private val lineObserver = Observer<Boolean> {
        updateLineLabel()
    }

    private val collisionObserver = Observer<Boolean> { value ->
        collision.text = formatBoolean(value)
    }

    init {
        padding = Insets(12.0)
        prefWidth = 210.0
        style = "-fx-background-color: #14171c;"
        children.addAll(
            title,
            captioned("Sonar (distance)", sonar),
            captioned("Temperature", temperature),
            captioned("Vision (color)", vision),
            captioned("Line L / C / R", line),
            captioned("Collision", collision),
        )
    }

    /**
     * Subscribe observers to the given robot's sensors so the labels update live. Called whenever
     * the robot is (re)created — on startup, environment change, and reset.
     *
     * TODO(student): subscribe an observer to each sensor and update the matching label, e.g.:
     * You can change the text of one of the Labels above by modifying the `text` property,
     * e.g: `vision.text = "The new text to display"`
     *
     * The labels (`sonar`, `temperature`, `vision`, `line`, `collision`) are ready to write to.
     * Until you do this, they stay "—". (This depends on your Observer pattern working — see
     * AbstractSubject.)
     */
    fun bindTo(robot: Robot) {
        boundRobot?.let { oldRobot ->
            oldRobot.sonar.unsubscribe(sonarObserver)
            oldRobot.temperature.unsubscribe(temperatureObserver)
            oldRobot.vision.unsubscribe(visionObserver)
            oldRobot.lineLeft.unsubscribe(lineObserver)
            oldRobot.lineCenter.unsubscribe(lineObserver)
            oldRobot.lineRight.unsubscribe(lineObserver)
            oldRobot.collision.unsubscribe(collisionObserver)
        }

        boundRobot = robot

        sonar.text = robot.sonar.reading?.let { "%.0f".format(it) } ?: "—"
        temperature.text = robot.temperature.reading?.let { "%.1f°".format(it) } ?: "—"
        vision.text = robot.vision.reading?.let(::formatColor) ?: "—"
        updateLineLabel()
        collision.text = robot.collision.reading?.let(::formatBoolean) ?: "—"

        robot.sonar.subscribe(sonarObserver)
        robot.temperature.subscribe(temperatureObserver)
        robot.vision.subscribe(visionObserver)
        robot.lineLeft.subscribe(lineObserver)
        robot.lineCenter.subscribe(lineObserver)
        robot.lineRight.subscribe(lineObserver)
        robot.collision.subscribe(collisionObserver)
    }

    private fun captioned(caption: String, value: Label): VBox =
        VBox(2.0, styledLabel(caption, 11.0, color = "#8b949e"), value)

    private fun valueLabel() = styledLabel("—", 18.0, bold = true)

    private fun styledLabel(text: String, size: Double, bold: Boolean = false, color: String = "#e6edf3"): Label =
        Label(text).apply {
            style = "-fx-font-size: ${size}px; -fx-text-fill: $color;" +
                if (bold) " -fx-font-weight: bold;" else ""
        }
}

private fun formatBoolean(value: Boolean) = if (value) "Yes" else "No"

private fun formatColor(color: Color): String =
    when {
        color.red > 0.70 && color.green < 0.35 && color.blue < 0.35 -> "Red"
        color.red > 0.80 && color.green > 0.70 && color.blue < 0.35 -> "Line"
        else -> "Floor"
    }

private fun updateLineLabel() {
    val robot = boundRobot

    val left = robot?.lineLeft?.reading?.let(::formatBoolean) ?: "—"
    val center = robot?.lineCenter?.reading?.let(::formatBoolean) ?: "—"
    val right = robot?.lineRight?.reading?.let(::formatBoolean) ?: "—"

    line.text = "$left / $center / $right"
}