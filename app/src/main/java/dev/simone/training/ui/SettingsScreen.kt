package dev.simone.training.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import dev.simone.training.data.Settings
import dev.simone.training.data.Sex
import dev.simone.training.data.Warmup
import dev.simone.training.data.snakeId
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.simone.training.AppViewModel
import dev.simone.training.data.TrainingData
import java.time.LocalDate

@Composable
fun ImportButton(vm: AppViewModel, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readImport) }
    Slab("Import", { launcher.launch(arrayOf("*/*")) }, modifier)
}

@Composable
fun ProfileFields(settings: Settings, onChange: (Settings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Choices(Sex.entries, settings.sex, { it?.key.orEmpty() }) { onChange(settings.copy(sex = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Bodyweight kg", settings.bodyweightKg, { onChange(settings.copy(bodyweightKg = it)) }, Modifier.weight(1f))
            IntField("Birth year", settings.birthYear, { onChange(settings.copy(birthYear = it)) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, data: TrainingData) {
    var draft by remember(data.settings) { mutableStateOf(data.settings) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    fun setWarmup(index: Int, warmup: Warmup) {
        draft = draft.copy(warmups = draft.warmups.toMutableList().also { it[index] = warmup })
    }
    WithExercisePicker(data, { e -> vm.update { it.copy(exercises = it.exercises + e) } }) { pick ->
        Column {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "top") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileFields(draft) { draft = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Slab("Export", { export.launch("training-${LocalDate.now()}.json") }, Modifier.weight(1f))
                            ImportButton(vm, Modifier.weight(1f))
                        }
                    }
                }
                draft.warmups.forEachIndexed { wi, warmup ->
                    item(key = "w-${warmup.id}") {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextIn("Warmup", warmup.name, { setWarmup(wi, warmup.copy(name = it ?: "")) }, Modifier.weight(1f))
                            IconButton(onClick = { draft = draft.copy(warmups = draft.warmups.filter { it.id != warmup.id }) }) {
                                Icon(Icons.Default.Delete, "Delete warmup", tint = Muted)
                            }
                        }
                    }
                    blockItems("w-${warmup.id}", data, warmup.blocks, { setWarmup(wi, warmup.copy(blocks = it)) }, pick)
                }
                item(key = "add-warmup") {
                    Slab("+ Warmup", {
                        val name = "Circuit ${draft.warmups.size + 1}"
                        draft = draft.copy(warmups = draft.warmups + Warmup(snakeId(name, draft.warmups.map { it.id }.toSet()), name, emptyList()))
                    }, Modifier.fillMaxWidth())
                }
            }
            if (draft != data.settings) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Slab("Save", { vm.update { it.copy(settings = draft.copy(warmups = draft.warmups.map { w -> w.copy(name = w.name.trim().ifEmpty { w.id }) })) } }, Modifier.fillMaxWidth(), filled = true)
                }
            }
        }
    }
}
