# Home And Topic Pagination Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement phase-1 pagination and pull-to-refresh for the Android home feed and topic detail screens.

**Architecture:** Keep the existing `Repository + ViewModel + StateFlow<UiState>` flow and extend it instead of introducing Paging 3 or a new data layer. Parse pagination metadata in `core/network`, expose feed pages and topic stream ids to feature repositories, and let the two screen ViewModels own refresh, append, retry, and de-duplication state.

**Tech Stack:** Kotlin, Jetpack Compose Material3 `pulltorefresh`, StateFlow, Retrofit, OkHttp, Kotlinx Serialization, JUnit4, Turbine, MockWebServer

---

**Scope note:** This plan covers only phase 1 from [2026-04-02-home-topic-pagination-refresh-design.md](/Users/baba/Documents/workspace_baba/linuxdo-native/docs/superpowers/specs/2026-04-02-home-topic-pagination-refresh-design.md). MessageBus-based live update prompts stay out of scope.

**Current user instruction:** Do not create commits while executing this plan.

## File Structure Map

### Contracts And Fixtures

- Modify: `contracts/discourse/endpoints.md` — add `GET /t/{id}/posts.json?post_ids[]=...` and clarify `more_topics_url` pagination metadata.
- Modify: `contracts/discourse/models.md` — document that `TopicDetail` now includes total post count and stream ids for incremental topic loading.
- Modify: `fixtures/discourse/latest-topics.json` — add `more_topics_url`.
- Modify: `fixtures/discourse/topic-detail.json` — add `posts_count` and `post_stream.stream`.
- Create: `fixtures/discourse/topic-posts.json` — sample incremental topic-post chunk response.

### Core Network And Models

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicListPage.kt` — shared feed-page result returned by `DiscourseApiClient`.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicDetail.kt` — add `totalPosts` and `streamPostIds`.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt` — parse `more_topics_url`, `posts_count`, `stream`, and incremental `posts.json`.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt` — expose page-aware feed fetchers and topic-post chunk fetcher.
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt` — cover page metadata and topic post chunks.

### Home Feature

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeFeedPage.kt` — home-only pagination state returned by repository/use case.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeRepository.kt` — map `TopicListPage` into `HomeFeedPage`.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/domain/LoadHomeFeedUseCase.kt` — accept `page`.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt` — add append state.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModel.kt` — own refresh, append, de-duplication, and retry state.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt` — add pull-to-refresh, bottom loading/retry item, and near-end append trigger.
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModelTest.kt` — add pagination/refresh tests.
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeRouteSearchChromeTest.kt` — add load-more trigger helper tests.

### Topic Feature

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/data/TopicRepository.kt` — load initial topic and incremental post chunks.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/domain/LoadTopicUseCase.kt` — expose initial load and chunk load.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicUiState.kt` — add total count, refresh state, append state.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModel.kt` — own refresh, append, remaining post ids, and retry state.
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt` — add pull-to-refresh, bottom loading/retry item, near-end append trigger, and total post count label.
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModelTest.kt` — add refresh and chunk-loading tests.
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoutePaginationTest.kt` — test route helper functions for load-more threshold and count label.

---

### Task 1: Expand Contracts And Network Pagination Models

**Files:**
- Modify: `contracts/discourse/endpoints.md`
- Modify: `contracts/discourse/models.md`
- Modify: `fixtures/discourse/latest-topics.json`
- Modify: `fixtures/discourse/topic-detail.json`
- Create: `fixtures/discourse/topic-posts.json`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicListPage.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicDetail.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt`
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt`

- [ ] **Step 1: Write the failing network tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt
@Test
fun fetchLatestPage_mapsMoreTopicsUrl() = runTest {
    server.enqueue(
        MockResponse()
            .setBody(
                """
                {
                  "topic_list": {
                    "more_topics_url": "/latest.json?page=1",
                    "topics": [
                      {
                        "id": 101,
                        "title": "Welcome",
                        "posters": [
                          {
                            "user": {
                              "username": "baba"
                            }
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
            .setHeader("Content-Type", "application/json"),
    )

    val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
    val page = client.fetchLatestPage(page = 0)

    assertThat(page.topics).hasSize(1)
    assertThat(page.moreTopicsUrl).isEqualTo("/latest.json?page=1")
}

@Test
fun fetchTopic_mapsTotalPostsAndStreamIds() = runTest {
    val payload = checkNotNull(
        javaClass.classLoader?.getResource("discourse/topic-detail.json"),
    ).readText()
    server.enqueue(
        MockResponse()
            .setBody(payload)
            .setHeader("Content-Type", "application/json"),
    )

    val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
    val topic = client.fetchTopic(topicId = 101)

    assertThat(topic.totalPosts).isEqualTo(3)
    assertThat(topic.streamPostIds).containsExactly(1001L, 1002L, 1003L).inOrder()
}

@Test
fun fetchTopicPosts_mapsChunkPosts() = runTest {
    val payload = checkNotNull(
        javaClass.classLoader?.getResource("discourse/topic-posts.json"),
    ).readText()
    server.enqueue(
        MockResponse()
            .setBody(payload)
            .setHeader("Content-Type", "application/json"),
    )

    val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
    val posts = client.fetchTopicPosts(topicId = 101, postIds = listOf(1002L, 1003L))

    assertThat(posts).hasSize(2)
    assertThat(posts.map { it.id }).containsExactly(1002L, 1003L).inOrder()
}
```

- [ ] **Step 2: Run the network tests to verify they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.network.DiscourseApiClientTest"
```

Expected:

```text
FAIL
- unresolved reference: TopicListPage
- unresolved reference: fetchLatestPage
- unresolved reference: totalPosts
- unresolved reference: streamPostIds
- unresolved reference: fetchTopicPosts
```

- [ ] **Step 3: Implement the contracts, fixtures, DTOs, and client methods**

```markdown
# contracts/discourse/endpoints.md
- `GET /latest.json?page={page}` - latest topic feed with optional `topic_list.more_topics_url`
- `GET /new.json?page={page}` - new topic feed with optional `topic_list.more_topics_url`
- `GET /top.json?page={page}` - top topic feed with optional `topic_list.more_topics_url`
- `GET /t/{id}.json` - topic detail with `posts_count` and `post_stream.stream`
- `GET /t/{id}/posts.json?post_ids[]={id}` - incremental topic post chunk fetch
```

```markdown
# contracts/discourse/models.md
- `TopicListPage` - topic summaries plus `moreTopicsUrl` for feed pagination
- `TopicDetail` - topic header, ordered `Post` items, total post count, and full stream post ids
```

```json
// fixtures/discourse/latest-topics.json
{
  "topic_list": {
    "more_topics_url": "/latest.json?page=1",
    "topics": [
      {
        "id": 101,
        "title": "Welcome to NativeDo Android",
        "slug": "welcome-to-nativedo-android",
        "reply_count": 11,
        "views": 345,
        "last_posted_at": "2026-03-31T09:00:00.000Z",
        "category_id": 6,
        "posters": [
          {
            "user": {
              "username": "baba"
            }
          }
        ]
      }
    ]
  }
}
```

```json
// fixtures/discourse/topic-detail.json
{
  "id": 101,
  "title": "Welcome to NativeDo Android",
  "posts_count": 3,
  "post_stream": {
    "stream": [1001, 1002, 1003],
    "posts": [
      {
        "id": 1001,
        "username": "baba",
        "cooked": "<p>Hello from Android.</p>"
      }
    ]
  }
}
```

```json
// fixtures/discourse/topic-posts.json
{
  "post_stream": {
    "posts": [
      {
        "id": 1002,
        "username": "alice",
        "cooked": "<p>Second reply</p>"
      },
      {
        "id": 1003,
        "username": "charlie",
        "cooked": "<p>Third reply</p>"
      }
    ]
  }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicListPage.kt
package io.github.baba983.nativedo.core.model

data class TopicListPage(
    val topics: List<TopicSummary>,
    val moreTopicsUrl: String?,
)
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicDetail.kt
package io.github.baba983.nativedo.core.model

data class TopicDetail(
    val id: Long,
    val title: String,
    val posts: List<Post>,
    val totalPosts: Int,
    val streamPostIds: List<Long>,
)
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt
@GET("t/{id}/posts.json")
suspend fun topicPosts(
    @Path("id") id: Long,
    @Query("post_ids[]") postIds: List<Long>,
): TopicPostsResponseDto

@Serializable
data class TopicListDto(
    val topics: List<TopicDto>,
    @SerialName("more_topics_url")
    val moreTopicsUrl: String? = null,
)

@Serializable
data class TopicResponseDto(
    val id: Long,
    val title: String,
    @SerialName("posts_count")
    val postsCount: Int = 0,
    @SerialName("post_stream")
    val postStream: PostStreamDto,
)

@Serializable
data class TopicPostsResponseDto(
    @SerialName("post_stream")
    val postStream: PostStreamDto,
)

@Serializable
data class PostStreamDto(
    val posts: List<PostDto>,
    val stream: List<Long> = emptyList(),
)

internal fun TopicListResponseDto.toModel(): TopicListPage = TopicListPage(
    topics = topicList.topics.map { it.toModel() },
    moreTopicsUrl = topicList.moreTopicsUrl,
)

internal fun TopicResponseDto.toModel(): TopicDetail = TopicDetail(
    id = id,
    title = title,
    posts = postStream.posts.map { it.toModel() },
    totalPosts = if (postsCount > 0) postsCount else postStream.stream.size,
    streamPostIds = postStream.stream,
)
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt
suspend fun fetchLatestPage(page: Int): TopicListPage =
    service.latest(page).toModel()

suspend fun fetchNewPage(page: Int): TopicListPage =
    service.newTopics(page).toModel()

suspend fun fetchTopPage(page: Int): TopicListPage =
    service.topTopics(page).toModel()

suspend fun fetchTopicPosts(topicId: Long, postIds: List<Long>): List<Post> =
    service.topicPosts(topicId, postIds).postStream.posts.map { it.toModel() }
```

- [ ] **Step 4: Run the network tests to verify they pass**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.network.DiscourseApiClientTest"
```

Expected:

```text
BUILD SUCCESSFUL
DiscourseApiClientTest > fetchLatestPage_mapsMoreTopicsUrl PASSED
DiscourseApiClientTest > fetchTopic_mapsTotalPostsAndStreamIds PASSED
DiscourseApiClientTest > fetchTopicPosts_mapsChunkPosts PASSED
```

---

### Task 2: Add Home Feed Pagination State And Tests

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeFeedPage.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeRepository.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/domain/LoadHomeFeedUseCase.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModel.kt`
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModelTest.kt`

- [ ] **Step 1: Write the failing HomeViewModel pagination tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModelTest.kt
@Test
fun refresh_replacesItemsAndResetsNextPage() = runTest {
    val useCase = FakeLoadHomeFeedUseCase(
        pages = mapOf(
            HomeFeedRequest(HomeFeedTab.Latest, 0) to HomeFeedPage(
                items = listOf(
                    TopicSummary(id = 1, title = "First page", authorUsername = "baba"),
                ),
                nextPage = 1,
                hasMore = true,
            ),
            HomeFeedRequest(HomeFeedTab.Latest, 1) to HomeFeedPage(
                items = listOf(
                    TopicSummary(id = 2, title = "Second page", authorUsername = "alice"),
                ),
                nextPage = null,
                hasMore = false,
            ),
        ),
    )
    val viewModel = HomeViewModel(loadHomeFeed = useCase)

    runCurrent()
    viewModel.loadNextPage()
    runCurrent()
    viewModel.onRefresh()
    runCurrent()

    assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(1L)
    assertThat(viewModel.uiState.value.nextPage).isEqualTo(1)
    assertThat(viewModel.uiState.value.hasMore).isTrue()
}

@Test
fun loadNextPage_appendsUniqueItemsAndStopsAtEnd() = runTest {
    val useCase = FakeLoadHomeFeedUseCase(
        pages = mapOf(
            HomeFeedRequest(HomeFeedTab.Latest, 0) to HomeFeedPage(
                items = listOf(
                    TopicSummary(id = 1, title = "Page 0", authorUsername = "baba"),
                ),
                nextPage = 1,
                hasMore = true,
            ),
            HomeFeedRequest(HomeFeedTab.Latest, 1) to HomeFeedPage(
                items = listOf(
                    TopicSummary(id = 1, title = "Page 0 duplicate", authorUsername = "baba"),
                    TopicSummary(id = 2, title = "Page 1", authorUsername = "alice"),
                ),
                nextPage = null,
                hasMore = false,
            ),
        ),
    )
    val viewModel = HomeViewModel(loadHomeFeed = useCase)

    runCurrent()
    viewModel.loadNextPage()
    runCurrent()
    viewModel.loadNextPage()
    runCurrent()

    assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(1L, 2L).inOrder()
    assertThat(viewModel.uiState.value.hasMore).isFalse()
    assertThat(useCase.requests).containsExactly(
        HomeFeedRequest(HomeFeedTab.Latest, 0),
        HomeFeedRequest(HomeFeedTab.Latest, 1),
    ).inOrder()
}

private data class HomeFeedRequest(
    val tab: HomeFeedTab,
    val page: Int,
)

private class FakeLoadHomeFeedUseCase(
    private val pages: Map<HomeFeedRequest, HomeFeedPage> = emptyMap(),
) : LoadHomeFeedUseCase() {
    val requests = mutableListOf<HomeFeedRequest>()

    override suspend fun invoke(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> {
        val request = HomeFeedRequest(tab, page)
        requests += request
        return Result.success(
            checkNotNull(pages[request]) {
                "Missing fake page for $request"
            },
        )
    }
}
```

- [ ] **Step 2: Run the HomeViewModel tests to verify they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest"
```

Expected:

```text
FAIL
- unresolved reference: HomeFeedPage
- unresolved reference: HomeFeedRequest
- no value passed for parameter: page
- unresolved reference: loadNextPage
- unresolved reference: nextPage
- unresolved reference: hasMore
```

- [ ] **Step 3: Implement page-aware home repository, use case, ui state, and view model**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeFeedPage.kt
package io.github.baba983.nativedo.feature.home.data

import io.github.baba983.nativedo.core.model.TopicSummary

data class HomeFeedPage(
    val items: List<TopicSummary>,
    val nextPage: Int?,
    val hasMore: Boolean,
)
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeRepository.kt
package io.github.baba983.nativedo.feature.home.data

import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab

private val PAGE_QUERY_REGEX = Regex("""[?&]page=(\d+)""")

class HomeRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> = runCatching {
        val result = when (tab) {
            HomeFeedTab.Latest -> client.fetchLatestPage(page)
            HomeFeedTab.New -> client.fetchNewPage(page)
            HomeFeedTab.Top -> client.fetchTopPage(page)
        }
        val parsedNextPage = result.moreTopicsUrl
            ?.let { PAGE_QUERY_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }

        HomeFeedPage(
            items = result.topics,
            nextPage = parsedNextPage,
            hasMore = parsedNextPage != null,
        )
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/domain/LoadHomeFeedUseCase.kt
open class LoadHomeFeedUseCase protected constructor() {
    private lateinit var repository: HomeRepository

    constructor(repository: HomeRepository) : this() {
        this.repository = repository
    }

    open suspend operator fun invoke(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> =
        repository.load(tab, page)
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt
data class HomeUiState(
    val selectedTab: HomeFeedTab = HomeFeedTab.Latest,
    val items: List<TopicSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPage: Int? = null,
    val errorMessage: String? = null,
    val loadMoreErrorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && items.isEmpty() && errorMessage == null
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModel.kt
class HomeViewModel(
    private val loadHomeFeed: LoadHomeFeedUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh(HomeFeedTab.Latest, initial = true)
    }

    fun onFeedSelected(tab: HomeFeedTab) {
        if (tab != _uiState.value.selectedTab) {
            refresh(tab, initial = false)
        }
    }

    fun onRefresh() {
        refresh(_uiState.value.selectedTab, initial = false)
    }

    fun loadNextPage() {
        val state = _uiState.value
        val nextPage = state.nextPage ?: return
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            loadHomeFeed(state.selectedTab, nextPage).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        items = (_uiState.value.items + page.items).distinctBy { it.id },
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                        nextPage = page.nextPage,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = error.message,
                    )
                },
            )
        }
    }

    private fun refresh(tab: HomeFeedTab, initial: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedTab = tab,
                isLoading = initial,
                isRefreshing = !initial,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            loadHomeFeed(tab, 0).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        items = page.items,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = page.hasMore,
                        nextPage = page.nextPage,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message,
                    )
                },
            )
        }
    }
}
```

- [ ] **Step 4: Run the HomeViewModel tests to verify they pass**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest"
```

Expected:

```text
BUILD SUCCESSFUL
HomeViewModelTest > refresh_replacesItemsAndResetsNextPage PASSED
HomeViewModelTest > loadNextPage_appendsUniqueItemsAndStopsAtEnd PASSED
```

---

### Task 3: Wire Home Pull-To-Refresh And Load-More UI

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeRouteSearchChromeTest.kt`

- [ ] **Step 1: Write the failing home route helper tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeRouteSearchChromeTest.kt
@Test
fun loadMore_requestedWhenLastVisibleItemIsInsideThreshold() {
    assertThat(
        shouldLoadMoreHomeFeed(
            lastVisibleIndex = 17,
            totalItemsCount = 20,
            hasMore = true,
            isLoadingMore = false,
        ),
    ).isTrue()
}

@Test
fun loadMore_notRequestedWhenNoMorePagesRemain() {
    assertThat(
        shouldLoadMoreHomeFeed(
            lastVisibleIndex = 17,
            totalItemsCount = 20,
            hasMore = false,
            isLoadingMore = false,
        ),
    ).isFalse()
}
```

- [ ] **Step 2: Run the route helper tests to verify they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.home.ui.HomeRouteSearchChromeTest"
```

Expected:

```text
FAIL
- unresolved reference: shouldLoadMoreHomeFeed
```

- [ ] **Step 3: Implement pull-to-refresh, append trigger, and footer UI**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

private const val HOME_LOAD_MORE_THRESHOLD = 5

HomeFeedContent(
    uiState = uiState,
    listState = listState,
    forceShowSearchField = forceShowSearchField,
    cookieStore = appContainer.cookieStore,
    baseUrl = appContainer.baseUrl,
    onTopicClick = onTopicClick,
    onFeedSelected = viewModel::onFeedSelected,
    onRefresh = viewModel::onRefresh,
    onLoadMore = viewModel::loadNextPage,
    onSearchClick = {
        searchOpenedFromHeader = false
        forceShowSearchField = false
        isSearchMode = true
    },
    onHeaderSearchVisibilityChanged = onHeaderSearchVisibilityChanged,
    modifier = Modifier.fillMaxSize(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedContent(
    uiState: HomeUiState,
    listState: LazyListState,
    forceShowSearchField: Boolean,
    cookieStore: io.github.baba983.nativedo.core.storage.CookieStore,
    baseUrl: String,
    onTopicClick: (Long) -> Unit,
    onFeedSelected: (HomeFeedTab) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSearchClick: () -> Unit,
    onHeaderSearchVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val errorMessage = uiState.errorMessage

    val shouldLoadMore by remember(listState, uiState.hasMore, uiState.isLoadingMore) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            shouldLoadMoreHomeFeed(
                lastVisibleIndex = lastVisibleIndex,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
            )
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = HOME_SEARCH_CONTAINER_HEIGHT + HOME_TABS_CONTAINER_HEIGHT,
                    bottom = 12.dp,
                ),
            ) {
                when {
                    uiState.isLoading -> {
                        item(key = "loading") {
                            HomeFeedbackCard { CircularProgressIndicator() }
                        }
                    }

                    errorMessage != null && uiState.items.isEmpty() -> {
                        item(key = "error") {
                            HomeFeedbackCard {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = errorMessage)
                                    TextButton(onClick = onRefresh) {
                                        Text(text = "Retry")
                                    }
                                }
                            }
                        }
                    }

                    uiState.isEmpty -> {
                        item(key = "empty") {
                            HomeFeedbackCard {
                                Text(text = "No topics yet")
                            }
                        }
                    }

                    else -> {
                        itemsIndexed(
                            items = uiState.items,
                            key = { _, topic -> topic.id },
                        ) { index, topic ->
                            TopicListItem(
                                topic = topic,
                                cookieStore = cookieStore,
                                baseUrl = baseUrl,
                                onClick = { onTopicClick(topic.id) },
                                modifier = Modifier.fillMaxWidth(),
                                showDivider = index != uiState.items.lastIndex,
                            )
                        }
                    }
                }

                if (uiState.isLoadingMore) {
                    item(key = "load-more") {
                        HomeFeedbackCard {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (uiState.loadMoreErrorMessage != null) {
                    item(key = "load-more-error") {
                        HomeFeedbackCard {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = uiState.loadMoreErrorMessage)
                                TextButton(onClick = onLoadMore) {
                                    Text(text = "Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldLoadMoreHomeFeed(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    threshold: Int = HOME_LOAD_MORE_THRESHOLD,
): Boolean {
    if (!hasMore || isLoadingMore || totalItemsCount == 0) return false
    return lastVisibleIndex >= totalItemsCount - threshold
}
```

- [ ] **Step 4: Run the home route tests and compile the app**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.home.ui.HomeRouteSearchChromeTest"
./apps/android/gradlew -p apps/android :app:compileDebugKotlin
```

Expected:

```text
BUILD SUCCESSFUL
HomeRouteSearchChromeTest > loadMore_requestedWhenLastVisibleItemIsInsideThreshold PASSED
HomeRouteSearchChromeTest > loadMore_notRequestedWhenNoMorePagesRemain PASSED
compileDebugKotlin completed successfully
```

---

### Task 4: Add Topic Incremental Loading State And Tests

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/data/TopicRepository.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/domain/LoadTopicUseCase.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicUiState.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModel.kt`
- Modify: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModelTest.kt`

- [ ] **Step 1: Write the failing topic pagination tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModelTest.kt
@Test
fun refresh_resetsLoadedPostsAndRemainingStream() = runTest {
    val useCase = FakeLoadTopicUseCase(
        initial = TopicDetail(
            id = 42,
            title = "Compose on linux.do",
            posts = listOf(
                Post(id = 1, authorUsername = "baba", cookedHtml = "<p>Hello</p>", imageUrls = emptyList()),
            ),
            totalPosts = 3,
            streamPostIds = listOf(1L, 2L, 3L),
        ),
        chunks = mapOf(
            listOf(2L, 3L) to listOf(
                Post(id = 2, authorUsername = "alice", cookedHtml = "<p>Second</p>", imageUrls = emptyList()),
                Post(id = 3, authorUsername = "charlie", cookedHtml = "<p>Third</p>", imageUrls = emptyList()),
            ),
        ),
    )
    val viewModel = TopicViewModel(loadTopic = useCase, topicId = 42)

    runCurrent()
    viewModel.loadMore()
    runCurrent()
    viewModel.refresh()
    runCurrent()

    assertThat(viewModel.uiState.value.posts.map { it.id }).containsExactly(1L)
    assertThat(viewModel.uiState.value.hasMore).isTrue()
}

@Test
fun loadMore_appendsChunkAndStopsWhenStreamExhausted() = runTest {
    val useCase = FakeLoadTopicUseCase(
        initial = TopicDetail(
            id = 42,
            title = "Compose on linux.do",
            posts = listOf(
                Post(id = 1, authorUsername = "baba", cookedHtml = "<p>Hello</p>", imageUrls = emptyList()),
            ),
            totalPosts = 3,
            streamPostIds = listOf(1L, 2L, 3L),
        ),
        chunks = mapOf(
            listOf(2L, 3L) to listOf(
                Post(id = 2, authorUsername = "alice", cookedHtml = "<p>Second</p>", imageUrls = emptyList()),
                Post(id = 3, authorUsername = "charlie", cookedHtml = "<p>Third</p>", imageUrls = emptyList()),
            ),
        ),
    )
    val viewModel = TopicViewModel(loadTopic = useCase, topicId = 42)

    runCurrent()
    viewModel.loadMore()
    runCurrent()
    viewModel.loadMore()
    runCurrent()

    assertThat(viewModel.uiState.value.posts.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
    assertThat(viewModel.uiState.value.hasMore).isFalse()
    assertThat(useCase.chunkRequests).containsExactly(listOf(2L, 3L))
}

private class FakeLoadTopicUseCase(
    private val initial: TopicDetail,
    private val chunks: Map<List<Long>, List<Post>> = emptyMap(),
) : LoadTopicUseCase() {
    val chunkRequests = mutableListOf<List<Long>>()

    override suspend fun invoke(topicId: Long): Result<TopicDetail> {
        return Result.success(initial)
    }

    override suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> {
        chunkRequests += postIds
        return Result.success(
            checkNotNull(chunks[postIds]) {
                "Missing fake chunk for $postIds"
            },
        )
    }
}
```

- [ ] **Step 2: Run the topic view model tests to verify they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.topic.ui.TopicViewModelTest"
```

Expected:

```text
FAIL
- unresolved reference: totalPosts
- unresolved reference: streamPostIds
- unresolved reference: loadMore
- unresolved reference: hasMore
```

- [ ] **Step 3: Implement topic repository, use case, ui state, and view model**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/data/TopicRepository.kt
package io.github.baba983.nativedo.feature.topic.data

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.network.DiscourseApiClient

class TopicRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(topicId: Long): Result<TopicDetail> = runCatching {
        client.fetchTopic(topicId = topicId)
    }

    suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> = runCatching {
        client.fetchTopicPosts(topicId = topicId, postIds = postIds)
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/domain/LoadTopicUseCase.kt
open class LoadTopicUseCase protected constructor() {
    private lateinit var repository: TopicRepository

    constructor(repository: TopicRepository) : this() {
        this.repository = repository
    }

    open suspend operator fun invoke(topicId: Long): Result<TopicDetail> =
        repository.load(topicId)

    open suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> =
        repository.loadPosts(topicId, postIds)
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicUiState.kt
data class TopicUiState(
    val title: String = "",
    val totalPosts: Int = 0,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreErrorMessage: String? = null,
) {
    val canRetry: Boolean
        get() = errorMessage != null && posts.isEmpty()
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModel.kt
private const val TOPIC_POST_PAGE_SIZE = 20

class TopicViewModel(
    private val loadTopic: LoadTopicUseCase,
    private val topicId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var remainingPostIds: List<Long> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasExistingPosts = _uiState.value.posts.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasExistingPosts,
                isRefreshing = hasExistingPosts,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            loadTopic(topicId).fold(
                onSuccess = { detail ->
                    val loadedIds = detail.posts.map { it.id }.toSet()
                    remainingPostIds = detail.streamPostIds.filterNot(loadedIds::contains)
                    _uiState.value = TopicUiState(
                        title = detail.title,
                        totalPosts = detail.totalPosts,
                        posts = detail.posts,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = remainingPostIds.isNotEmpty(),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message,
                    )
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || remainingPostIds.isEmpty()) return

        val nextChunk = remainingPostIds.take(TOPIC_POST_PAGE_SIZE)
        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            loadTopic.loadPosts(topicId, nextChunk).fold(
                onSuccess = { posts ->
                    remainingPostIds = remainingPostIds.drop(nextChunk.size)
                    _uiState.value = _uiState.value.copy(
                        posts = (_uiState.value.posts + posts).distinctBy { it.id },
                        isLoadingMore = false,
                        hasMore = remainingPostIds.isNotEmpty(),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = error.message,
                    )
                },
            )
        }
    }
}
```

- [ ] **Step 4: Run the topic view model tests to verify they pass**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.topic.ui.TopicViewModelTest"
```

Expected:

```text
BUILD SUCCESSFUL
TopicViewModelTest > refresh_resetsLoadedPostsAndRemainingStream PASSED
TopicViewModelTest > loadMore_appendsChunkAndStopsWhenStreamExhausted PASSED
```

---

### Task 5: Wire Topic Pull-To-Refresh, Load-More UI, And Final Verification

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoutePaginationTest.kt`

- [ ] **Step 1: Write the failing topic route helper tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoutePaginationTest.kt
package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicRoutePaginationTest {

    @Test
    fun loadMore_requestedWhenLastVisibleItemIsInsideThreshold() {
        assertThat(
            shouldLoadMoreTopicPosts(
                lastVisibleIndex = 15,
                totalItemsCount = 18,
                hasMore = true,
                isLoadingMore = false,
            ),
        ).isTrue()
    }

    @Test
    fun postCountLabel_prefersServerTotalCount() {
        assertThat(topicPostCountLabel(totalPosts = 12, loadedPosts = 3))
            .isEqualTo("12 posts")
    }
}
```

- [ ] **Step 2: Run the topic route helper tests to verify they fail**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.topic.ui.TopicRoutePaginationTest"
```

Expected:

```text
FAIL
- unresolved reference: shouldLoadMoreTopicPosts
- unresolved reference: topicPostCountLabel
```

- [ ] **Step 3: Implement topic pull-to-refresh, footer states, and append trigger**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

private const val TOPIC_LOAD_MORE_THRESHOLD = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicRoute(
    topicId: Long,
    onTopicClick: (Long) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    val shouldLoadMore by remember(listState, uiState.hasMore, uiState.isLoadingMore) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            shouldLoadMoreTopicPosts(
                lastVisibleIndex = lastVisibleIndex,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
            )
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    when {
        uiState.isLoading -> {
            TopicFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.canRetry -> {
            TopicFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.errorMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::refresh) {
                        Text(text = "Retry")
                    }
                }
            }
        }

        else -> PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item {
                    Text(
                        text = topicPostCountLabel(
                            totalPosts = uiState.totalPosts,
                            loadedPosts = uiState.posts.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                itemsIndexed(
                    items = uiState.posts,
                    key = { _, post -> post.id },
                ) { index, post ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                label = post.authorUsername,
                                avatarTemplate = post.authorAvatarTemplate,
                                cookieStore = appContainer.cookieStore,
                                baseUrl = appContainer.baseUrl,
                                size = 32.dp,
                            )
                            Text(
                                text = "@${post.authorUsername}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HtmlPostBodyView(
                            html = post.cookedHtml,
                            cookieStore = appContainer.cookieStore,
                            baseUrl = appContainer.baseUrl,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (index != uiState.posts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                if (uiState.isLoadingMore) {
                    item(key = "load-more") {
                        TopicFeedbackCard { CircularProgressIndicator() }
                    }
                }

                if (uiState.loadMoreErrorMessage != null) {
                    item(key = "load-more-error") {
                        TopicFeedbackCard {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = uiState.loadMoreErrorMessage)
                                TextButton(onClick = viewModel::loadMore) {
                                    Text(text = "Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldLoadMoreTopicPosts(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    threshold: Int = TOPIC_LOAD_MORE_THRESHOLD,
): Boolean {
    if (!hasMore || isLoadingMore || totalItemsCount == 0) return false
    return lastVisibleIndex >= totalItemsCount - threshold
}

internal fun topicPostCountLabel(totalPosts: Int, loadedPosts: Int): String {
    val count = if (totalPosts > 0) totalPosts else loadedPosts
    return "$count posts"
}
```

- [ ] **Step 4: Run final verification for topic UI and the full phase-1 change set**

Run:

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.topic.ui.TopicRoutePaginationTest"
./apps/android/gradlew -p apps/android :app:testDebugUnitTest
./apps/android/gradlew -p apps/android :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
TopicRoutePaginationTest > loadMore_requestedWhenLastVisibleItemIsInsideThreshold PASSED
TopicRoutePaginationTest > postCountLabel_prefersServerTotalCount PASSED
All unit tests passed
assembleDebug completed successfully
```

---

## Self-Review

### Spec Coverage

- Home page-number pagination, refresh, append state, and retry behavior are covered by Tasks 2 and 3.
- Topic stream-id pagination, refresh, append state, and retry behavior are covered by Tasks 1, 4, and 5.
- Contract and fixture updates required by the spec are covered by Task 1.
- MessageBus phase 2 is intentionally excluded from this implementation plan and remains future work.

### Placeholder Scan

- No `TODO`, `TBD`, or “similar to previous task” placeholders remain.
- Every task lists exact files, concrete code snippets, commands, and expected outputs.
- Commit steps are intentionally omitted because the current user instruction says not to create commits while executing this plan.

### Type Consistency

- `TopicDetail.totalPosts` and `TopicDetail.streamPostIds` are introduced in Task 1 and reused consistently in Tasks 4 and 5.
- `HomeFeedPage.nextPage` / `HomeFeedPage.hasMore` are introduced in Task 2 and reused consistently in Task 3.
- The route helper names `shouldLoadMoreHomeFeed`, `shouldLoadMoreTopicPosts`, and `topicPostCountLabel` are defined in the same tasks that reference them.

### Review Findings

- Important: Task 3 depends on `loadMoreErrorMessage` being added in Task 2, and Task 5 depends on `TopicUiState.totalPosts` / `loadMoreErrorMessage` from Task 4. The current ordering already respects that dependency.
- Important: The plan keeps phase 2 MessageBus logic out of implementation, which matches the current spec boundary and avoids scope creep.
- Minor: `HomeRepository` parses the next page from `more_topics_url`; if a future site theme changes that format, the parsing regex will need its own focused test. That risk is already contained in Task 1 network coverage.

---

Plan complete and saved to `docs/superpowers/plans/2026-04-02-home-topic-pagination-refresh.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
