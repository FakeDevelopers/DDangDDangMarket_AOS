package com.fakedevelopers.presentation.ui.productRegistration.albumList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakedevelopers.domain.model.AlbumItem
import com.fakedevelopers.domain.usecase.GetDateModifiedByUriUseCase
import com.fakedevelopers.domain.usecase.GetValidUrisUseCase
import com.fakedevelopers.domain.usecase.IsValidUriUseCase
import com.fakedevelopers.presentation.ui.productRegistration.SelectedPictureListAdapter
import com.fakedevelopers.presentation.ui.productRegistration.albumList.AlbumListFragment.Companion.ALL_PICTURES
import com.fakedevelopers.presentation.ui.util.MutableEventFlow
import com.fakedevelopers.presentation.ui.util.asEventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    isValidUriUseCase: IsValidUriUseCase,
    private val getDateModifiedFromUriUseCase: GetDateModifiedByUriUseCase,
    private val getValidUrisUseCase: GetValidUrisUseCase
) : ViewModel() {
    private val _albumViewMode = MutableStateFlow(AlbumViewState.GRID)
    val albumViewMode: StateFlow<AlbumViewState> get() = _albumViewMode

    private val _onListChange = MutableEventFlow<Boolean>()
    val onListChange = _onListChange.asEventFlow()

    private val _selectErrorImage = MutableEventFlow<Boolean>()
    val selectErrorImage = _selectErrorImage.asEventFlow()

    private val _startViewPagerIndex = MutableEventFlow<Int>()
    val startViewPagerIndex = _startViewPagerIndex.asEventFlow()

    private val _imageCountEvent = MutableEventFlow<Int>()
    val imageCountEvent = _imageCountEvent.asEventFlow()

    private val _editButtonEnableState = MutableStateFlow(false)
    val editButtonEnableState: StateFlow<Boolean> get() = _editButtonEnableState

    private val updatedImageList = hashSetOf<String>()

    private var currentAlbum = ""
    private var totalPictureCount = 0
    private var allImages = mapOf<String, MutableList<AlbumItem>>()
    val selectedImageInfo = SelectedImageInfo()

    // 현재 뷰 페이저 인덱스
    var currentViewPagerIdx = 0
        private set

    // 앨범 전환 시 리스트를 탑으로 올리기 위한 플래그
    var scrollToTopFlag = false
        private set

    // 그리드 앨범 리스트 어뎁터
    val albumListAdapter = AlbumListAdapter(
        isValidUri = { uri -> isValidUriUseCase(uri) },
        findSelectedImageIndex = { findSelectedImageIndex(it) },
        sendErrorToast = { viewModelScope.launch { _selectErrorImage.emit(true) } },
        showViewPager = { uri -> showViewPager(uri) }
    ) { uri, state ->
        setSelectedState(uri, state)
    }

    // 뷰 페이저 앨범 리스트 어뎁터
    val albumPagerAdapter = AlbumPagerAdapter(
        isValidUri = { uri -> isValidUriUseCase(uri) },
        sendErrorToast = { viewModelScope.launch { _selectErrorImage.emit(true) } },
        getEditedImage = { uri -> getEditedBitmapInfo(uri) }
    ) { uri ->
        setSelectedState(uri, findSelectedImageIndex(uri) == -1)
    }

    // 선택 사진 리스트 어뎁터
    val selectedPictureAdapter = SelectedPictureListAdapter(
        deleteSelectedImage = {
            setSelectedState(it)
            albumListAdapter.refreshSelectedOrder()
        },
        findSelectedImageIndex = { findSelectedImageIndex(it) },
        swapSelectedImage = { fromPosition, toPosition -> swapSelectedImage(fromPosition, toPosition) }
    )

    fun initSelectedImageList(selectedImageInfo: SelectedImageInfo) {
        this.selectedImageInfo.apply {
            uris = selectedImageInfo.uris
            changeBitmaps.putAll(selectedImageInfo.changeBitmaps)
        }
        viewModelScope.launch {
            // 선택 이미지를 세팅 해주고
            setSelectedImageList()
            // 편집 버튼 활성화
            _editButtonEnableState.emit(true)
        }
    }

    fun initAlbumInfo(map: Map<String, MutableList<AlbumItem>>) {
        allImages = map
    }

    // 수정된 이미지 비트맵 추가
    fun addBitmapInfo(uri: String, bitmapInfo: BitmapInfo) {
        selectedImageInfo.changeBitmaps[uri] = bitmapInfo
    }

    // 수정된 이미지 비트맵 삭제
    fun removeBitmapInfo(uri: String) {
        selectedImageInfo.changeBitmaps.remove(uri)
    }

    fun switchScrollFlag() {
        scrollToTopFlag = !scrollToTopFlag
    }

    fun setCurrentViewPagerIdx(idx: Int) {
        currentViewPagerIdx = idx
        setImageSelectCount(findSelectedImageIndex(getCurrentUri()))
    }

    fun setAlbumViewMode(state: AlbumViewState) {
        // 보기 모드를 전환하기 전에 변경 사항을 반영해준다
        if (state == AlbumViewState.GRID) {
            albumListAdapter.refreshAll()
        }
        viewModelScope.launch {
            _albumViewMode.emit(state)
        }
    }

    fun setSelectedImage(list: List<String>) {
        selectedImageInfo.uris.filter { !list.contains(it) }.forEach { uri ->
            removeImage(uri)
            selectedImageInfo.changeBitmaps.remove(uri)
        }
        viewModelScope.launch {
            selectedPictureAdapter.submitList(list.toMutableList())
            if (list.isNotEmpty() && !list.contains(selectedImageInfo.uris[0])) {
                selectedPictureAdapter.notifyItemChanged(findSelectedImageIndex(list[0]))
            }
            setAdapterList()
        }
        selectedImageInfo.uris = list.toMutableList()
        albumListAdapter.refreshSelectedOrder()
    }

    fun setSelectedState(uri: String, state: Boolean = false) {
        if (state) {
            selectedImageInfo.uris.add(uri)
        } else {
            val idx = findSelectedImageIndex(uri)
            selectedImageInfo.uris.removeAt(idx)
            // 수정된 내용(BitmapInfo)도 같이 삭제
            if (selectedImageInfo.changeBitmaps.remove(uri) != null) {
                // 페이저에 보이는 이미지 원상 복구
                allImages[currentAlbum]?.let { list ->
                    albumPagerAdapter.notifyItemChanged(list.indexOfFirst { it.uri == uri })
                }
            }
            // 첫번째 사진이 삭제 된다면 다음 사진에게 대표직을 물려줌
            if (selectedImageInfo.uris.isNotEmpty() && idx == 0) {
                selectedPictureAdapter.notifyItemChanged(1)
            }
        }
        // 선택 상태를 변경해준다.
        viewModelScope.launch {
            if (state) {
                setImageSelectCount(selectedImageInfo.uris.lastIndex)
            } else {
                setImageSelectCount(-1)
            }
        }
        // 선택 이미지가 남아 있으면 편집 버튼 활성화
        viewModelScope.launch {
            _editButtonEnableState.emit(selectedImageInfo.uris.isNotEmpty())
        }
        setSelectedImageList()
    }

    fun findSelectedImageIndex(uri: String) = selectedImageInfo.uris.indexOf(uri)

    fun getCurrentPositionString(position: Int) = "$position / $totalPictureCount"

    fun getCurrentUri() = allImages[currentAlbum]?.get(currentViewPagerIdx)?.uri ?: ""

    fun getPictureUri(albumName: String = currentAlbum, position: Int) =
        allImages[albumName]?.get(position)?.uri ?: ""

    // 수정된 비트맵 가져오기
    fun getEditedBitmapInfo(uri: String) =
        selectedImageInfo.changeBitmaps[uri]

    fun isAlbumListChanged() =
        albumListAdapter.currentList[0] == allImages[currentAlbum]?.get(0)

    fun onAlbumListUpdated(uri: String) {
        updatedImageList.add(uri)
    }

    // 편집 버튼 클릭
    fun onEditButtonClick() {
        showViewPager(selectedImageInfo.uris.last())
    }

    fun updateAlbumList(
        albumName: String? = null
    ) {
        val updatedAlbumItems = getValidUpdatedImageList()
        updatedImageList.forEach { uri ->
            removeImage(uri)
        }
        // 앨범 리스트 갱신
        updatedAlbumItems.forEach { item ->
            val albumItem = AlbumItem(item.uri, item.modified)
            allImages[ALL_PICTURES]?.add(albumItem)
            allImages[item.path]?.add(albumItem)
        }
        // 수정된 날짜 기준으로 소팅
        if (updatedAlbumItems.isNotEmpty()) {
            for (key in allImages.keys) {
                allImages[key]?.sortByDescending { it.modified }
            }
        }
        updatedImageList.clear()
        setAdapterList(albumName ?: currentAlbum)
    }

    fun checkSelectedImages(idx: Int? = null) {
        // 선택 이미지 리스트가 존재한다면 유효한지 검사
        if (selectedImageInfo.uris.isNotEmpty()) {
            // 유효한 선택 이미지 리스트로 갱신
            setSelectedImage(getValidUrisUseCase(selectedImageInfo.uris))
            if (albumViewMode.value == AlbumViewState.PAGER && idx != null) {
                setImageSelectCount(
                    findSelectedImageIndex(getPictureUri(position = idx))
                )
            }
        }
    }

    private fun removeImage(uri: String) {
        val targetImage = allImages[ALL_PICTURES]?.find { it.uri == uri } ?: return
        for (key in allImages.keys) {
            allImages[key]?.remove(targetImage)
        }
    }

    private fun setAdapterList(albumName: String = currentAlbum) {
        allImages[albumName]?.let { list ->
            val currentList = mutableListOf<AlbumItem>().apply { addAll(list) }
            albumListAdapter.submitList(currentList)
            albumPagerAdapter.submitList(currentList)
            totalPictureCount = list.size
        }
        if (albumName != currentAlbum) {
            currentAlbum = albumName
            // 앨범을 바꿀 때 최상위 스크롤을 해주는 플래그를 true로 바꿔준다.
            switchScrollFlag()
        }
    }

    // 앨범 뷰 페이저
    private fun showViewPager(uri: String) {
        allImages[currentAlbum]?.let { album ->
            val idx = album.indexOf(album.find { it.uri == uri })
            if (idx != -1) {
                viewModelScope.launch {
                    _startViewPagerIndex.emit(idx)
                }
                setAlbumViewMode(AlbumViewState.PAGER)
            }
        }
    }

    private fun setSelectedImageList() {
        selectedPictureAdapter.submitList(selectedImageInfo.uris.toMutableList())
        viewModelScope.launch {
            _onListChange.emit(true)
        }
    }

    private fun swapSelectedImage(fromPosition: Int, toPosition: Int) {
        Collections.swap(selectedImageInfo.uris, fromPosition, toPosition)
        selectedPictureAdapter.submitList(selectedImageInfo.uris.toMutableList())
        albumListAdapter.refreshSelectedOrder()
    }

    private fun getValidUpdatedImageList() =
        updatedImageList.mapNotNull { getDateModifiedFromUriUseCase(it) }

    private fun setImageSelectCount(idx: Int) {
        viewModelScope.launch {
            _imageCountEvent.emit(idx)
        }
    }
}