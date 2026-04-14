package com.chibiclaw.ui.components

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.chibiclaw.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/**
 * Reusable app picker. Shows all launcher-visible installed apps with icons
 * + labels, supports fuzzy search, and lets the caller pick ONE or MANY
 * packages depending on [multiSelect]. Returns only package names — the
 * caller is responsible for persisting them.
 *
 * Typical usage:
 * ```
 * var showPicker by remember { mutableStateOf(false) }
 * if (showPicker) AppPickerDialog(
 *     multiSelect = true,
 *     preselected = currentWhitelist,
 *     onDismiss = { showPicker = false },
 *     onConfirm = { picked ->
 *         viewModel.setWhitelist(picked)
 *         showPicker = false
 *     }
 * )
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    multiSelect: Boolean = true,
    preselected: Set<String> = emptySet(),
    title: String = if (multiSelect) "Pilih App" else "Pilih Satu App",
    hideSystemApps: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel()
) {
    val apps by viewModel.filteredApps.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val query by viewModel.query.collectAsState()

    // Local selection state — seeded once from `preselected` so repeated
    // recompositions don't wipe user picks.
    val selection = remember {
        mutableStateOf(preselected.toSet())
    }

    LaunchedEffect(hideSystemApps) {
        viewModel.setHideSystem(hideSystemApps)
        viewModel.load(forceRefresh = false)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (multiSelect && selection.value.isNotEmpty()) {
                        Text(
                            "${selection.value.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { viewModel.load(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Cari app…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )

                Spacer(Modifier.height(8.dp))

                // App list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (apps.isEmpty()) {
                        Text(
                            "Tidak ada app yang cocok.",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(apps, key = { it.packageName }) { app ->
                                val isSelected = app.packageName in selection.value
                                AppRow(
                                    info = app,
                                    selected = isSelected,
                                    onClick = {
                                        selection.value = if (multiSelect) {
                                            if (isSelected) selection.value - app.packageName
                                            else selection.value + app.packageName
                                        } else {
                                            setOf(app.packageName)
                                        }
                                        if (!multiSelect) {
                                            onConfirm(selection.value)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Footer actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    if (multiSelect) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onConfirm(selection.value) }) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    info: InstalledAppsProvider.AppInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(drawable = info.icon, fallback = info.label.firstOrNull()?.uppercaseChar() ?: '?')
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                info.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                info.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (info.isSystem) {
            Text(
                "sys",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Renders a [Drawable] in Compose via an embedded [ImageView]. This is the
 * zero-dependency path that handles adaptive icons (API 26+) correctly —
 * Coil/accompanist-drawablepainter would be cleaner but we don't have
 * either on the classpath and app icons are a one-shot use case.
 */
@Composable
private fun AppIcon(drawable: Drawable?, fallback: Char) {
    if (drawable == null) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallback.toString(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        return
    }
    AndroidView(
        modifier = Modifier.size(40.dp),
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { it.setImageDrawable(drawable) }
    )
}

/**
 * Picker-scoped ViewModel. Keeps the filtered list in sync with the search
 * query without rebuilding the full inventory on every keystroke.
 */
@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider
) : ViewModel() {

    private val _allApps = MutableStateFlow<List<InstalledAppsProvider.AppInfo>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _hideSystem = MutableStateFlow(false)

    /** Derived list — re-filtered on every query or hide-system change. */
    private val _filtered = MutableStateFlow<List<InstalledAppsProvider.AppInfo>>(emptyList())
    val filteredApps: StateFlow<List<InstalledAppsProvider.AppInfo>> = _filtered.asStateFlow()

    fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            val apps = installedAppsProvider.getInstalledApps(forceRefresh = forceRefresh)
            _allApps.value = apps
            refilter()
            _loading.value = false
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        refilter()
    }

    fun setHideSystem(hide: Boolean) {
        if (_hideSystem.value == hide) return
        _hideSystem.value = hide
        refilter()
    }

    private fun refilter() {
        val q = _query.value.trim().lowercase()
        val hideSys = _hideSystem.value
        val base = _allApps.value
        val out = base.asSequence()
            .filter { !hideSys || !it.isSystem }
            .filter {
                if (q.isEmpty()) true
                else it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
            .toList()
        _filtered.value = out
    }
}
