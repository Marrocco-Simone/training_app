package dev.simone.training.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val FORMAT_VERSION = 1

private val OFFSET_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)
private fun JSONObject.num(key: String): Double? = (opt(key) as? Number)?.toDouble()
private fun JSONObject.int(key: String): Int? = (opt(key) as? Number)?.toInt()

private fun JSONObject.objects(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun JSONObject.putOrNull(key: String, value: String?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun JSONObject.putOrNull(key: String, value: Number?): JSONObject = put(key, value ?: JSONObject.NULL)

private fun <T> List<T>.toJson(write: (T) -> JSONObject): JSONArray = JSONArray().also { array ->
    forEach { array.put(write(it)) }
}

private fun parseStart(text: String): OffsetDateTime =
    runCatching { OffsetDateTime.parse(text) }.getOrNull()
        ?: LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime()

private fun readLevel(o: JSONObject?): Level? = when (o?.str("kind")) {
    "ss" -> SsLift.entries.byKey(o.str("lift"))?.let { Level.Ss(it) }
    "og" -> o.num("value")?.let { Level.Og(it) }
    else -> null
}

private fun readExercise(o: JSONObject): Exercise {
    val id = o.str("id") ?: throw IllegalArgumentException("Exercise without id")
    return Exercise(
        id = id,
        name = o.str("name") ?: id,
        category = Category.entries.byKey(o.str("category")) ?: Category.OTHER,
        role = Role.entries.byKey(o.str("role")) ?: Role.MAIN,
        level = readLevel(o.optJSONObject("level")),
        topReps = o.int("topReps"),
        notes = o.str("notes"),
    )
}

private fun readItem(o: JSONObject): Item = Item(
    exerciseId = o.str("exerciseId") ?: throw IllegalArgumentException("Item without exerciseId"),
    sets = o.int("sets"),
    reps = o.int("reps"),
    seconds = o.num("seconds"),
    loadKg = o.num("loadKg"),
    lastReps = o.int("lastReps"),
    lastSeconds = o.num("lastSeconds"),
    dropReps = o.int("dropReps"),
    extra = o.objects("extra").map { SetValues(it.int("reps"), it.num("seconds"), it.num("loadKg")) },
    perSide = o.optBoolean("perSide", false),
    setup = o.str("setup"),
    toFailure = o.optBoolean("toFailure", false),
    note = o.str("note"),
    raw = o.str("raw"),
    parsed = o.optBoolean("parsed", true),
)

private fun readBlock(o: JSONObject) = Block(o.objects("items").map(::readItem), o.int("restSec"))

private fun readSession(o: JSONObject): Session {
    val id = o.str("id") ?: throw IllegalArgumentException("Session without id")
    val date = o.str("date") ?: throw IllegalArgumentException("Session $id without date")
    return Session(
        id = id,
        date = LocalDate.parse(date),
        source = o.str("source") ?: "app",
        notes = o.str("notes"),
        blocks = o.objects("blocks").map(::readBlock),
        warmup = o.str("warmup"),
    )
}

private fun readWarmups(settings: JSONObject?): List<Warmup> {
    settings ?: return emptyList()
    if (settings.has("warmups")) {
        return settings.objects("warmups").map { o ->
            val id = o.str("id") ?: throw IllegalArgumentException("Warmup without id")
            Warmup(id, o.str("name") ?: id, o.objects("blocks").map(::readBlock))
        }
    }
    val legacy = settings.objects("warmup").map(::readBlock)
    return if (legacy.isEmpty()) emptyList() else listOf(Warmup("circuit_1", "Circuit 1", legacy))
}

private fun readRun(o: JSONObject): Run {
    val id = o.str("id") ?: throw IllegalArgumentException("Run without id")
    val start = o.str("startedAt") ?: throw IllegalArgumentException("Run $id without startedAt")
    return Run(
        id = id,
        startedAt = parseStart(start),
        durationSec = o.int("durationSec"),
        distanceM = o.num("distanceM"),
        avgHr = o.int("avgHr"),
        maxHr = o.int("maxHr"),
        type = RunType.entries.byKey(o.str("type")) ?: RunType.OTHER,
        title = o.str("title"),
        kms = o.objects("kms").map { Km(KmKind.entries.byKey(it.str("kind")) ?: KmKind.NORMAL, it.int("sec")) },
        elevationGainM = o.num("elevationGainM"),
        notes = o.str("notes"),
        source = o.str("source") ?: "app",
        track = o.str("track"),
    )
}

fun parseData(text: String): TrainingData {
    val root = JSONObject(text)
    val version = root.int("version")
    require(version == FORMAT_VERSION) { "Unsupported version: ${version ?: "missing"}" }
    val settings = root.optJSONObject("settings")
    return TrainingData(
        settings = Settings(
            bodyweightKg = settings?.num("bodyweightKg"),
            sex = Sex.entries.byKey(settings?.str("sex")),
            birthYear = settings?.int("birthYear"),
            warmups = readWarmups(settings),
        ),
        exercises = root.objects("exercises").map(::readExercise),
        sessions = root.objects("sessions").map(::readSession),
        runs = root.objects("runs").map(::readRun),
    )
}

private fun JSONArray.doubles(): List<Double> = (0 until length()).map { getDouble(it) }

fun parseStandards(text: String): Standards {
    val root = JSONObject(text)
    val lifts = root.getJSONObject("lifts")
    return Standards(
        bodyweightsKg = root.getJSONArray("bodyweightsKg").doubles(),
        ages = root.getJSONArray("ages").doubles().map { it.toInt() },
        lifts = SsLift.entries.filter { lifts.has(it.key) }.associateWith { lift ->
            val bySex = lifts.getJSONObject(lift.key)
            Sex.entries.filter { bySex.has(it.key) }.associateWith { sex ->
                val rows = bySex.getJSONArray(sex.key)
                (0 until rows.length()).map { b ->
                    val cells = rows.getJSONArray(b)
                    (0 until cells.length()).map { a -> cells.optJSONArray(a)?.doubles() }
                }
            }
        },
    )
}

private fun writeLevel(level: Level?): JSONObject? = when (level) {
    null -> null
    is Level.Ss -> JSONObject().put("kind", "ss").put("lift", level.lift.key)
    is Level.Og -> JSONObject().put("kind", "og").put("value", level.value)
}

private fun writeExercise(e: Exercise): JSONObject = JSONObject()
    .put("id", e.id)
    .put("name", e.name)
    .put("category", e.category.key)
    .put("role", e.role.key)
    .put("level", writeLevel(e.level) ?: JSONObject.NULL)
    .putOrNull("topReps", e.topReps)
    .putOrNull("notes", e.notes)

private fun writeItem(i: Item): JSONObject = JSONObject()
    .put("exerciseId", i.exerciseId)
    .putOrNull("sets", i.sets)
    .putOrNull("reps", i.reps)
    .putOrNull("seconds", i.seconds)
    .putOrNull("loadKg", i.loadKg)
    .putOrNull("lastReps", i.lastReps)
    .putOrNull("lastSeconds", i.lastSeconds)
    .putOrNull("dropReps", i.dropReps)
    .put("extra", i.extra.toJson {
        JSONObject().putOrNull("reps", it.reps).putOrNull("seconds", it.seconds).putOrNull("loadKg", it.loadKg)
    })
    .put("perSide", i.perSide)
    .putOrNull("setup", i.setup)
    .put("toFailure", i.toFailure)
    .putOrNull("note", i.note)
    .putOrNull("raw", i.raw)
    .put("parsed", i.parsed)

private fun writeBlock(b: Block): JSONObject = JSONObject()
    .put("items", b.items.toJson(::writeItem))
    .putOrNull("restSec", b.restSec)

private fun writeSession(s: Session): JSONObject = JSONObject()
    .put("id", s.id)
    .put("date", s.date.toString())
    .put("source", s.source)
    .putOrNull("notes", s.notes)
    .put("blocks", s.blocks.toJson(::writeBlock))
    .putOrNull("warmup", s.warmup)

private fun writeRun(r: Run): JSONObject = JSONObject()
    .put("id", r.id)
    .put("startedAt", r.startedAt.format(OFFSET_FORMAT))
    .putOrNull("durationSec", r.durationSec)
    .putOrNull("distanceM", r.distanceM)
    .putOrNull("avgHr", r.avgHr)
    .putOrNull("maxHr", r.maxHr)
    .put("type", r.type.key)
    .putOrNull("title", r.title)
    .put("kms", r.kms.toJson { JSONObject().put("kind", it.kind.key).putOrNull("sec", it.sec) })
    .putOrNull("elevationGainM", r.elevationGainM)
    .putOrNull("notes", r.notes)
    .put("source", r.source)
    .putOrNull("track", r.track)

fun serializeData(data: TrainingData, exportedAt: Instant?): String {
    val root = JSONObject().put("version", FORMAT_VERSION)
    if (exportedAt != null) root.put("exportedAt", exportedAt.truncatedTo(ChronoUnit.SECONDS).toString())
    root.put(
        "settings",
        JSONObject()
            .putOrNull("bodyweightKg", data.settings.bodyweightKg)
            .putOrNull("sex", data.settings.sex?.key)
            .putOrNull("birthYear", data.settings.birthYear)
            .put("warmups", data.settings.warmups.toJson { w ->
                JSONObject().put("id", w.id).put("name", w.name).put("blocks", w.blocks.toJson(::writeBlock))
            }),
    )
    root.put("exercises", data.exercises.toJson(::writeExercise))
    root.put("sessions", data.sessions.toJson(::writeSession))
    root.put("runs", data.runs.toJson(::writeRun))
    return root.toString(1)
}

fun readDataFile(file: File): TrainingData =
    if (file.exists()) parseData(file.readText()) else TrainingData.EMPTY

fun writeDataFile(file: File, data: TrainingData) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(serializeData(data, null))
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
