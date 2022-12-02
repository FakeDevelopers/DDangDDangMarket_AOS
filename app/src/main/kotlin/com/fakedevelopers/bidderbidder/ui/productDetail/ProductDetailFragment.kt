package com.fakedevelopers.bidderbidder.ui.productDetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.fakedevelopers.bidderbidder.R
import com.fakedevelopers.bidderbidder.databinding.FragmentProductDetailBinding
import com.fakedevelopers.bidderbidder.ui.util.repeatOnStarted
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductDetailViewModel by viewModels()

    private val productDetailAdapter by lazy { ProductDetailAdapter() }

    private val onPageChanged = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            setPagerCount(position)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_product_detail,
            container,
            false
        )
        return binding.run {
            vm = viewModel
            lifecycleOwner = viewLifecycleOwner
            root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args: ProductDetailFragmentArgs by navArgs()
        if (args.productId != -1L) {
            viewModel.productDetailRequest(args.productId)
        }
        binding.viewpagerProductDetailPictures.run {
            adapter = productDetailAdapter
            registerOnPageChangeCallback(onPageChanged)
        }
        initCollector()
    }

    private fun initCollector() {
        repeatOnStarted(viewLifecycleOwner) {
            viewModel.sendMessageEvent.collectLatest { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
        repeatOnStarted(viewLifecycleOwner) {
            viewModel.productPictures.collectLatest { uri ->
                productDetailAdapter.submitList(uri)
                setPagerCount(binding.viewpagerProductDetailPictures.currentItem)
            }
        }
    }

    private fun setPagerCount(position: Int) {
        binding.textviewProductDetailPictureCount.text =
            getString(R.string.product_detail_picture_count, position + 1, productDetailAdapter.itemCount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.viewpagerProductDetailPictures.unregisterOnPageChangeCallback(onPageChanged)
        _binding = null
    }
}
