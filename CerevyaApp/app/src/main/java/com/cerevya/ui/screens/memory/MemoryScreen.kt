package com.cerevya.ui.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cerevya.domain.models.MemoryEntity
import com.cerevya.ui.components.MemoryCard
import com.cerevya.ui.components.SearchBar
import com.cerevya.ui.components.TagChip
import com.cerevya.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val memories by viewModel.memories.collectAsState()
    var memoryToDelete by remember { mutableStateOf<MemoryEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Memórias",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            placeholder = "Pesquisar memórias..."
        )

        if (memories.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.searchQuery.isEmpty()) {
                        "Nenhuma memória salva ainda.\nVá ao chat e digite 'salva isso: [sua ideia]'"
                    } else {
                        "Nenhum resultado encontrado."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onEdit = { viewModel.showEditDialog(memory) },
                        onDelete = { memoryToDelete = memory }
                    )
                }
            }
        }
    }

    // Show selected memory detail
    uiState.selectedMemory?.let { memory ->
        MemoryDetailDialog(
            memory = memory,
            onDismiss = { viewModel.clearSelection() },
            onEdit = {
                viewModel.clearSelection()
                viewModel.showEditDialog(memory)
            },
            onDelete = {
                viewModel.clearSelection()
                memoryToDelete = memory
            }
        )
    }

    if (uiState.showEditDialog && uiState.editingMemory != null) {
        EditMemoryDialog(
            memory = uiState.editingMemory!!,
            onDismiss = viewModel::hideEditDialog,
            onSave = { content, category, tags ->
                viewModel.updateMemory(content, category, tags)
            }
        )
    }

    memoryToDelete?.let { memory ->
        DeleteConfirmationDialog(
            onConfirm = {
                viewModel.deleteMemory(memory)
                memoryToDelete = null
            },
            onDismiss = { memoryToDelete = null }
        )
    }
}

@Composable
fun MemoryDetailDialog(
    memory: MemoryEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = memory.category,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (memory.tags.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        memory.tags.split(",").forEach { tag ->
                            TagChip(tag = tag.trim())
                        }
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text(
                    text = "Criado: ${formatDateTime(memory.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                
                if (memory.updatedAt != memory.createdAt) {
                    Text(
                        text = "Atualizado: ${formatDateTime(memory.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        dismissButton = {
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text("Editar")
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Excluir", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
private fun EditMemoryDialog(
    memory: MemoryEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var content by remember { mutableStateOf(memory.content) }
    var category by remember { mutableStateOf(memory.category) }
    var tags by remember { mutableStateOf(memory.tags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Editar Memória",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Conteúdo") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoria") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (separadas por vírgula)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(content, category, tags) },
                enabled = content.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Excluir Memória",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Tem certeza que deseja excluir esta memória? Esta ação não pode ser desfeita.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Excluir", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
