package com.vultisig.wallet.presenter.import_file

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.FileContentFetched
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.FileNameFetched
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.FileSelected
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.OnContinueClick
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.RemoveSelectedFile
import com.vultisig.wallet.presenter.import_file.ImportFileUiEvent.CopyFileToAppDir
import com.vultisig.wallet.presenter.import_file.ImportFileUiEvent.FetchFileName
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ImportFileViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    private val gson: Gson,
    private val navigator: Navigator<Destination>
) : ViewModel() {
    val uiModel = MutableStateFlow(ImportFileState())
    private val _channel = Channel<ImportFileUiEvent>()
    val channel = _channel.receiveAsFlow()
    fun onEvent(event: ImportFileEvent) {
        when (event) {
            is FileSelected -> fetchFileName(event.uri)
            OnContinueClick -> fetchFileContent()
            RemoveSelectedFile -> removeSelectedFile()
            is FileNameFetched -> updateFileName(event.fileName)
            is FileContentFetched -> insertContentToDb(event.fileContent)
        }
    }

    private fun insertContentToDb(fileContent: String?) {
        if (fileContent == null)
            return
        val fromJson = gson.fromJson(fileContent, Vault::class.java)
        viewModelScope.launch {
            val saveFile = async(Dispatchers.IO) {
                vaultDB.upsert(fromJson)
            }
            saveFile.join()
            navigator.navigate(Destination.Home)
        }
    }

    private fun updateFileName(fileName: String) {
        uiModel.update {
            it.copy(fileName = fileName)
        }
    }

    private fun removeSelectedFile() {
        uiModel.update {
            it.copy(fileUri = null, fileName = null, fileContent = null)
        }
    }

    private fun fetchFileContent() {
        val uri = uiModel.value.fileUri ?: return
        viewModelScope.launch {
            _channel.send(CopyFileToAppDir(uri))
        }
    }

    private fun fetchFileName(uri: Uri?) {
        uiModel.update {
            it.copy(fileUri = uri, fileName = null, fileContent = null)
        }
        if (uri == null)
            return
        viewModelScope.launch {
            _channel.send(FetchFileName(uri))
        }
    }
}