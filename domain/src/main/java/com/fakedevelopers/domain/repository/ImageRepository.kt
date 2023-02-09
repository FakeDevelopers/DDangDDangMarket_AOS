package com.fakedevelopers.domain.repository

import com.fakedevelopers.domain.model.AlbumItem
import com.fakedevelopers.domain.model.MediaInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface ImageRepository {
    fun isValid(uri: String): Boolean
    suspend fun getImages(path: String?): List<AlbumItem>
    fun getValidUris(uris: List<String>): List<String>
    fun getDateModifiedByUri(uri: String): AlbumItem?
    suspend fun getBytesByUri(uri: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): ByteArray?
    fun getMediaInfo(uri: String): MediaInfo
    fun getRotate(uri: String): Float
}
