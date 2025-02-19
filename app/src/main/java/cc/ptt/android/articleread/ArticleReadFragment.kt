package cc.ptt.android.articleread

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import cc.ptt.android.Navigation
import cc.ptt.android.R
import cc.ptt.android.base.BaseFragment
import cc.ptt.android.common.CustomLinearLayoutManager
import cc.ptt.android.common.KeyboardUtils
import cc.ptt.android.common.ResourcesUtils
import cc.ptt.android.common.extension.bundleDelegate
import cc.ptt.android.data.model.remote.board.article.Article
import cc.ptt.android.databinding.ArticleReadFragmentLayoutBinding
import cc.ptt.android.domain.model.ui.article.PostRankMark
import cc.ptt.android.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ArticleReadFragment : BaseFragment() {
    private var _binding: ArticleReadFragmentLayoutBinding? = null
    private val binding get() = _binding!!
    private var adapter: ArticleReadAdapter? = null

    private val article by bundleDelegate<Article>()
    private val boardName by bundleDelegate<String>()

    private var progressDialog: ProgressDialog? = null

    private val viewModel: ArticleReadViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ArticleReadFragmentLayoutBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    private fun showEditMode(isEdit: Boolean) {
        with(binding) {
            if (isEdit) {
                articleReadItemLinearlayoutOrgLeft.visibility = View.GONE
                articleReadItemLinearlayoutOrgRight.visibility = View.GONE
                articleReadItemLinearlayoutReplyLeft.visibility = View.VISIBLE
                articleReadItemLinearlayoutReplyRight.visibility = View.VISIBLE
                articleReadItemEditTextReply.isSingleLine = false
                articleReadItemEditTextReply.maxLines = 5
            } else {
                articleReadItemLinearlayoutOrgLeft.visibility = View.VISIBLE
                articleReadItemLinearlayoutOrgRight.visibility = View.VISIBLE
                articleReadItemLinearlayoutReplyLeft.visibility = View.GONE
                articleReadItemLinearlayoutReplyRight.visibility = View.GONE
                articleReadItemEditTextReply.isSingleLine = true
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            articleReadItemEditTextReply.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    showEditMode(true)
                }
            }
            articleReadItemImageButtonHideReply.setOnClickListener {
                articleReadItemEditTextReply.clearFocus()
                showEditMode(false)
            }
            articleReadItemImageButtonLike.setOnClickListener { setRankMenu(it) }
            articleReadItemImageButtonShare.setOnClickListener {
                shareTo(
                    requireContext(),
                    viewModel.originalTitle(article.classX, article.title),
                    """
                                ${viewModel.originalTitle(article.classX, article.title)}
                                ${article.url}
                    """.trimIndent(),
                    "分享文章"
                )
            }
            articleReadItemImageButtonReplySend.setOnClickListener {
                viewModel.createComment(article, articleReadItemEditTextReply.text.toString(), null)
            }
            articleReadFragmentRecyclerView.apply {
                this@ArticleReadFragment.adapter = ArticleReadAdapter(viewModel.data)
                setHasFixedSize(true)
                val layoutManager = CustomLinearLayoutManager(context)
                layoutManager.orientation = RecyclerView.VERTICAL
                setLayoutManager(layoutManager)
                adapter = this@ArticleReadFragment.adapter
            }
            articleReadFragmentRefreshLayout.apply {
                setColorSchemeResources(
                    android.R.color.holo_red_light,
                    android.R.color.holo_blue_light,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light
                )
                setOnRefreshListener {
                    viewModel.loadData(article)
                }
            }
        }
        viewModel.apply {
            observeNotNull(loadingState) {
                binding.articleReadFragmentRefreshLayout.isRefreshing = it
                binding.articleReadFragmentRecyclerView.adapter?.notifyDataSetChanged()
            }
            observeNotNull(errorMessage) {
                Toast.makeText(requireContext(), "Error : $it", Toast.LENGTH_SHORT).show()
            }
            observeNotNull(progressDialogState) {
                progressDialog?.dismiss()
            }
            observeNotNull(likeNumber) {
                binding.articleReadItemTextViewLike.text = it
            }
        }

        val window = requireActivity().window
        window.statusBarColor = ResourcesUtils.getColor(requireContext(), R.attr.article_header)

        // 取得Bundle
        viewModel.createDefaultHeader(
            article.title, article.owner, article.createTime, article.classX, boardName
        )
        viewModel.putDefaultHeader()

        lifecycleScope.launch {
            viewModel.actionState.collect {
                when (it) {
                    is ArticleReadViewModel.ActionEvent.ChooseCommentType -> chooseCommentType()
                    is ArticleReadViewModel.ActionEvent.CreateCommentSuccess -> {
                        showEditMode(false)
                        KeyboardUtils.hideSoftInput(requireActivity())
                        binding.articleReadItemEditTextReply.text.clear()
                        viewModel.loadData(article)
                    }
                }
            }
        }
    }

    override fun onAnimFinished() {
        viewModel.loadData(article)
    }

    private fun chooseCommentType() = lifecycleScope.launch(Dispatchers.Main) {
        if (!viewModel.isLogin()) {
            Navigation.switchToLoginPage(requireActivity())
            return@launch
        }
        val popupMenu = PopupMenu(requireContext(), binding.articleReadItemImageButtonReplySend)
        popupMenu.menuInflater.inflate(R.menu.carete_article_comment_type_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            val type = when (item.itemId) {
                R.id.create_article_comment_type_push -> cc.ptt.android.data.model.remote.article.ArticleCommentType.PUSH
                R.id.create_article_comment_type_hush -> cc.ptt.android.data.model.remote.article.ArticleCommentType.HUSH
                else -> cc.ptt.android.data.model.remote.article.ArticleCommentType.COMMENT
            }
            progressDialog = ProgressDialog.show(
                requireContext(),
                "",
                "Please wait."
            ).apply {
                window?.setBackgroundDrawableResource(R.drawable.dialog_background)
                viewModel.createComment(article, binding.articleReadItemEditTextReply.text.toString(), type)
            }
            true
        }
        popupMenu.show()
    }

    private fun setRankMenu(view: View) {
        if (!viewModel.isLogin()) {
            Navigation.switchToLoginPage(requireActivity())
            return
        }
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.post_article_rank_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            val rank = when (item.itemId) {
                R.id.post_article_rank_like -> PostRankMark.Like
                R.id.post_article_rank_dislike -> PostRankMark.Dislike
                R.id.post_article_rank_non -> PostRankMark.None
                else -> PostRankMark.None
            }
            progressDialog = ProgressDialog.show(
                requireContext(),
                "",
                "Please wait."
            ).apply {
                window?.setBackgroundDrawableResource(R.drawable.dialog_background)
                viewModel.setRank(article, rank)
            }
            true
        }
        popupMenu.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        try {
            val inputMethodManager = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.data.clear()
        val typedValue = TypedValue()
        val theme = requireActivity().theme
        theme.resolveAttribute(R.attr.black, typedValue, true)
        @ColorInt val color = typedValue.data
        val window = requireActivity().window
        window.statusBarColor = color
    }

    companion object {
        const val KEY_ARTICLE = "article"
        const val KEY_BOARD_NAME = "boardName"
    }
}
