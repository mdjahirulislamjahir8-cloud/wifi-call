package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.data.VideoQualityPreset
import com.example.ui.AppLanguage
import com.example.ui.UiStrings

@Composable
fun GuideAndSettingsScreen(
    strings: UiStrings,
    settings: UserSettings,
    onUpdateAlias: (String) -> Unit,
    onUpdateQuality: (VideoQualityPreset) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var aliasInput by remember(settings.deviceAlias) { mutableStateOf(settings.deviceAlias) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            // Device Alias + Language + Quality Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(strings.deviceAliasLabel, style = MaterialTheme.typography.titleSmall)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = aliasInput,
                                onValueChange = { aliasInput = it },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("alias_input")
                            )
                            Button(
                                onClick = { onUpdateAlias(aliasInput) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_alias_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null)
                            }
                        }

                        Text(strings.videoQualityLabel, style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoQualityPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = settings.videoQuality == preset,
                                    onClick = { onUpdateQuality(preset) },
                                    label = { Text(preset.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = settings.language == AppLanguage.BN,
                                onClick = { onUpdateLanguage(AppLanguage.BN) },
                                label = { Text("বাংলা") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lang_bn_chip")
                            )
                            FilterChip(
                                selected = settings.language == AppLanguage.EN,
                                onClick = { onUpdateLanguage(AppLanguage.EN) },
                                label = { Text("English") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lang_en_chip")
                            )
                        }
                    }
                }
            }

            // AAB Play Store Build Guide
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(strings.aabGuideTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = strings.aabGuideBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Source Architecture Guide
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(strings.sourceArchTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = strings.sourceArchBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
