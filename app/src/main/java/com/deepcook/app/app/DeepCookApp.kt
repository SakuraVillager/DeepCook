@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.app

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.deepcook.app.R
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.USER_RECIPE_ID_PREFIX
import com.deepcook.app.data.AppContainer
import com.deepcook.app.feature.authoring.MyRecipesScreen
import com.deepcook.app.feature.authoring.RecipeEditorScreen
import com.deepcook.app.feature.authoring.RecipeEditorViewModel
import com.deepcook.app.feature.browse.*
import com.deepcook.app.feature.profile.ProfileInfoScreen
import com.deepcook.app.feature.recipe.*
import com.deepcook.app.feature.search.SearchViewModel
import com.deepcook.app.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance
import java.util.UUID

@Serializable data object Home

@Serializable data object Discover

@Serializable data object Favorites

@Serializable data object Profile

@Serializable data class Search(val category: String = "全部")

@Serializable data class RecipeDetail(val recipeId: String)

@Serializable data class Reader(val recipeId: String, val initialStepId: String? = null)

@Serializable data object Recent

@Serializable data object MyRecipes

/** Name, picture, and taste tags; reached by tapping the heading on the profile page. */
@Serializable data object ProfileInfo

/** [recipeId] null means "a new recipe"; an id means "edit that one". */
@Serializable data class RecipeEditor(val recipeId: String? = null)

@Serializable data class Settings(val section: String = "main")

@Composable
fun DeepCookApp(vm: AppViewModel, state: AppUiState, container: AppContainer) {
    // The previous version used a flat root graph. Do not restore its transient
    // navigation bundle into this new home-container graph on an APK upgrade.
    val nav = key("root-navigation-v2") { rememberNavController() }
    val entry by nav.currentBackStackEntryAsState()
    val activity = LocalActivity.current
    val light = MaterialTheme.colorScheme.background.luminance() > .5
    var detailPhotoVisible by remember { mutableStateOf(false) }
    val photoHeader = entry?.destination?.hasRoute<RecipeDetail>() == true && detailPhotoVisible
    LaunchedEffect(activity, light, photoHeader) {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = light && !photoHeader
                isAppearanceLightNavigationBars = light
            }
        }
    }
    val slideDistance = rememberSlideDistance()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val result = snackbars.showSnackbar(
                message.text,
                actionLabel = if (message.undo != null) "撤销" else null,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.let(vm::undo)
        }
    }
    val wide = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density >= 600f
    val homeVisible = entry?.destination?.hasRoute<Home>() == true
    fun openPage(route: Any) {
        // Ignore repeated taps until Navigation finishes the current transition.
        if (nav.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
            nav.navigate(route)
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(
                snackbars,
                Modifier.navigationBarsPadding().padding(bottom = if (homeVisible && !wide) 80.dp else 0.dp),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val storageError = state.userError ?: state.userRecipeError
            if (storageError != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(storageError, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = {
                            if (state.userError !=
                                null
                            ) {
                                vm.observeUserData()
                            } else {
                                vm.observeUserRecipes()
                            }
                        }) { Text("重试") }
                    }
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.contentError != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("菜谱暂不可用", state.contentError, "重试", vm::loadRecipes)
                }
            } else {
                // Mihon's outer Navigator moves the entire home container,
                // including its navigation bar/rail, when opening a child page.
                NavHost(
                    navController = nav,
                    startDestination = Home,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { materialSharedAxisXIn(forward = true, slideDistance = slideDistance) },
                    exitTransition = { materialSharedAxisXOut(forward = true, slideDistance = slideDistance) },
                    popEnterTransition = { materialSharedAxisXIn(forward = false, slideDistance = slideDistance) },
                    popExitTransition = { materialSharedAxisXOut(forward = false, slideDistance = slideDistance) },
                ) {
                    composable<Home> { homeEntry ->
                        val findRequest by homeEntry.savedStateHandle
                            .getStateFlow("findRequest", 0L).collectAsStateWithLifecycle()
                        HomeScreen(
                            vm,
                            state,
                            findRequest,
                            onFindHandled = { homeEntry.savedStateHandle["findRequest"] = 0L },
                            onOpen = { openPage(RecipeDetail(it)) },
                            onSettings = { openPage(Settings()) },
                            onRecent = { openPage(Recent) },
                            onMyRecipes = { openPage(MyRecipes) },
                            onAddRecipe = { openPage(RecipeEditor()) },
                            onProfile = { openPage(ProfileInfo) },
                        )
                    }
                    composable<ProfileInfo> {
                        ProfileInfoScreen(
                            profile = state.user.profile,
                            onBack = { nav.popBackStack() },
                            onName = { name -> vm.updateProfile { it.copy(displayName = name) } },
                            onToggleLike = { tag, on -> vm.updateProfile { it.withLike(tag, on) } },
                            onToggleAvoid = { tag, on -> vm.updateProfile { it.withAvoid(tag, on) } },
                            onClearLikes = { vm.updateProfile { it.copy(likes = emptySet()) } },
                            onClearAvoids = { vm.updateProfile { it.copy(avoids = emptySet()) } },
                            loadPhoto = vm::avatarSource,
                            onSaveAvatar = vm::saveAvatar,
                            onRemoveAvatar = vm::removeAvatar,
                        )
                    }
                    composable<Recent> {
                        RecentScreen(state, { nav.popBackStack() }, {
                            nav.getBackStackEntry<Home>().savedStateHandle["findRequest"] = System.nanoTime()
                            nav.popBackStack<Home>(inclusive = false)
                        }, { openPage(RecipeDetail(it)) }, vm::removeVisit)
                    }
                    composable<RecipeDetail> { detailEntry ->
                        val args = detailEntry.toRoute<RecipeDetail>()
                        val recipe = state.recipes.find { it.id == args.recipeId }
                        val restored by detailEntry.savedStateHandle
                            .getStateFlow<String?>("readStep", null).collectAsStateWithLifecycle()
                        if (recipe == null) {
                            EmptyState("没有找到这道菜", "内容可能已更新，请返回重新选择。", "返回", { nav.popBackStack() })
                        } else {
                            DetailScreen(
                                recipe, state.user.favorites.any { it.recipeId == recipe.id },
                                state.user.preferences, restored,
                                { vm.favorite(recipe.id) }, { nav.popBackStack() },
                                { openPage(Reader(recipe.id, it)) }, { vm.visit(recipe.id) },
                                { detailPhotoVisible = it },
                            )
                        }
                    }
                    composable<Reader> { readerEntry ->
                        val args = readerEntry.toRoute<Reader>()
                        val recipe = state.recipes.find { it.id == args.recipeId }
                        if (recipe != null) {
                            ReaderScreen(recipe, args.initialStepId, state.user.preferences) { step ->
                                nav.previousBackStackEntry?.savedStateHandle?.set("readStep", step)
                                nav.popBackStack()
                            }
                        } else {
                            EmptyState("没有找到这道菜", "请返回重新选择。", "返回", { nav.popBackStack() })
                        }
                    }
                    composable<Settings> { settingsEntry ->
                        SettingsScreen(
                            settingsEntry.toRoute<Settings>().section, state.user.preferences,
                            state.user.favorites.size, state.user.recent.size,
                            state.recipes.filterNot { it.id.startsWith(USER_RECIPE_ID_PREFIX) },
                            { openPage(Settings(it)) }, { nav.popBackStack() },
                            vm::preferences, vm::clearRecent, vm::clearFavorites, vm::resetPreferences,
                        )
                    }
                    composable<MyRecipes> {
                        MyRecipesScreen(
                            state,
                            onBack = { nav.popBackStack() },
                            onAdd = { openPage(RecipeEditor()) },
                            onEdit = { openPage(RecipeEditor(it)) },
                            onOpen = { openPage(RecipeDetail(it)) },
                            onFavorite = vm::favorite,
                            onDelete = vm::deleteRecipe,
                        )
                    }
                    composable<RecipeEditor> { editorEntry ->
                        val args = editorEntry.toRoute<RecipeEditor>()
                        val existing = args.recipeId?.let { id -> state.recipes.find { it.id == id } }
                        val editor: RecipeEditorViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    // The id is decided once per editor session, so an edit keeps
                                    // it and a new recipe gets a stable one before its cover is
                                    // written.
                                    val saved = createSavedStateHandle()
                                    val id = args.recipeId
                                        ?: saved.get<String>("newRecipeId")
                                        ?: (USER_RECIPE_ID_PREFIX + UUID.randomUUID().toString())
                                            .also { generated -> saved["newRecipeId"] = generated }
                                    RecipeEditorViewModel(
                                        container.lookup,
                                        container.covers,
                                        container.userRecipes,
                                        id,
                                        existing,
                                        saved,
                                    )
                                }
                            },
                        )
                        RecipeEditorScreen(
                            editor,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    vm: AppViewModel,
    state: AppUiState,
    findRequest: Long,
    onFindHandled: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onRecent: () -> Unit,
    onMyRecipes: () -> Unit,
    onAddRecipe: () -> Unit,
    onProfile: () -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val selected = when {
        destination?.hasRoute<Favorites>() == true -> 1
        destination?.hasRoute<Profile>() == true -> 2
        else -> 0
    }
    val labels = listOf(
        stringResource(R.string.discover),
        stringResource(R.string.favorites),
        stringResource(R.string.profile),
    )
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.StarBorder, Icons.Rounded.PersonOutline)
    val routes = listOf(Discover, Favorites, Profile)
    fun selectTab(index: Int) {
        if (selected == index) return
        nav.navigate(routes[index]) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(findRequest, destination?.id) {
        // Home can be recreated before its inner graph restores the active tab.
        // Consume the command only once an actual destination is available.
        if (findRequest != 0L && destination != null) {
            selectTab(0)
            onFindHandled()
        }
    }
    val slideDistance = rememberSlideDistance()
    BoxWithConstraints {
        val rail = maxWidth >= 600.dp
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!rail) {
                    NavigationBar {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { selectTab(index) },
                                icon = { Icon(icons[index], null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (rail) {
                    NavigationRail {
                        labels.forEachIndexed { index, label ->
                            NavigationRailItem(
                                selected = selected == index,
                                onClick = { selectTab(index) },
                                icon = { Icon(icons[index], null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                // Mihon's HomeScreen uses 200ms Fade Through, initialScale=1f.
                // Only tab content animates; the main navigation stays in place.
                NavHost(
                    navController = nav,
                    startDestination = Discover,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enterTransition = {
                        homeEnterTransition(initialState.destination, targetState.destination, slideDistance, true)
                    },
                    exitTransition = {
                        homeExitTransition(initialState.destination, targetState.destination, slideDistance, true)
                    },
                    popEnterTransition = {
                        homeEnterTransition(initialState.destination, targetState.destination, slideDistance, false)
                    },
                    popExitTransition = {
                        homeExitTransition(initialState.destination, targetState.destination, slideDistance, false)
                    },
                ) {
                    composable<Discover> {
                        DiscoverScreen(state, { nav.navigate(Search(it)) }, onOpen, vm::favorite)
                    }
                    composable<Search> { searchEntry ->
                        val args = searchEntry.toRoute<Search>()
                        val searchVM: SearchViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { SearchViewModel(createSavedStateHandle(), args.category) }
                            },
                        )
                        val filter by searchVM.filter.collectAsStateWithLifecycle()
                        val query by searchVM.query.collectAsStateWithLifecycle()
                        SearchScreen(
                            state, filter, query, searchVM::query, searchVM::category,
                            searchVM::time, searchVM::difficulty, searchVM::clearFilters,
                            { nav.popBackStack() }, onOpen, vm::favorite,
                        )
                    }
                    composable<Favorites> {
                        FavoritesScreen(state, { selectTab(0) }, onOpen, vm::favorite)
                    }
                    composable<Profile> {
                        ProfileScreen(
                            state, onSettings, { selectTab(1) }, onRecent, onMyRecipes, onAddRecipe,
                            onOpen, vm::favorite, { selectTab(0) }, onProfile,
                        )
                    }
                }
            }
        }
    }
}
