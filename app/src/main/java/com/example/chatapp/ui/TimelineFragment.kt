package com.example.chatapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.TimelineAdapter
import com.example.chatapp.TimelineViewModel
import com.example.chatapp.TimelineViewModelFactory

class TimelineFragment : Fragment() {

    private lateinit var viewModel: TimelineViewModel
    private lateinit var adapter: TimelineAdapter
    private lateinit var recyclerView: RecyclerView
    private var emptyState: View? = null
    private var errorState: View? = null
    private var progressLoading: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_timeline, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val token = PrefsHelper.getAuthToken(requireContext())
        val username = PrefsHelper.getUsername(requireContext())
        val conversationId = PrefsHelper.getConversationId(requireContext())

        recyclerView = view.findViewById(R.id.timelineRecyclerView)
        emptyState = view.findViewById(R.id.emptyStateTimeline)
        errorState = view.findViewById(R.id.errorStateTimeline)
        progressLoading = view.findViewById(R.id.progressLoadingTimeline)

        val factory = TimelineViewModelFactory(token, conversationId)
        viewModel = ViewModelProvider(this, factory)[TimelineViewModel::class.java]

        setupRecyclerView()
        observeTimeline(username)

        view.findViewById<View>(R.id.btnRetryTimeline).setOnClickListener {
            showLoadingState()
            viewModel.loadTimeline()
        }
        showLoadingState()
        viewModel.loadTimeline()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observeTimeline(username: String) {
        viewModel.items.observe(viewLifecycleOwner) { items ->
            if (items.isNullOrEmpty()) {
                recyclerView.visibility = View.GONE
                progressLoading?.visibility = View.GONE
                errorState?.visibility = View.GONE
                emptyState?.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                progressLoading?.visibility = View.GONE
                errorState?.visibility = View.GONE
                emptyState?.visibility = View.GONE
                adapter = TimelineAdapter(items, currentUsername = username)
                recyclerView.adapter = adapter
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                showErrorState()
            }
        }
    }

    private fun showLoadingState() {
        progressLoading?.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState?.visibility = View.GONE
        errorState?.visibility = View.GONE
    }

    private fun showErrorState() {
        progressLoading?.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyState?.visibility = View.GONE
        errorState?.visibility = View.VISIBLE
    }
}
