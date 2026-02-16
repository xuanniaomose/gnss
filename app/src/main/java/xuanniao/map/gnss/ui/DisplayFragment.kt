package xuanniao.map.gnss.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import xuanniao.map.gnss.databinding.FragmentDisplayBinding
import xuanniao.map.gnss.GnssViewModel
import java.util.Locale

class DisplayFragment: Fragment() {
    private var _binding: FragmentDisplayBinding? = null
    private val binding get() = _binding!! // 非空断言，确保视图创建后使用
    private lateinit var gnssViewModel: GnssViewModel
    companion object {
        fun newInstance(displayId: String): DisplayFragment {
            val fragment = DisplayFragment()
            val bundle = Bundle()
            bundle.putString("display_id", displayId)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentDisplayBinding.inflate(
            inflater, container, false)
        return binding.root
    }

    // 在onViewCreated中初始化Handler（View已创建，可安全关联控件）
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 初始化ViewModel
        gnssViewModel = ViewModelProvider(requireActivity())[GnssViewModel::class.java]
        // 订阅数据（自动在主线程接收，生命周期安全）
        gnssViewModel.location.observe(viewLifecycleOwner) {location ->
            binding.tvDisplay.text = String.format(
                Locale.CHINA, "%.1f",location.speed)
        }
    }

}