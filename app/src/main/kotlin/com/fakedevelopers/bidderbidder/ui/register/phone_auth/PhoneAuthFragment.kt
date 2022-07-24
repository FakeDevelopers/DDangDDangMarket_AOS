package com.fakedevelopers.bidderbidder.ui.register.phone_auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fakedevelopers.bidderbidder.MainActivity
import com.fakedevelopers.bidderbidder.R
import com.fakedevelopers.bidderbidder.databinding.FragmentPhoneAuthBinding
import com.fakedevelopers.bidderbidder.ui.register.RegistrationProgressState.INPUT_BIRTH
import com.fakedevelopers.bidderbidder.ui.register.RegistrationProgressState.PHONE_AUTH_CHECK_AUTH_CODE
import com.fakedevelopers.bidderbidder.ui.register.UserRegistrationViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class PhoneAuthFragment : Fragment() {

    private var _binding: FragmentPhoneAuthBinding? = null

    private val binding get() = _binding!!
    private val phoneAuthViewModel: PhoneAuthViewModel by viewModels()
    private val userRegistrationViewModel: UserRegistrationViewModel by lazy {
        ViewModelProvider(requireActivity())[UserRegistrationViewModel::class.java]
    }
    private val mainActivity by lazy { activity as MainActivity }

    private val callbacks by lazy {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // 인증이 끝난 상태
            }
            // 인증 실패 상태
            // 보통 할당량이 다 떨어지면 여기로 간다
            override fun onVerificationFailed(e: FirebaseException) {
                Toast.makeText(requireContext(), "저런 실패했군요!", Toast.LENGTH_SHORT).show()
                phoneAuthViewModel.setCodeSendingStates(PhoneAuthState.INIT)
            }
            // 전화번호는 확인 했고 인증코드를 입력해야 하는 상태
            override fun onCodeSent(verificationCode: String, resendingToken: PhoneAuthProvider.ForceResendingToken) {
                phoneAuthViewModel.apply {
                    // 타이머 시작
                    startTimer()
                    // 인증 id 저장
                    setVerificationCodeAndResendingToken(verificationCode, resendingToken)
                    setCodeSendingStates(PhoneAuthState.SENT)
                }
                userRegistrationViewModel.setCurrentStep(PHONE_AUTH_CHECK_AUTH_CODE)
                super.onCodeSent(verificationCode, resendingToken)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_phone_auth,
            container,
            false
        )
        return binding.run {
            vm = phoneAuthViewModel
            lifecycleOwner = viewLifecycleOwner
            root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListener()
        initCollector()
    }

    private fun initListener() {
        // 인증 번호 발송 버튼
        binding.buttonPhoneauthSendCode.setOnClickListener {
            sendPhoneAuthCode()
        }
    }

    private fun initCollector() {
        // 코드 발송 상태에 따라 버튼 메세지가 바뀜
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                phoneAuthViewModel.codeSendingStates.collectLatest { state ->
                    handlePhoneAuthEvent(state)
                }
            }
        }
        // 인증 코드 검사 요청
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRegistrationViewModel.checkAuthCode.collectLatest {
                    signInWithPhoneAuthCredential()
                }
            }
        }
        // 타이머 visibility
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                phoneAuthViewModel.timerVisibility.collectLatest {
                    binding.textviewPhoneauthTimer.visibility = if (it) View.VISIBLE else View.INVISIBLE
                }
            }
        }
    }

    private fun handlePhoneAuthEvent(state: PhoneAuthState) {
        binding.buttonPhoneauthSendCode.apply {
            when (state) {
                PhoneAuthState.INIT -> setText(R.string.phoneauth_getauthcode)
                PhoneAuthState.SENDING -> setText(R.string.phoneauth_sending_authcode)
                PhoneAuthState.SENT -> setText(R.string.phoneauth_resend)
            }
            isEnabled = state != PhoneAuthState.SENDING
        }
        binding.edittextPhoneauthAuthcode.isEnabled = state == PhoneAuthState.SENT
    }

    private fun handleAuthResult(task: Task<AuthResult>) {
        if (task.isSuccessful) {
            // 토큰 받아서 넘겨주기
            task.result.user?.let { user ->
                user.getIdToken(true).addOnSuccessListener { result ->
                    userRegistrationViewModel.apply {
                        setPhoneAuthToken(result.token ?: "")
                        setCurrentStep(INPUT_BIRTH)
                    }
                }
            }
        }
        userRegistrationViewModel.setNextStepEnabled(true)
    }

    // 인증 번호 보내기
    private fun sendPhoneAuthCode() {
        if (phoneAuthViewModel.phoneNumber.value.isNotEmpty()) {
            val options = phoneAuthViewModel.getAuthBuilder()
                .setPhoneNumber("+82${phoneAuthViewModel.phoneNumber.value}")
                .setTimeout(EXPIRE_TIME, TimeUnit.SECONDS)
                .setActivity(mainActivity)
                .setCallbacks(callbacks)
            phoneAuthViewModel.requestSendPhoneAuth(options)
        }
    }

    // 인증 번호 검사
    private fun signInWithPhoneAuthCredential() {
        // 검사 받는 동안은 버튼을 막아 둔다.
        if (phoneAuthViewModel.authCode.value.isNotEmpty()) {
            userRegistrationViewModel.setNextStepEnabled(false)
            phoneAuthViewModel.getAuthResult().addOnCompleteListener(mainActivity) { task ->
                handleAuthResult(task)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val EXPIRE_TIME = 120L
    }
}
