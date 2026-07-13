package observer

/**
 * Reusable base implementation of [Subject] — this is the heart of the Observer pattern, and it is
 * yours to implement. Every sensor extends this class, so once it works, every sensor can be
 * subscribed to.
 *
 * TODO(student): give this class a collection of observers and implement the three methods:
 *   - subscribe:       remember [observer] (avoid adding the same one twice)
 *   - unsubscribe:     forget [observer]
 *   - notifyObservers: call onUpdate(value) on every currently-subscribed observer
 *
 * Until you implement these, sensors still compute their readings but nothing is ever delivered —
 * the telemetry panel stays blank and programs receive no callbacks.
 */
abstract class AbstractSubject<T> : Subject<T> {

    override fun subscribe(observer: Observer<T>) {
        // TODO(student): remember this observer
    }

    override fun unsubscribe(observer: Observer<T>) {
        // TODO(student): forget this observer
    }

    override fun notifyObservers(value: T) {
        // TODO(student): deliver `value` to every subscribed observer via onUpdate(value)
    }
}
