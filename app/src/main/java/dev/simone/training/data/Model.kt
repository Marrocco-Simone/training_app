package dev.simone.training.data

import java.time.LocalDate
import java.time.OffsetDateTime

interface Keyed {
    val key: String
}

fun <E : Keyed> List<E>.byKey(key: String?): E? = firstOrNull { it.key == key }

enum class Category(override val key: String, val label: String) : Keyed {
    SQUAT("squat", "Squat"),
    HINGE("hinge", "Hinge"),
    HORIZONTAL_PUSH("horizontal_push", "Horizontal push"),
    HORIZONTAL_PULL("horizontal_pull", "Horizontal pull"),
    VERTICAL_PUSH("vertical_push", "Vertical push"),
    VERTICAL_PULL("vertical_pull", "Vertical pull"),
    CORE("core", "Core"),
    OTHER("other", "Other");

    val isGroup: Boolean get() = this != CORE && this != OTHER
}

enum class Role(override val key: String) : Keyed {
    MAIN("main"), SKILL("skill"), ACCESSORY("accessory"), WARMUP("warmup")
}

enum class SsLift(override val key: String, val label: String, val addedLoad: Boolean) : Keyed {
    BACK_SQUAT("back_squat", "Back squat", false),
    FRONT_SQUAT("front_squat", "Front squat", false),
    DEADLIFT("deadlift", "Deadlift", false),
    BENCH_PRESS("bench_press", "Bench press", false),
    INCLINE_BENCH_PRESS("incline_bench_press", "Incline bench press", false),
    OVERHEAD_PRESS("overhead_press", "Overhead press", false),
    PUSH_PRESS("push_press", "Push press", false),
    WEIGHTED_DIP("weighted_dip", "Weighted dip", true),
    WEIGHTED_CHINUP("weighted_chinup", "Weighted chin-up", true),
    WEIGHTED_PULLUP("weighted_pullup", "Weighted pull-up", true),
    PENDLAY_ROW("pendlay_row", "Pendlay row", false),
}

enum class Sex(override val key: String) : Keyed {
    MALE("male"), FEMALE("female")
}

/** [lifts] is indexed by lift, sex, [bodyweightsKg] index and [ages] index. A cell holds 8 thresholds in kg, or null where the source has no values. */
data class Standards(
    val bodyweightsKg: List<Double>,
    val ages: List<Int>,
    val lifts: Map<SsLift, Map<Sex, List<List<List<Double>?>>>>,
)

sealed interface Level {
    data class Ss(val lift: SsLift) : Level
    data class Og(val value: Double) : Level
}

data class Exercise(
    val id: String,
    val name: String,
    val category: Category,
    val role: Role,
    val level: Level?,
    val topReps: Int?,
    val notes: String?,
)

data class SetValues(val reps: Int?, val seconds: Double?, val loadKg: Double?)

data class Item(
    val exerciseId: String,
    val sets: Int? = DEFAULT_SETS,
    val reps: Int? = null,
    val seconds: Double? = null,
    val loadKg: Double? = null,
    val lastReps: Int? = null,
    val lastSeconds: Double? = null,
    val dropReps: Int? = null,
    val extra: List<SetValues> = emptyList(),
    val perSide: Boolean = false,
    val setup: String? = null,
    val toFailure: Boolean = false,
    val note: String? = null,
    val raw: String? = null,
    val parsed: Boolean = true,
)

data class Block(val items: List<Item>, val restSec: Int? = null)

data class Session(
    val id: String,
    val date: LocalDate,
    val source: String,
    val notes: String?,
    val blocks: List<Block>,
    val warmup: String? = null,
)

enum class KmKind(override val key: String) : Keyed {
    NORMAL("normal"), FAST("fast"), RECOVERY("recovery"), UP("up"), DOWN("down")
}

data class Km(val kind: KmKind, val sec: Int?)

enum class RunType(override val key: String) : Keyed {
    EASY("easy"), LONG("long"), INTERVALS("intervals"), HILLS("hills"), RACE("race"), RECOVERY("recovery"), OTHER("other")
}

data class Run(
    val id: String,
    val startedAt: OffsetDateTime,
    val durationSec: Int?,
    val distanceM: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val type: RunType,
    val title: String?,
    val kms: List<Km>,
    val elevationGainM: Double?,
    val notes: String?,
    val source: String,
    val track: String? = null,
)

data class Warmup(val id: String, val name: String, val blocks: List<Block>)

data class Settings(val bodyweightKg: Double?, val sex: Sex?, val birthYear: Int?, val warmups: List<Warmup>)

data class TrainingData(
    val settings: Settings,
    val exercises: List<Exercise>,
    val sessions: List<Session>,
    val runs: List<Run>,
) {
    val exerciseById: Map<String, Exercise> by lazy { exercises.associateBy { it.id } }

    companion object {
        val EMPTY = TrainingData(Settings(null, null, null, emptyList()), emptyList(), emptyList(), emptyList())
    }
}
