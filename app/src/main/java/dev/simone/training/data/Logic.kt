package dev.simone.training.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

const val LEVEL_WINDOW_DAYS = 28
const val DEFAULT_TOP_REPS = 7
const val DEFAULT_SETS = 3
const val STALE_DAYS = 7
const val HEAT_WEEKS = 16
private const val LEVEL_SCALE = 12.0

val GROUPS: List<Category> = Category.entries.filter { it.isGroup }

fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

private fun epley(weight: Double, reps: Int): Double = weight * (1 + reps / 30.0)

private fun Item.loadedSets(): List<Pair<Int?, Double?>> =
    listOf(reps to loadKg, lastReps to loadKg) + extra.map { it.reps to it.loadKg }

fun oneRepMax(item: Item, lift: SsLift, bodyweightKg: Double?): Double? =
    item.loadedSets().mapNotNull { (reps, load) ->
        when {
            reps == null || reps !in 1..12 -> null
            lift.addedLoad -> bodyweightKg?.let { epley(it + (load ?: 0.0), reps) - it }
            else -> load?.let { epley(it, reps) }
        }
    }.maxOrNull()

/** Grid indices around [x] with their linear weights; [x] is clamped to the range of [axis]. */
private fun bracket(axis: List<Double>, x: Double): List<Pair<Int, Double>> {
    val v = x.coerceIn(axis.first(), axis.last())
    val i = axis.indexOfLast { it <= v }
    if (i == axis.lastIndex || axis[i] == v) return listOf(i to 1.0)
    val t = (v - axis[i]) / (axis[i + 1] - axis[i])
    return listOf(i to 1 - t, i + 1 to t)
}

fun thresholds(standards: Standards, lift: SsLift, sex: Sex, bodyweightKg: Double, age: Int): List<Double>? {
    val grid = standards.lifts[lift]?.get(sex) ?: return null
    val ages = bracket(standards.ages.map { it.toDouble() }, age.toDouble())
    val cells = bracket(standards.bodyweightsKg, bodyweightKg).flatMap { (b, wb) ->
        ages.map { (a, wa) -> (grid[b][a] ?: return null) to wb * wa }
    }
    return List(cells.first().first.size) { k -> cells.sumOf { (row, w) -> row[k] * w } }
}

private fun ssThresholds(standards: Standards?, lift: SsLift, settings: Settings, date: LocalDate): List<Double>? {
    val sex = settings.sex ?: return null
    val bodyweight = settings.bodyweightKg ?: return null
    val birthYear = settings.birthYear ?: return null
    return thresholds(standards ?: return null, lift, sex, bodyweight, date.year - birthYear)
}

fun ssIndex(oneRm: Double, thresholds: List<Double>): Double {
    val first = thresholds.first()
    if (oneRm < first) return if (first <= 0.0) 1.0 else oneRm / first
    for (i in 0 until thresholds.size - 1) {
        if (oneRm <= thresholds[i + 1]) return i + 1 + (oneRm - thresholds[i]) / (thresholds[i + 1] - thresholds[i])
    }
    val n = thresholds.size
    return n + (oneRm - thresholds[n - 1]) / (thresholds[n - 1] - thresholds[n - 2])
}

private fun Item.hasCompleteSet(): Boolean =
    sets != 0 && ((reps ?: 0) > 0 || (seconds ?: 0.0) > 0 || toFailure || extra.any { (it.reps ?: 0) > 0 || (it.seconds ?: 0.0) > 0 })

fun itemLevel(item: Item, exercise: Exercise, date: LocalDate, standards: Standards?, settings: Settings): Double? {
    if (!item.parsed || exercise.role == Role.ACCESSORY || exercise.role == Role.WARMUP) return null
    return when (val level = exercise.level) {
        null -> null
        is Level.Ss -> ssThresholds(standards, level.lift, settings, date)?.let { t ->
            oneRepMax(item, level.lift, settings.bodyweightKg)?.let { 1.5 * ssIndex(it, t) + 1.5 }
        }
        is Level.Og -> if (item.hasCompleteSet()) level.value else null
    }
}

/** One value per day from [from] to [to] inclusive; null where the group has no item in the window. */
fun groupLevels(data: TrainingData, standards: Standards?, from: LocalDate, to: LocalDate): Map<Category, List<Double?>> {
    val windowStart = from.minusDays(LEVEL_WINDOW_DAYS - 1L)
    val span = daysBetween(windowStart, to).toInt() + 1
    val best = GROUPS.associateWith { DoubleArray(span) { Double.NaN } }
    for (session in data.sessions) {
        if (session.date < windowStart || session.date > to) continue
        val day = daysBetween(windowStart, session.date).toInt()
        for (item in session.blocks.flatMap { it.items }) {
            val exercise = data.exerciseById[item.exerciseId] ?: continue
            val days = best[exercise.category] ?: continue
            val level = itemLevel(item, exercise, session.date, standards, data.settings) ?: continue
            if (days[day].isNaN() || level > days[day]) days[day] = level
        }
    }
    val count = daysBetween(from, to).toInt() + 1
    return best.mapValues { (_, days) ->
        List(count) { d ->
            (d until d + LEVEL_WINDOW_DAYS).map { days[it] }.filterNot { it.isNaN() }.maxOrNull()
        }
    }
}

/** [known] is the newest level of the last year; [current] is null when the group has no item in the last window. */
data class GroupLevel(val current: Double?, val known: Double?)

fun currentLevels(data: TrainingData, standards: Standards?, today: LocalDate): Map<Category, GroupLevel> =
    groupLevels(data, standards, today.minusDays(364), today).mapValues { (_, series) ->
        val current = series.last()
        GroupLevel(current, current ?: series.lastOrNull { it != null })
    }

fun levelFraction(level: Double): Float = (level / LEVEL_SCALE).toFloat().coerceIn(0.02f, 1f)

enum class Measure(val unit: String) { ONE_RM("kg e1RM"), REPS("reps"), SECONDS("s") }

fun measureFor(exercise: Exercise?, items: List<Item>): Measure = when {
    exercise?.level is Level.Ss || items.any { (it.loadKg ?: 0.0) > 0 || it.extra.any { e -> (e.loadKg ?: 0.0) > 0 } } -> Measure.ONE_RM
    items.any { it.reps != null } -> Measure.REPS
    else -> Measure.SECONDS
}

fun itemValue(item: Item, exercise: Exercise?, measure: Measure, bodyweightKg: Double?): Double? {
    if (!item.parsed) return null
    return when (measure) {
        Measure.ONE_RM -> when (val level = exercise?.level) {
            is Level.Ss -> oneRepMax(item, level.lift, bodyweightKg)
            else -> item.loadedSets().mapNotNull { (reps, load) ->
                if (reps == null || reps !in 1..12 || load == null || load <= 0) null else epley(load, reps)
            }.maxOrNull()
        }
        Measure.REPS -> (listOf(item.reps, item.lastReps) + item.extra.map { it.reps }).filterNotNull().maxOrNull()?.toDouble()
        Measure.SECONDS -> (listOf(item.seconds, item.lastSeconds) + item.extra.map { it.seconds }).filterNotNull().maxOrNull()
    }
}

data class Occurrence(val date: LocalDate, val sessionId: String, val item: Item)

/** Newest first, per exercise id. */
fun occurrences(sessions: List<Session>): Map<String, List<Occurrence>> =
    sessions.sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.id })
        .flatMap { s -> s.blocks.flatMap { b -> b.items.filter { it.parsed }.map { Occurrence(s.date, s.id, it) } } }
        .groupBy { it.item.exerciseId }

fun previousOccurrence(index: Map<String, List<Occurrence>>, exerciseId: String, date: LocalDate, sessionId: String): Occurrence? =
    index[exerciseId]?.firstOrNull {
        it.sessionId != sessionId && (it.date < date || (it.date == date && (sessionId.isEmpty() || it.sessionId < sessionId)))
    }

data class Suggestion(val text: String, val values: Item?)

fun Item.withValuesOf(other: Item): Item = copy(
    sets = other.sets ?: sets,
    reps = other.reps,
    seconds = other.seconds,
    loadKg = other.loadKg,
    lastReps = other.lastReps,
    lastSeconds = other.lastSeconds,
    dropReps = other.dropReps,
    extra = other.extra,
    perSide = other.perSide,
    setup = other.setup,
    toFailure = other.toFailure,
    raw = null,
    parsed = true,
)

fun suggestion(previous: Occurrence?, exercise: Exercise?, date: LocalDate): Suggestion? {
    previous ?: return null
    val last = previous.item
    val text = itemText(last, exercise, withSets = true)
    val days = daysBetween(previous.date, date)
    val lastReps = last.lastReps
    val reps = last.reps
    return when {
        days >= STALE_DAYS -> Suggestion("$days days since last time · repeat $text", last)
        lastReps == null -> Suggestion("Last: $text", null)
        reps != null && lastReps <= reps -> Suggestion("Repeat $text", last)
        lastReps - 1 > (exercise?.topReps ?: DEFAULT_TOP_REPS) -> Suggestion("Last: $text · add load or move to the next progression", null)
        else -> {
            val next = last.copy(reps = lastReps - 1, lastReps = null)
            Suggestion("Next: ${itemText(next, exercise, withSets = true)}", next)
        }
    }
}

data class Period(val start: LocalDate, val end: LocalDate, val sessions: Int, val exerciseIds: List<String>)

private fun Session.mainExerciseIds(exercises: Map<String, Exercise>): Set<String> =
    blocks.flatMap { it.items }.map { it.exerciseId }.filter { exercises[it]?.role != Role.WARMUP }.toSet()

/** Newest first. */
fun planPeriods(data: TrainingData): List<Period> {
    val periods = mutableListOf<MutableList<Session>>()
    var previous: Set<String>? = null
    for (session in data.sessions.sortedWith(compareBy<Session> { it.date }.thenBy { it.id })) {
        val ids = session.mainExerciseIds(data.exerciseById)
        // limit: a period starts when a session shares less than half of its exercise ids with the previous one; a deload or a one-off session splits a plan. A stored plan id would make this exact.
        val prev = previous
        if (prev == null || ids.count { it in prev } * 2 < ids.size) periods.add(mutableListOf())
        periods.last().add(session)
        previous = ids
    }
    return periods.map { list ->
        Period(
            start = list.first().date,
            end = list.last().date,
            sessions = list.size,
            exerciseIds = list.flatMap { it.mainExerciseIds(data.exerciseById) }.distinct(),
        )
    }.reversed()
}

fun fmtNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

private fun signedLoad(exercise: Exercise?): Boolean = when (val level = exercise?.level) {
    is Level.Og -> true
    is Level.Ss -> level.lift.addedLoad
    null -> false
}

private fun loadText(load: Double?, exercise: Exercise?): String? = load?.let {
    if (signedLoad(exercise) && it > 0) "+${fmtNum(it)}" else fmtNum(it)
}

private fun workText(reps: Int?, seconds: Double?, perSide: Boolean): String =
    listOfNotNull(reps?.let { if (perSide) "$it+$it" else "$it" }, seconds?.let { "${fmtNum(it)}s" }).joinToString("×")

fun itemText(item: Item, exercise: Exercise?, withSets: Boolean): String {
    if (!item.parsed) return item.raw ?: "?"
    val work = workText(item.reps, item.seconds, item.perSide).ifEmpty { if (item.toFailure) "max" else "" }
    return buildString {
        append(listOfNotNull(loadText(item.loadKg, exercise), work.ifEmpty { null }).joinToString("×"))
        item.lastReps?.let { append(" ($it)") }
        item.lastSeconds?.let { append(" (${fmtNum(it)}s)") }
        item.dropReps?.let { append("+$it") }
        item.extra.forEach { e ->
            append(", ")
            append(listOfNotNull(loadText(e.loadKg, exercise), workText(e.reps, e.seconds, item.perSide).ifEmpty { null }).joinToString("×"))
        }
        item.setup?.let { append(" [$it]") }
        if (withSets && item.sets != null) append(" · ${item.sets} sets")
    }
}

/** Adds the exercises of [catalog] whose id is not in the data yet. */
fun TrainingData.withCatalog(catalog: List<Exercise>): TrainingData {
    val known = exercises.map { it.id }.toSet()
    return copy(exercises = exercises + catalog.filter { it.id !in known })
}

fun exerciseName(data: TrainingData, id: String): String = data.exerciseById[id]?.name ?: id

fun blockText(data: TrainingData, block: Block, withSets: Boolean = false): String =
    block.items.joinToString(" / ") { "${exerciseName(data, it.exerciseId)} ${itemText(it, data.exerciseById[it.exerciseId], withSets)}".trim() }

fun latestSession(data: TrainingData): Session? =
    data.sessions.maxWithOrNull(compareBy<Session> { it.date }.thenBy { it.id })

fun latestRun(data: TrainingData): Run? = data.runs.maxByOrNull { it.startedAt }

fun nextWarmup(data: TrainingData, last: Session): Warmup? =
    data.settings.warmups.firstOrNull { it.id == last.warmup } ?: data.settings.warmups.firstOrNull()

/** The non-warmup exercises of [last], each with the suggestion text for [today]. */
fun nextSessionRows(data: TrainingData, last: Session, today: LocalDate): List<Pair<String, String?>> {
    val index = occurrences(data.sessions)
    return last.blocks.flatMap { it.items }
        .filter { data.exerciseById[it.exerciseId]?.role != Role.WARMUP }
        .map { it.exerciseId }.distinct()
        .map { id -> id to suggestion(previousOccurrence(index, id, today, ""), data.exerciseById[id], today)?.text?.takeUnless { it.endsWith(": ") } }
}

/** Monday of the first of the [HEAT_WEEKS] weeks that end with the week of [today]. */
fun heatmapStart(today: LocalDate): LocalDate = today.with(DayOfWeek.MONDAY).minusWeeks(HEAT_WEEKS - 1L)

fun strengthDays(sessions: List<Session>, from: LocalDate): Set<LocalDate> = sessions.map { it.date }.filter { it >= from }.toSet()

fun runDays(runs: List<Run>, from: LocalDate): Set<LocalDate> = runs.map { it.startedAt.toLocalDate() }.filter { it >= from }.toSet()

fun newSessionId(data: TrainingData, date: LocalDate): String {
    val taken = data.sessions.map { it.id }.toSet()
    return ('a'..'z').map { "$date-$it" }.firstOrNull { it !in taken } ?: "$date-${System.currentTimeMillis()}"
}

fun newSession(data: TrainingData, today: LocalDate): Session {
    val latest = latestSession(data)
    val copied = latest?.blocks.orEmpty()
        .map { block ->
            block.copy(items = block.items
                .filter { data.exerciseById[it.exerciseId]?.role != Role.WARMUP }
                .map { if (it.parsed) it.copy(sets = it.sets ?: DEFAULT_SETS, raw = null) else it })
        }
        .filter { it.items.isNotEmpty() }
    val warmup = latest?.warmup?.takeIf { id -> data.settings.warmups.any { it.id == id } } ?: data.settings.warmups.firstOrNull()?.id
    return Session(id = "", date = today, source = "app", notes = null, blocks = copied, warmup = warmup)
}

fun snakeId(name: String, taken: Set<String>): String {
    val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "exercise" }
    return generateSequence(1) { it + 1 }.map { if (it == 1) base else "${base}_$it" }.first { it !in taken }
}

data class Delta(val unit: String, val diff: Double)

fun itemDeltas(item: Item, previous: Item): List<Delta> {
    if (!item.parsed || !previous.parsed) return emptyList()
    fun d(unit: String, a: Number?, b: Number?): Delta? =
        if (a == null || b == null) null else Delta(unit, a.toDouble() - b.toDouble()).takeIf { it.diff != 0.0 }
    val kg = d("kg", item.loadKg ?: 0.0, previous.loadKg ?: 0.0).takeIf { item.loadKg != null || previous.loadKg != null }
    fun work(delta: Delta?): Delta? = delta?.takeUnless { kg != null && it.diff * kg.diff < 0 }
    return listOfNotNull(
        kg,
        d("sets", item.sets, previous.sets),
        work(d("reps", item.reps, previous.reps)),
        work(d("last", item.lastReps ?: item.reps, previous.lastReps ?: previous.reps).takeIf { item.lastReps != null || previous.lastReps != null }),
        work(d("s", item.seconds, previous.seconds)),
    )
}
