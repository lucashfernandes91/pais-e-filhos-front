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
import com.example.chatapp.TimelineItem
import com.example.chatapp.TimelineViewModel
import com.example.chatapp.TimelineViewModelFactory

class TimelineFragment : Fragment() {

    private lateinit var viewModel: TimelineViewModel
    private lateinit var adapter: TimelineAdapter
    private lateinit var recyclerView: RecyclerView
    private var emptyState: View? = null
    private var errorState: View? = null
    private var progressLoading: View? = null
    private var eventCount: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_timeline, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val conversationId = PrefsHelper.getConversationId(requireContext())

        recyclerView = view.findViewById(R.id.timelineRecyclerView)
        emptyState = view.findViewById(R.id.emptyStateTimeline)
        errorState = view.findViewById(R.id.errorStateTimeline)
        progressLoading = view.findViewById(R.id.progressLoadingTimeline)

        val factory = TimelineViewModelFactory(requireContext(), conversationId)
        viewModel = ViewModelProvider(this, factory)[TimelineViewModel::class.java]

        setupRecyclerView()
        observeTimeline()

        view.findViewById<View>(R.id.btnRetryTimeline).setOnClickListener {
            showLoadingState()
            viewModel.loadTimeline()
        }
        view.findViewById<View>(R.id.btnExportTimeline).setOnClickListener {
            openExportSheet()
        }
        showLoadingState()
        viewModel.loadTimeline()
    }

    private fun openExportSheet() {
        val sheet = ExportBottomSheet.newInstance(eventCount, ExportBottomSheet.TYPE_EVENTS)
        sheet.show(childFragmentManager, "ExportBottomSheet")
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = TimelineAdapter()
        recyclerView.adapter = adapter

        // Lista é decrescente (recentes no topo): chegar perto do fim
        // carrega a página anterior de mensagens.
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_OLDER_THRESHOLD) {
                    viewModel.loadOlderMessages()
                }
            }
        })
    }

    companion object {
        // Posições antes do fim que disparam o carregamento da página anterior.
        private const val LOAD_OLDER_THRESHOLD = 5
    }

    private fun observeTimeline() {
        viewModel.items.observe(viewLifecycleOwner) { items ->
            eventCount = items?.count { it is TimelineItem.EventItem } ?: 0

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
                adapter.updateItems(items)
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
