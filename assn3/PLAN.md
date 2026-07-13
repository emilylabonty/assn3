# Homework 3 — Skid-Steer Robot Simulation

**Course:** CS 5700 – Object-Oriented Design
**Patterns taught:** Observer, Command
**Language / platform:** Kotlin (JVM 25), Gradle (Kotlin DSL), **JavaFX** UI

---

## 1. Learning objectives

By completing this assignment students will:

1. **Implement the Observer pattern** to decouple *sources of change* (the robot's
   sensors and pose) from *reactors to change* (on-screen telemetry and their own
   autonomous behaviors). Sensors are **Subjects**; students write **Observers** that
   subscribe to them.
2. **Implement the Command pattern** to decouple the callers (the controller UI and their
   own programs) from the *robot* (the receiver). Students **design their own set of
   Command classes** — the starter does not prescribe them — and an **Invoker** executes
   them with a history stack supporting **undo / redo**.
3. **Program the robot through a provided application interface** (`RobotApi`): given the
   current input (sensor readings + pose), decide a set of actions and submit them. This
   is the seam where the two patterns compose — a sensor (Subject) delivers input to an
   Observer, which responds by performing Commands through the API. That closed loop is
   the heart of reactive/robotic control.

> **What is provided vs. what students build.** The starter code is a *fully runnable*
> application: the simulation physics, all three environments, all four sensor
> *reading computations*, the JavaFX window, the canvas renderer, the control-panel
> layout, and the **`RobotApi` application interface** are complete. What is intentionally
> **stubbed** (with `// TODO(student)` comments and harmless no-op bodies so the app still
> compiles and runs) is the **pattern code**: the Observer subscription mechanism, the
> Command invoker, the **Command classes students design themselves**, the button wiring,
> and the `RobotProgram` they write. Students bring the app to life by filling those in.

---

## 2. Tech stack & build changes

Current `build.gradle.kts` is minimal (Kotlin JVM + `kotlin("test")`). We add the
**application** plugin (to `./gradlew run`) and the **JavaFX** plugin.

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(25) }

javafx {
    version = "25"                       // verify vs. JDK 25 at implementation time
    modules = listOf("javafx.controls", "javafx.graphics")
}

application { mainClass.set("MainKt") }

tasks.test { useJUnitPlatform() }
```

- **Run:** `./gradlew run`
- **Test:** `./gradlew test`
- Version numbers (JavaFX `25`, plugin `0.1.0`) are pinned during implementation and
  verified against the JDK 25 toolchain; noted here as an open item (§13).

---

## 3. High-level architecture

```
                    ┌──────────────────────────────────────────────┐
                    │            JavaFX Application (app/)           │
                    │  AnimationTimer  ──tick(dt)──►  Simulation     │
                    └───────────────┬──────────────────────────────┘
                                    │ step(dt)
                    ┌───────────────▼───────────────┐
                    │        Simulation (sim/)       │
                    │  Environment + Robot + goal    │
                    └───────┬───────────────┬────────┘
                            │               │
              ┌─────────────▼──┐      ┌──────▼─────────────────┐
              │  Environment    │      │        Robot           │
              │ obstacles/lines │◄─────│ pose, trackVelocities  │
              │ heat field/ball │ read │ sensors[]              │
              └─────────────────┘      └──────┬─────────────────┘
                                              │ each sensor updates its
                                              │ reading, then (Observer)
                                              │ notifies subscribers
                        ┌─────────────────────┼───────────────────────┐
                        ▼                     ▼                        ▼
                 SonarSensor           TemperatureSensor        LineSensor / Vision
              (Subject<Double>)        (Subject<Double>)        (Subject<…>)
                        │                     │                        │
                        └──────── students' Observers subscribe ───────┘
                                       │
                        ┌──────────────┴───────────────┐
                        ▼                               ▼
                 TelemetryPanel (ui/)          student's RobotProgram
                 updates labels                reads input → robot.perform(actions)

   Action path (Command pattern), shared by manual + autonomous clients:
     ControlPanel button  ─┐
                           ├─► RobotApi.perform(command) ──► CommandInvoker.run(cmd)
     RobotProgram.step(..) ─┘                                   │
                                                                ├─ cmd.execute()  (student-defined)
                                                                └─ push to history (undo/redo)
```

**Two independent concerns, deliberately separated:**

- **Rendering** is per-frame: the `AnimationTimer` repaints the whole canvas every
  frame from the current world state. Rendering is *not* the Observer lesson — trying
  to force it there would be contrived.
- **Reacting to sensor readings** *is* the Observer lesson: telemetry labels and any
  autonomous behavior only update because they are subscribed observers. Before a
  student implements the subscription mechanism, the telemetry stays blank — a visible,
  motivating "why do I need this pattern?" moment.

---

## 4. The two patterns, mapped to this project

### 4.1 Observer — sensors are Subjects

```kotlin
// observer/Observer.kt  (PROVIDED interface)
fun interface Observer<T> {
    fun onUpdate(value: T)
}

// observer/Subject.kt  (interface PROVIDED; AbstractSubject body is STUDENT TODO)
interface Subject<T> {
    fun subscribe(observer: Observer<T>)
    fun unsubscribe(observer: Observer<T>)
    fun notifyObservers(value: T)
}

// observer/AbstractSubject.kt  (STUDENT TODO — the core of the pattern)
abstract class AbstractSubject<T> : Subject<T> {
    private val observers = mutableListOf<Observer<T>>()
    override fun subscribe(observer: Observer<T>)   { /* TODO(student) */ }
    override fun unsubscribe(observer: Observer<T>) { /* TODO(student) */ }
    override fun notifyObservers(value: T)          { /* TODO(student) */ }
}
```

- Each **sensor** extends `AbstractSubject<ReadingType>`. The starter *computes* the
  reading each tick (provided) and calls `notifyObservers(reading)` **every tick** so a
  subscriber has a steady heartbeat (a program driven purely by subscriptions needs one).
  Once students implement `AbstractSubject`, every subscribed observer starts receiving
  values.
- Students write concrete **Observers**: e.g., a `LabelObserver` that writes a sensor
  value into a JavaFX `Label` (telemetry), and — inside their `RobotProgram` — control
  observers that turn readings into commands.
- Because readings have different types (`Double` distance, `Color`, `Boolean` on-line,
  temperature `Double`), the generic `Subject<T>` keeps each subscription type-safe.

### 4.2 Command — actions are objects the invoker runs

```kotlin
// command/Command.kt  (PROVIDED interface)
interface Command {
    fun execute()
    fun undo()
}

// command/CommandInvoker.kt  (STUBBED — STUDENT TODO)
class CommandInvoker {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()
    fun run(command: Command) { /* TODO: execute, push to undo, clear redo */ }
    fun undo()                { /* TODO: pop undo, cmd.undo(), push to redo */ }
    fun redo()                { /* TODO: pop redo, cmd.execute(), push to undo */ }
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
}
```

- **We do not prescribe the command set.** The starter ships only the `Command`
  interface and the invoker. *Students decide which concrete commands to create* and how
  to name them — that design choice is part of the assignment. (The control panel's
  button labels hint at intent, but students choose the classes behind them.)
- The **receiver** is the `Robot`; the true actuators are its track velocities. A typical
  command captures the robot's previous velocities in `execute()` and restores them in
  `undo()` — but students own that design.
- `undo()` reverses the state a command changed (e.g., track velocities). Rewinding
  *position* is intentionally out of scope; noted as an extension in §11.

### 4.3 The application interface — how students program the robot

We provide a stable **application interface** students program against. It is the seam
where the two patterns meet: a program **subscribes to the sensors it needs** (Observer)
and, in the callbacks, **performs Commands**. The program *is* an Observer; there is no
pushed-input parameter and no per-tick callback — a subscribed sensor notifies every tick,
so the sensor stream is the program's own control loop.

```kotlin
// sensor/RobotSensors.kt  (PROVIDED) — the sensor suite a program may subscribe to.
// Each sensor is a Subject<T> (and exposes its latest reading).
interface RobotSensors {
    val sonar: Sensor<Double>
    val vision: Sensor<Color>
    val temperature: Sensor<Double>
    val lineLeft: Sensor<Boolean>
    val lineCenter: Sensor<Boolean>
    val lineRight: Sensor<Boolean>
    val collision: Sensor<Boolean>   // true while the robot is bumping an obstacle/wall
}

// api/RobotApi.kt  (PROVIDED) — the application interface students call
interface RobotApi {
    val sensors: RobotSensors                // subscribe to the ones you need (Observer)
    val actuator: RobotActuator              // build commands against the receiver
    fun perform(command: Command)            // run one action  (routes through invoker → undoable)
    fun perform(commands: List<Command>)     // run a *set* of actions, in order
    fun undo()
    fun redo()
}

// api/RobotProgram.kt  (PROVIDED interface; students implement their own program)
interface RobotProgram {
    val name: String                         // shown in the "run program" dropdown
    fun startProgram(robot: RobotApi)        // subscribe to sensors here, and start driving
    fun stopProgram(robot: RobotApi)         // unsubscribe here, and stop the robot
}
```

- `RobotApi` is a thin **facade over the invoker** — every `perform(...)` runs the
  student's Command through `CommandInvoker`, so manual clicks and autonomous programs
  share one undoable action path. The facade is provided; it comes alive once students
  implement their commands and the invoker.
- **Sensors notify every tick** (not only on change), so subscribing to one sensor gives a
  program a steady heartbeat to act on. A program that needs several sensors caches each
  one's value in its observers and recomputes when any fires.
- The **ControlPanel** (manual control) and a student **`RobotProgram`** (autonomous
  control) are just two clients of the same `RobotApi`. The robot's internals stay hidden
  behind the interface — students program *to the interface*, not to the concrete robot.
- This is the literal answer to "program the robot to perform a set of actions": in
  `startProgram`, subscribe to the sensors you need and, in each observer callback,
  `robot.perform(...)` the actions you chose; in `stopProgram`, unsubscribe.

### 4.4 Registering and running programs

Students **register** their programs with the system through a small provided API. Each
registered program appears by name in a **dropdown**; a **"Run Program"** button launches
the selected one (and a **"Stop"** returns control to manual). This gives the immediate,
motivating loop: *write a program → register it → it appears in the UI → click Run → watch
the robot follow it.*

```kotlin
// api/ProgramRegistry.kt  (PROVIDED) — the "register program" API students call
interface ProgramRegistry {
    fun register(program: RobotProgram)          // shows up in the dropdown by program.name
    fun programs(): List<RobotProgram>           // used by the UI to build the dropdown
}

// api/StudentPrograms.kt  (STUDENT — the one place to register your programs)
object StudentPrograms {
    fun registerAll(registry: ProgramRegistry) {
        // TODO(student): register each program you write, e.g.
        // registry.register(LineFollowerProgram())
        // registry.register(BallFinderProgram())
    }
}
```

- The app calls `StudentPrograms.registerAll(registry)` once at startup, then populates the
  dropdown from `registry.programs()`. Registration and the run/stop wiring are **provided
  plumbing** — the teachable work is *writing* the program (Observer + Command), not wiring
  a button.
- **Runtime:** the app holds an optional *active program*. "Run Program" calls
  `startProgram(...)` (where the program subscribes to sensors and starts driving); **Stop**
  (or switching environment) calls `stopProgram(...)` (where it unsubscribes and halts). The
  program then runs entirely from its sensor subscriptions — there is no per-tick program
  call. Manual controls and a running program both act through the same `RobotApi`.
- An empty registry is fine — the dropdown simply shows "(no programs registered)" and the
  app still runs, so students see the hook before they've written anything.

---

## 5. Domain model — the skid-steer robot

A skid-steer (differential-drive) robot is actuated by two independent tracks. This is
the real, teachable model and it maps cleanly onto commands.

**State**
- `pose: Pose` — `x`, `y`, `headingRadians`
- `leftTrackVelocity: Double`, `rightTrackVelocity: Double` (units/sec)
- `radius: Double` (robot modeled as a disc for collision), `trackWidth: Double`
- `sensors: List<Sensor<*>>` mounted at offsets

**Kinematics** (`step(dt)`), provided:
```
v  = (vL + vR) / 2                     // linear speed
ω  = (vR - vL) / trackWidth            // angular speed
heading' = heading + ω · dt
x' = x + v · cos(heading) · dt
y' = y + v · sin(heading) · dt
```
Command → velocity mapping (defaults; students may refine):
| Command            | left track | right track |
|--------------------|-----------:|------------:|
| Forward            |        +V |         +V |
| Backward           |        −V |         −V |
| Turn Left (spin)   |        −V |         +V |
| Turn Right (spin)  |        +V |         −V |
| Stop               |         0 |          0 |

**Collision** (provided): attempt the translation; if the robot disc would intersect any
obstacle rectangle or leave the world bounds, the translation is rejected (rotation still
allowed). Sensors then read the blocked pose.

**Sensor mounting:** each sensor has a mount offset `(dx, dy)` relative to the robot
center and an orientation, so students can, e.g., place three `LineSensor`s across the
front (left/center/right) for line-following, or a forward-facing `SonarSensor`.

---

## 6. Environments (student-selectable at runtime)

An `Environment` exposes what the sensors query. A dropdown in the UI switches between
three concrete worlds. Each defines obstacles/bounds plus the features its "signature"
sensor reads, and an optional objective the simulation reports.

`Environment` interface (provided):
```kotlin
interface Environment {
    val bounds: Rectangle
    val obstacles: List<Obstacle>
    fun temperatureAt(p: Vector2): Double     // for TemperatureSensor / heat map
    fun colorAt(p: Vector2): Color            // for VisionSensor (e.g., red ball)
    fun isOnLineAt(p: Vector2): Boolean       // for LineSensor
    fun objectiveStatus(robot: Robot): String // "Ball reached!", progress, etc.
    fun startPose(): Pose
}
```

1. **`ObstacleCourseEnvironment` — "find and touch the red ball."**
   Rectangular obstacles scattered across the field; a **red ball** (rendered circle) at
   a target location. `SonarSensor` gives distance to obstacles (avoid); `VisionSensor`
   reports red when the ball is ahead. Objective satisfied when the robot disc overlaps
   the ball.

2. **`LineMazeEnvironment` — "follow the line."**
   A path of line segments forming a route/maze drawn on the floor. `LineSensor`(s)
   detect the line (array of left/center/right sensors is the classic setup). Objective:
   travel the path to its end.

3. **`TemperatureGradientEnvironment` — "temperature playground."**
   A scalar heat field (e.g., a hot source with distance falloff), rendered as a heat map
   underlay. `TemperatureSensor` reads local temperature. Objective (soft): reach and
   hold the hottest region — a good target for a gradient-ascent behavior.

> The specific *graded tasks* per environment (e.g., "write an observer-driven controller
> that follows the line") are set in the assignment write-up you author. The starter
> guarantees the sensors + commands make each objective achievable.

---

## 7. Package / file layout

Mirrors your HW2 convention (packages named by concern). **[P] = provided & working,
[S] = student TODO (stubbed, still compiles/runs).**

```
src/main/kotlin/
  Main.kt                         [P] entry point → Application.launch(...)

  app/
    RobotSimulationApp.kt         [P] JavaFX Application; builds Stage, panels, AnimationTimer

  geometry/
    Vector2.kt                    [P] 2D vector + helpers
    Pose.kt                       [P] x, y, headingRadians
    Rectangle.kt                  [P] AABB + intersection (obstacles, bounds)
    Ray.kt                        [P] ray for sonar raycasts

  model/
    Robot.kt                      [P] receiver: pose, setTrackVelocities(l,r), sensors, step(dt), collision
    RobotState.kt                 [P] immutable pose+velocity snapshot

  environment/
    Environment.kt                [P] interface (see §6)
    Obstacle.kt                   [P] rectangle obstacle
    ObstacleCourseEnvironment.kt  [P] obstacles + red ball
    LineMazeEnvironment.kt        [P] line path
    TemperatureGradientEnvironment.kt [P] heat field

  sensor/
    Sensor.kt                     [P] abstract Sensor<T>: computes reading; notifies every tick
    RobotSensors.kt               [P] the suite a program subscribes to (sonar/vision/temp/3×line/collision)
    SonarSensor.kt                [P] distance to nearest obstacle OR outer wall (raycast) → Double
    VisionSensor.kt               [P] forward "camera": color of nearest thing ahead → Color
    TemperatureSensor.kt          [P] temperature at mount → Double
    LineSensor.kt                 [P] over-line detection → Boolean
    CollisionSensor.kt            [P] bump sensor: robot blocked by obstacle/wall → Boolean

  observer/                       ── OBSERVER PATTERN ──
    Observer.kt                   [P] fun interface Observer<T>
    Subject.kt                    [P] interface Subject<T>
    AbstractSubject<T>            [S] subscribe / unsubscribe / notifyObservers
    LabelObserver.kt              [S] example concrete observer (updates a Label)

  command/                        ── COMMAND PATTERN ──
    Command.kt                    [P] interface { execute(); undo() }
    CommandInvoker.kt             [S] history stack + undo/redo
    (student-authored)            [S] students create their own command classes here —
                                      the starter does NOT prescribe a fixed set

  api/                            ── APPLICATION INTERFACE (how students program the robot) ──
    RobotApi.kt                   [P] interface: sensors + actuator + perform(command)/perform(list) + undo/redo
    RobotProgram.kt               [P] interface { name; startProgram(robot); stopProgram(robot) } — students implement
    DefaultRobotApi.kt            [P] facade impl delegating perform(...) to CommandInvoker
    ProgramRegistry.kt            [P] "register program" API + DefaultProgramRegistry backing the dropdown
    StudentPrograms.kt            [S] the one place students call registry.register(...)

  sim/
    Simulation.kt                 [P] holds Environment + Robot; step(dt); objective check
    EnvironmentCatalog.kt         [P] named list of the 3 environments for the dropdown

  ui/
    SimulationCanvas.kt           [P] draws env (obstacles/lines/heat/ball), robot, sensor rays
    ControlPanel.kt               [P] layout of buttons + env dropdown; [S] wire clicks → RobotApi.perform(...)
    ProgramPanel.kt               [P] program dropdown + "Run Program"/"Stop" buttons (registry-driven)
    TelemetryPanel.kt             [P] labels for sensor readings; [S] subscribe observers to sensors
    MainView.kt                   [P] BorderPane composition (canvas center, controls bottom, telemetry right)

src/test/kotlin/
  KinematicsTest.kt               [P] verifies skid-steer math & collision
  GeometryTest.kt                 [P] rectangle/ray intersection
  (students add) ObserverTest, CommandInvokerTest
```

---

## 8. Simulation loop & rendering

- `RobotSimulationApp` starts a JavaFX `AnimationTimer`. Each frame:
  1. compute `dt` (clamped),
  2. `simulation.step(dt)` → robot kinematics + collision → each sensor recomputes its
     reading and calls `notifyObservers(reading)` (every tick). A running program reacts
     *here*, through its own sensor subscriptions — there is no separate program tick,
  3. `simulationCanvas.render()` redraws the world.
- Rendering draws, in order: environment underlay (heat map / lines / obstacles / red
  ball), robot body + heading indicator, sensor rays and hit markers, and an objective/
  status readout.

---

## 9. UI layout (JavaFX)

```
┌───────────────────────────────────────────────┬───────────────┐
│                                                │  Telemetry     │
│                                                │  ───────────   │
│              SimulationCanvas                  │  Sonar:   ---  │
│         (top-down 2D world view)               │  Temp:    ---  │
│                                                │  Line:    ---  │
│                                                │  Vision:  ---  │
│                                                │  Objective:... │
├───────────────────────────────────────────────┴───────────────┤
│  Environment: [ Obstacle Course ▼ ]                            │
│  Program:     [ Line Follower ▼ ]  [ ▶ Run Program ] [ ■ Stop ] │
│  [◄ Left] [▲ Fwd] [▼ Back] [► Right] [■ Stop]   [Undo] [Redo]  │
└───────────────────────────────────────────────────────────────┘
```

- Buttons and keyboard bindings (arrow keys, space=stop) both flow through the
  **`RobotApi`** → **`CommandInvoker`** — reinforcing that neither the API nor the invoker
  cares who called it (manual click or autonomous program).
- The **Program** dropdown lists everything registered via the `ProgramRegistry`;
  **Run Program** launches the selected one (stepping it each tick), **Stop** halts it and
  returns to manual control.
- The environment dropdown reloads the `Simulation` with the chosen world.

---

## 10. Student deliverables — TODO checklist

**Observer pattern**
- [ ] Implement `AbstractSubject<T>`: `subscribe`, `unsubscribe`, `notifyObservers`.
- [ ] Implement `LabelObserver` (and any other concrete observers).
- [ ] In `TelemetryPanel`, subscribe observers to each sensor so the labels update live.

**Command pattern**
- [ ] **Design your own set of `Command` classes** — you decide which actions the robot
      supports and how they map to the robot's actuators. `execute()` performs the action;
      `undo()` reverses it. (The starter deliberately does not give you a command list.)
- [ ] Implement `CommandInvoker.run/undo/redo` with the history stacks.
- [ ] In `ControlPanel`, wire each button (and key binding) to build one of *your* commands
      and submit it via `RobotApi.perform(...)`; wire Undo/Redo to the API.

**Programming the robot via the application interface**
- [ ] Implement a `RobotProgram`: in `startProgram(robot)` **subscribe to the sensors you
      need** (`robot.sensors.…`) and, in each observer callback, `robot.perform(...)` your
      commands; in `stopProgram(robot)` unsubscribe and stop the robot. This is where
      Observer (your sensor subscriptions) and Command (your actions) come together —
      pursue an objective such as following the line, ascending the temperature gradient,
      or finding & touching the red ball.
- [ ] **Register your program** in `StudentPrograms.registerAll(...)` via
      `registry.register(...)` so it appears in the "Program" dropdown and can be launched
      with **Run Program**.

**Definition of done:** telemetry updates live, all controls + undo/redo work through the
`RobotApi`, and a `RobotProgram` you registered can be selected in the dropdown and, on
**Run Program**, drives the robot to meet at least one environment's objective.

---

## 11. Suggested extensions (optional / bonus)

- **Positional undo:** record pose snapshots so undo rewinds movement, not just velocity.
- **Macro command:** a `CompositeCommand` that batches several commands as one undoable unit.
- **Sensor fusion:** multiple line sensors (L/C/R) for smoother line-following.
- **Redo-on-replay:** replay the whole command history as a "recording."
- **Observer detach:** unsubscribe a panel and watch it freeze — demonstrates the contract.

---

## 12. Build & run

```bash
./gradlew run      # launches the JavaFX simulation
./gradlew test     # runs the provided kinematics/geometry tests
```

---

## 13. Open items to confirm during implementation

1. **JavaFX / plugin versions** vs. the JDK 25 toolchain (fallback to JavaFX 23/21 LTS if
   25 binaries lag). Verify `./gradlew run` launches cleanly on macOS (dev) and Windows
   (students commonly run there).
2. **Stub style:** stubs are no-op / return-default with `// TODO(student)` comments so the
   app always compiles and runs — *not* `TODO()` throwers. Confirm this is the desired
   student experience (app runs from day one, comes alive as they implement).
3. **Ship an example program?** With an empty registry the dropdown shows
   "(no programs registered)" and the app still runs. Decide whether the student hand-out
   ships a tiny *example* `RobotProgram` (e.g. "Drive Square") already registered as a
   template to copy, or leaves `StudentPrograms.registerAll` empty with only a TODO.
4. **Package root:** bare packages (`model`, `sensor`, …) as above, matching HW2, vs. an
   `org.example.*` root.

---

## 14. Implementation order (for building the starter)

1. `build.gradle.kts` (JavaFX + application) → confirm empty JavaFX window runs.
2. `geometry/` + tests.
3. `model/Robot` kinematics + collision + tests.
4. `environment/` (all three) + rendering underlays.
5. `sensor/` reading computations (Subject hooks present, notify stubbed).
6. `sim/Simulation` + `AnimationTimer` loop + `SimulationCanvas` (world animates).
7. `api/` application interface: `RobotApi`, `RobotInput`, `RobotProgram`, `DefaultRobotApi`
   (facade delegating to the invoker), plus `ProgramRegistry`/`DefaultProgramRegistry` and
   the `StudentPrograms` registration hook.
8. UI shell: `MainView`, `ControlPanel` (buttons laid out, unwired), `ProgramPanel`
   (registry-driven dropdown + Run/Stop, fully wired), `TelemetryPanel`.
9. Insert pattern **stubs**: `observer/*`, `command/CommandInvoker` (+ student command area)
   with `// TODO(student)` bodies.
10. Author a reference solution (kept out of the student hand-out) to validate the design —
    including a sample `RobotProgram` and command set that meets at least one objective.
11. Write `README`/assignment notes; verify the app runs end-to-end with stubs in place.
```
