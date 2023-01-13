package com.fakedevelopers.presentation.ui.productSearch

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.fakedevelopers.presentation.R
import com.fakedevelopers.presentation.api.datastore.DatastoreSetting.Companion.SEARCH_HISTORY
import com.fakedevelopers.presentation.api.datastore.DatastoreSetting.Companion.datastore
import com.fakedevelopers.presentation.databinding.FragmentProductSearchBinding
import com.fakedevelopers.presentation.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

@AndroidEntryPoint
class ProductSearchFragment : BaseFragment<FragmentProductSearchBinding>(
    R.layout.fragment_product_search
) {
    private val viewModel: ProductSearchViewModel by viewModels()
    private val args: ProductSearchFragmentArgs by navArgs()

    private val imm by lazy {
        requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private val searchHistory by lazy {
        requireContext().datastore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[SEARCH_HISTORY]?.toList() ?: listOf()
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.vm = viewModel
        initCollector()
        initListener()
    }

    private fun initCollector() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchHistory.collect {
                    viewModel.setHistoryList(it)
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchWord.collectLatest {
                    findNavController().apply {
                        getViewModelStoreOwner(R.id.nav_graph).viewModelStore.clear()
                        navigate(ProductSearchFragmentDirections.actionProductSearchFragmentToProductListFragment(it))
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historySet.collect {
                    requireContext().datastore.edit { preferences ->
                        preferences[SEARCH_HISTORY] = it
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchBar.collectLatest {
                    if (it.isNotEmpty()) {
                        binding.recyclerProductSearchResult.visibility = View.VISIBLE
                        binding.layoutProductSearchBeforeSearch.visibility = View.INVISIBLE
                        // 0.7초동안 키보드 조작이 없을때만 api를 요청한다.
                        delay(WAIT_BEFORE_REQUEST)
                        viewModel.requestSearchResult()
                    } else {
                        binding.recyclerProductSearchResult.visibility = View.INVISIBLE
                        binding.layoutProductSearchBeforeSearch.visibility = View.VISIBLE
                        viewModel.clearResult()
                    }
                }
            }
        }
    }

    private fun initListener() {
        binding.toolbarProductSearch.apply {
            edittextToolbarSearch.let {
                // 키보드 올리기 전에 포커싱을 줘야함
                it.requestFocus()
                imm.showSoftInput(it, 0)
                viewModel.setSearchBar(args.searchWord)
                it.setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        imm.hideSoftInputFromWindow(it.windowToken, 0)
                        viewModel.searchEvent(v.text.toString())
                    }
                    true
                }
            }
            buttonToolbarBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
        binding.textviewProductSearchEraseAll.setOnClickListener {
            viewModel.clearHistory()
        }
    }

    companion object {
        const val WAIT_BEFORE_REQUEST = 700L
    }
}