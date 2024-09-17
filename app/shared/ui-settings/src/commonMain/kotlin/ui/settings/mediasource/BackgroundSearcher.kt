/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package me.him188.ani.app.ui.settings.mediasource

import androidx.annotation.UiThread
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.settings.mediasource.BackgroundSearcher.RestartSearchScope
import kotlin.time.Duration.Companion.seconds

/**
 * 使用测试数据 [TestData] 运行测试 [TestResult].
 * @see DefaultBackgroundSearcher
 *
 * @param TestData 测试数据, 将所有[搜索][restartSearchImpl]时需要的数据都封装在这个类中, 以便 debounce
 */
@Stable
abstract class BackgroundSearcher<TestData, TestResult>(
    backgroundScope: CoroutineScope,
) {
    /**
     * 当前的测试数据. 测试数据的变工
     */
    protected abstract val testDataState: State<TestData>

    var searchResult: TestResult? by mutableStateOf(null)

    private val searchTasker = MonoTasker(backgroundScope)

    /**
     * 是否有查询正在后台进行中. 也差不多就是 [restartSearchImpl] 是否正在执行.
     */
    val isSearching by searchTasker::isRunning

    interface RestartSearchScope<TestResult> {
        /**
         * 一个没有作用的接口, 用来确保让 [restartSearchScopeImpl] 的实现调用 [launchRequestInBackground]
         */
        interface OK

        /**
         * 在 [searchTasker] 启动协程, 执行 [request].
         * @param request 保证在后台线程执行
         */
        fun launchRequestInBackground(request: suspend () -> TestResult): OK
    }

    /**
     * 在 UI 调用, 当测试数据变化时重新搜索
     */
    suspend fun observeChangeLoop() {
        withContext(Dispatchers.Main.immediate) {
            while (true) {
                snapshotFlow { testDataState.value }
                    .distinctUntilChanged()
                    .debounce(0.5.seconds)
                    .collect {
                        restartSearch(it)
                    }
            }
        }
    }

    private val restartSearchScopeImpl = object : RestartSearchScope<TestResult>, RestartSearchScope.OK {
        override fun launchRequestInBackground(request: suspend () -> TestResult): RestartSearchScope.OK {
            searchTasker.launch {
                // background scope
                val res = request()
                withContext(Dispatchers.Main) {
                    searchResult = res
                }
            }
            return this
        }
    }

    /**
     * 清空
     */
    @UiThread
    protected fun restartSearch(testData: TestData) {
        searchResult = null // ui scope
        restartSearchScopeImpl.restartSearchImpl(testData)
    }

    /**
     * 当执行搜索时调用.
     *
     * Sample implementation:
     * ```
     * val query = RssSearchQuery(
     *     subjectName = testData.keyword,
     *     episodeSort = EpisodeSort(sort),
     * )
     * // 因为这是在 UI 线程内, 可以更新 UI 状态清除正在查看详情的 item
     * // 但注意不要读取涉及搜索参数的状态 - 这会跳过 debounce. 需要将所有参数都封装为 TestData 才能 debounce.
     * viewingItem = null
     * return launchRequestInBackground {
     *     // 后台线程内执行搜索
     *     doSearch(testData, query)
     * }
     * ```
     */
    @UiThread
    protected abstract fun RestartSearchScope<TestResult>.restartSearchImpl(testData: TestData): RestartSearchScope.OK

    fun restartCurrentSearch() {
        restartSearch(testDataState.value)
    }
}

/**
 * @see testDataState 当前的测试数据
 * @see search 当测试数据变化时重新搜索调用
 */
fun <TestData, TestResult> BackgroundSearcher(
    backgroundScope: CoroutineScope,
    testDataState: State<TestData>,
    @UiThread search: RestartSearchScope<TestResult>.(testData: TestData) -> RestartSearchScope.OK,
) = DefaultBackgroundSearcher(backgroundScope, testDataState, search)

@Stable
class DefaultBackgroundSearcher<TestData, TestResult>(
    backgroundScope: CoroutineScope,
    override val testDataState: State<TestData>,
    val restartSearchImpl: RestartSearchScope<TestResult>.(testData: TestData) -> RestartSearchScope.OK,
) : BackgroundSearcher<TestData, TestResult>(backgroundScope) {
    override fun RestartSearchScope<TestResult>.restartSearchImpl(testData: TestData): RestartSearchScope.OK =
        restartSearchImpl.invoke(this, testData)
}