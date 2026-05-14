package com.chibiclaw.ui.initiative

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chibiclaw.agent.initiative.trigger.ComplexTrigger
import com.chibiclaw.agent.initiative.trigger.CompositeOp
import com.chibiclaw.agent.initiative.trigger.CronParser
import com.chibiclaw.agent.initiative.trigger.EventType
import com.chibiclaw.agent.initiative.trigger.TriggerFilter
import com.chibiclaw.data.repository.StandingInstructionRepository
import kotlinx.coroutines.launch

/**
 * StandingInstructionEditorScreen — guided form 3 tab.
 *
 * Tab 1 — Trigger:
 *   - Tipe: SIMPLE_TIME, SIMPLE_EVENT, SIMPLE_PREDICATE, COMPOSITE_AND_TIME_EVENT
 *   - Field per tipe ditampilkan dinamis
 *
 * Tab 2 — Task:
 *   - Task template + variabel `{{event.text}}` dll
 *
 * Tab 3 — Eksekusi:
 *   - Priority, cooldown, max fires/day, pre-authorized tools (CSV)
 *
 * Save button compile ke ComplexTrigger sealed class, persist via Repository.
 *
 * Untuk advanced user, Phase 9 polish bisa tambah raw JSON editor.
 */
@Composable
fun StandingInstructionEditorScreen(
    repository: StandingInstructionRepository,
    cronParser: CronParser,
    editingId: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var taskTemplate by remember { mutableStateOf("") }
    var triggerKind by remember { mutableStateOf(TriggerKind.TIME) }

    // Time fields
    var cronExpr by remember { mutableStateOf("0 7 * * *") }

    // Event fields
    var eventType by remember { mutableStateOf(EventType.NOTIFICATION_POSTED) }
    var filterPackage by remember { mutableStateOf("") }
    var filterTitleRegex by remember { mutableStateOf("") }
    var filterTextRegex by remember { mutableStateOf("") }

    // Predicate
    var predicateExpr by remember { mutableStateOf("battery.level < 30") }

    // Composite: combine time + event with AND
    var includeTimeInComposite by remember { mutableStateOf(true) }

    // Execution params
    var priority by remember { mutableStateOf("3") }
    var cooldownMinutes by remember { mutableStateOf("0") }
    var maxFiresPerDay by remember { mutableStateOf("-1") }
    var preAuthorizedToolsCsv by remember { mutableStateOf("") }

    var selectedTab by remember { mutableIntStateOf(0) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editingId) {
        if (editingId == null) return@LaunchedEffect
        val entity = repository.get(editingId) ?: return@LaunchedEffect
        name = entity.name
        description = entity.description
        taskTemplate = entity.taskTemplate
        priority = entity.priority.toString()
        cooldownMinutes = (entity.cooldownMs / 60_000L).toString()
        maxFiresPerDay = entity.maxFiresPerDay.toString()
        preAuthorizedToolsCsv = entity.preAuthorizedToolsCsv
        val parsed = repository.parseTrigger(entity) ?: return@LaunchedEffect
        when (parsed) {
            is ComplexTrigger.Time -> {
                triggerKind = TriggerKind.TIME
                cronExpr = parsed.cron
            }
            is ComplexTrigger.Event -> {
                triggerKind = TriggerKind.EVENT
                eventType = parsed.eventType
                filterPackage = parsed.filter.packageName.orEmpty()
                filterTitleRegex = parsed.filter.titleRegex.orEmpty()
                filterTextRegex = parsed.filter.textRegex.orEmpty()
            }
            is ComplexTrigger.Predicate -> {
                triggerKind = TriggerKind.PREDICATE
                predicateExpr = parsed.expression
            }
            is ComplexTrigger.Composite -> {
                triggerKind = TriggerKind.COMPOSITE_TIME_EVENT
                parsed.children.forEach { child ->
                    when (child) {
                        is ComplexTrigger.Time -> {
                            includeTimeInComposite = true
                            cronExpr = child.cron
                        }
                        is ComplexTrigger.Event -> {
                            eventType = child.eventType
                            filterPackage = child.filter.packageName.orEmpty()
                            filterTitleRegex = child.filter.titleRegex.orEmpty()
                            filterTextRegex = child.filter.textRegex.orEmpty()
                        }
                        else -> Unit
                    }
                }
            }
            is ComplexTrigger.Geofence -> { /* TBD UI */ }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (editingId == null) "Buat Instruction" else "Edit Instruction",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        TabRow(selectedTabIndex = selectedTab) {
            listOf("Trigger", "Task", "Eksekusi").forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = { Text(label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (selectedTab) {
                0 -> TriggerTab(
                    triggerKind, { triggerKind = it },
                    cronExpr, { cronExpr = it }, cronParser,
                    eventType, { eventType = it },
                    filterPackage, { filterPackage = it },
                    filterTitleRegex, { filterTitleRegex = it },
                    filterTextRegex, { filterTextRegex = it },
                    predicateExpr, { predicateExpr = it },
                    includeTimeInComposite, { includeTimeInComposite = it },
                )
                1 -> TaskTab(
                    name, { name = it },
                    description, { description = it },
                    taskTemplate, { taskTemplate = it },
                )
                2 -> ExecutionTab(
                    priority, { priority = it },
                    cooldownMinutes, { cooldownMinutes = it },
                    maxFiresPerDay, { maxFiresPerDay = it },
                    preAuthorizedToolsCsv, { preAuthorizedToolsCsv = it },
                )
            }
        }

        saveError?.let {
            Text(
                text = "⚠️ $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { Text("Batal") }
            Button(
                onClick = {
                    val trigger = buildTrigger(
                        triggerKind, cronExpr, cronParser,
                        eventType, filterPackage, filterTitleRegex, filterTextRegex,
                        predicateExpr, includeTimeInComposite,
                    )
                    val err = validate(name, taskTemplate, trigger)
                    if (err != null) {
                        saveError = err
                        return@Button
                    }
                    saveError = null
                    scope.launch {
                        repository.upsert(
                            id = editingId,
                            name = name,
                            description = description,
                            trigger = trigger!!,
                            taskTemplate = taskTemplate,
                            enabled = true,
                            priority = priority.toIntOrNull()?.coerceIn(1, 5) ?: 3,
                            cooldownMs = (cooldownMinutes.toLongOrNull() ?: 0L) * 60_000L,
                            maxFiresPerDay = maxFiresPerDay.toIntOrNull() ?: -1,
                            preAuthorizedTools = preAuthorizedToolsCsv.split(',')
                                .map { it.trim() }.filter { it.isNotEmpty() },
                        )
                        onDone()
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { Text(if (editingId == null) "Buat" else "Simpan") }
        }
    }
}

private enum class TriggerKind { TIME, EVENT, PREDICATE, COMPOSITE_TIME_EVENT }

@Composable
private fun TriggerTab(
    kind: TriggerKind, onKindChange: (TriggerKind) -> Unit,
    cronExpr: String, onCronChange: (String) -> Unit, cronParser: CronParser,
    eventType: EventType, onEventTypeChange: (EventType) -> Unit,
    filterPackage: String, onFilterPackageChange: (String) -> Unit,
    filterTitleRegex: String, onFilterTitleRegexChange: (String) -> Unit,
    filterTextRegex: String, onFilterTextRegexChange: (String) -> Unit,
    predicateExpr: String, onPredicateExprChange: (String) -> Unit,
    includeTimeInComposite: Boolean, onIncludeTimeChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tipe trigger:", style = MaterialTheme.typography.labelLarge)
        TriggerKind.values().forEach { k ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(
                    selected = kind == k, onClick = { onKindChange(k) },
                )
                Text(
                    text = when (k) {
                        TriggerKind.TIME -> "Time (cron)"
                        TriggerKind.EVENT -> "Event"
                        TriggerKind.PREDICATE -> "Predicate"
                        TriggerKind.COMPOSITE_TIME_EVENT -> "Composite (Time AND Event)"
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (kind == TriggerKind.TIME || (kind == TriggerKind.COMPOSITE_TIME_EVENT && includeTimeInComposite)) {
            OutlinedTextField(
                value = cronExpr,
                onValueChange = onCronChange,
                label = { Text("Cron expression (e.g. \"0 7 * * MON-FRI\")") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = !cronParser.isValid(cronExpr),
                supportingText = {
                    Text(if (cronParser.isValid(cronExpr)) "Valid" else "Cron tidak valid")
                },
            )
        }

        if (kind == TriggerKind.EVENT || kind == TriggerKind.COMPOSITE_TIME_EVENT) {
            Text("Event type:", style = MaterialTheme.typography.labelLarge)
            // Simple dropdown via wrap of RadioButton row
            EventType.values().take(8).forEach { et ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = eventType == et, onClick = { onEventTypeChange(et) },
                    )
                    Text(et.name)
                }
            }
            OutlinedTextField(
                value = filterPackage,
                onValueChange = onFilterPackageChange,
                label = { Text("Filter: package (e.g. com.whatsapp)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = filterTitleRegex,
                onValueChange = onFilterTitleRegexChange,
                label = { Text("Filter: title regex (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = filterTextRegex,
                onValueChange = onFilterTextRegexChange,
                label = { Text("Filter: text regex (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (kind == TriggerKind.PREDICATE) {
            OutlinedTextField(
                value = predicateExpr,
                onValueChange = onPredicateExprChange,
                label = { Text("Predicate (e.g. battery.level < 30)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Accessor: battery.level, battery.charging, screen.on, network.connected, time.hour, time.minute, time.weekday, event.text, event.title, event.package, event.value, location.distance_from(...)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskTab(
    name: String, onNameChange: (String) -> Unit,
    description: String, onDescriptionChange: (String) -> Unit,
    taskTemplate: String, onTaskTemplateChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nama (singkat)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Deskripsi (opsional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = taskTemplate,
            onValueChange = onTaskTemplateChange,
            label = { Text("Task template") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Text(
            text = "Variabel: {{event.text}}, {{event.title}}, {{event.package}}, {{event.value}}, {{time.hour}}, {{trigger.name}}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExecutionTab(
    priority: String, onPriorityChange: (String) -> Unit,
    cooldownMinutes: String, onCooldownChange: (String) -> Unit,
    maxFiresPerDay: String, onMaxFiresChange: (String) -> Unit,
    preAuthorizedToolsCsv: String, onPreAuthChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = priority,
            onValueChange = onPriorityChange,
            label = { Text("Priority (1-5, default 3)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cooldownMinutes,
            onValueChange = onCooldownChange,
            label = { Text("Cooldown (menit)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = maxFiresPerDay,
            onValueChange = onMaxFiresChange,
            label = { Text("Max fires per hari (-1 = unlimited)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = preAuthorizedToolsCsv,
            onValueChange = onPreAuthChange,
            label = { Text("Pre-authorized tools (CSV)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Contoh: a11y_click,a11y_type,messaging. HIGH severity tools yang listed di sini akan skip overlay konfirmasi saat task channel=STANDING.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildTrigger(
    kind: TriggerKind, cronExpr: String, cronParser: CronParser,
    eventType: EventType, filterPackage: String, filterTitleRegex: String, filterTextRegex: String,
    predicateExpr: String, includeTimeInComposite: Boolean,
): ComplexTrigger? {
    val eventTrigger = ComplexTrigger.Event(
        eventType = eventType,
        filter = TriggerFilter(
            packageName = filterPackage.takeIf { it.isNotBlank() },
            titleRegex = filterTitleRegex.takeIf { it.isNotBlank() },
            textRegex = filterTextRegex.takeIf { it.isNotBlank() },
        ),
    )
    return when (kind) {
        TriggerKind.TIME -> {
            if (!cronParser.isValid(cronExpr)) return null
            ComplexTrigger.Time(cron = cronExpr)
        }
        TriggerKind.EVENT -> eventTrigger
        TriggerKind.PREDICATE -> ComplexTrigger.Predicate(expression = predicateExpr)
        TriggerKind.COMPOSITE_TIME_EVENT -> {
            if (!includeTimeInComposite) return eventTrigger
            if (!cronParser.isValid(cronExpr)) return null
            ComplexTrigger.Composite(
                op = CompositeOp.AND,
                children = listOf(ComplexTrigger.Time(cron = cronExpr), eventTrigger),
            )
        }
    }
}

private fun validate(name: String, taskTemplate: String, trigger: ComplexTrigger?): String? {
    if (name.isBlank()) return "Nama wajib diisi"
    if (taskTemplate.isBlank()) return "Task template wajib diisi"
    if (trigger == null) return "Trigger tidak valid (cek cron expression / field)"
    return null
}
