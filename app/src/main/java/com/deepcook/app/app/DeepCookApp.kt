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
import com.deepcook.app.feature.auth.AccountScreen
import com.deepcook.app.feature.auth.AuthFormState
import com.deepcook.app.feature.auth.AuthHost
import com.deepcook.app.feature.auth.AuthSessionState
import com.deepcook.app.feature.auth.AuthViewModel
import com.deepcook.app.feature.browse.*
import com.deepcook.app.feature.cooking.CookingScreen
import com.deepcook.app.feature.cooking.CookingViewModel
import com.deepcook.app.feature.cooking.FeedbackScreen
import com.deepcook.app.feature.cooking.FeedbackViewModel
import com.deepcook.app.feature.notes.DishNotesScreen
import com.deepcook.app.feature.profile.ProfileInfoScreen
import com.deepcook.app.feature.recipe.*
import com.deepcook.app.feature.search.SearchViewModel
import com.deepcook.app.feature.settings.SettingsScreen
import com.deepcook.app.feature.onboarding.FoodProfileScreen
import com.deepcook.app.feature.onboarding.FoodProfileViewModel
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

@Serializable
data class Reader(val recipeId: String, val initialStepId: String? = null, val showTimers: Boolean = false)

/** The cooking view: one step at a time, with the timer and (later) the assistant. */
@Serializable data class Cooking(val recipeId: String)

/** One sentence of feedback right after cooking, stored as a dish note. */
@Serializable data class Feedback(val recipeId: String)

@Serializable data object Recent

@Serializable data object MyRecipes

/** Every remembered dish correction, with hand editing. */
@Serializable data object Notes

@Serializable data object FoodPreferences


/** Name, picture, and taste tags; reached by tapping the heading on the profile page. */
@Serializable data object ProfileInfo

/** Phone number, password and sign-out; reached from 用户信息. */
@Serializable data object AccountSecurity

/** [recipeId] null means "a new recipe"; an id means "edit that one". */
@Serializable data class RecipeEditor(val recipeId: String? = null)

@Serializable data class Settings(val section: String = "main")

@Composable
fun DeepCookApp(
    vm: AppViewModel,
    state: AppUiState,
    container: AppContainer,
    auth: AuthViewModel,
    authSession: AuthSessionState,
    authForm: AuthFormState,
    timerRecipe: String? = null,
    onTimerHandled: () -> Unit = {},
) {
    // The previous version used a flat root graph. Do not restore its transient
    // navigation bundle into this new home-container graph on an APK upgrade.
    // A different account also gets a fresh stack, so signing out never comes back
    // to the page the previous person was reading.
    val nav = key("root-navigation-v2", authSession.session?.accountId) { rememberNavController() }
    LaunchedEffect(
        timerRecipe,
        state.loading,
        state.recipes,
        state.userLoaded,
        state.user.foodSetup.completed,
        authSession.isSignedIn,
    ) {
        // A timer can fire while the phone is logged out; wait for the next sign-in instead of
        // navigating a graph that is not on screen yet.
        if (timerRecipe != null && !state.loading && state.userLoaded && authSession.isSignedIn &&
            state.user.foodSetup.completed && state.recipes.isNotEmpty()
        ) {
            if (state.recipes.any { it.id == timerRecipe }) {
                nav.navigate(Reader(timerRecipe, showTimers = true)) {
                    launchSingleTop = true
                }
            }
            onTimerHandled()
        }
    }
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
                duration = SnackbarDuration.Short,
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
            if (state.loading || (!state.userLoaded && state.userError == null) || !authSession.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (!authSession.isSignedIn) {
                // The account gate sits in front of the whole shell: onboarding, browsing and
                // everything else is unreachable until a number is signed in.
                AuthHost(auth, authForm)
            } else if (state.userLoaded && !state.user.foodSetup.completed) {
                FoodSetupHost(container, editing = false)
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
                            onFoodPreferences = { openPage(FoodPreferences) },
                            onProfile = { openPage(ProfileInfo) },
                            onNotes = { openPage(Notes) },
                        )
                    }
                    composable<ProfileInfo> {
                        ProfileInfoScreen(
                            profile = state.user.profile,
                            onBack = { nav.popBackStack() },
                            onName = { name -> vm.updateProfile { it.copy(displayName = name) } },
                            foodProfile = state.user.foodSetup.profile,
                            onFoodPreferences = { openPage(FoodPreferences) },
                            savingAvatar = state.avatarSaving,
                            loadPhoto = vm::avatarSource,
                            onSaveAvatar = vm::saveAvatar,
                            onRemoveAvatar = vm::removeAvatar,
                            phone = authSession.session?.phone,
                            onAccount = { openPage(AccountSecurity) },
                        )
                    }
                    composable<AccountSecurity> {
                        AccountScreen(
                            state = authSession,
                            onBack = { nav.popBackStack() },
                            changePassword = auth::changePassword,
                            signOut = auth::signOut,
                        )
                    }
                    composable<Recent> {
                        RecentScreen(state, { nav.popBackStack() }, {
                            nav.getBackStackEntry<Home>().savedStateHandle["findRequest"] = System.nanoTime()
                            nav.popBackStack<Home>(inclusive = false)
                        }, { openPage(RecipeDetail(it)) }, vm::removeVisit)
                    }
                    composable<FoodPreferences> {
                        FoodSetupHost(container, editing = true, onCancel = { nav.popBackStack() })
                    }
                    composable<RecipeDetail> { detailEntry ->
                        val args = detailEntry.toRoute<RecipeDetail>()
                        val recipe = state.recipes.find { it.id == args.recipeId }
                        val restored by detailEntry.savedStateHandle
                            .getStateFlow<String?>("readStep", null).collectAsStateWithLifecycle()
                        if (recipe == null) {
                            EmptyState("没有找到这道菜", "内容可能已更新，请返回重新选择。", "返回", { nav.popBackStack() })
                        } else {
                            val dishAi: DishAiViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        DishAiViewModel(
                                            recipe = recipe,
                                            ai = container.ai,
                                            userData = container.userData,
                                            overlays = container.aiOverlays,
                                        )
                                    }
                                },
                            )
                            val community: CommunityViewModel = viewModel(
                                key = "community-${recipe.id}",
                                factory = viewModelFactory {
                                    initializer {
                                        CommunityViewModel(
                                            recipe.id,
                                            createSavedStateHandle(),
                                            container.kitchen,
                                            container.covers,
                                        )
                                    }
                                },
                            )
                            val communityState by community.state.collectAsStateWithLifecycle()
                            DetailScreen(
                                recipe = recipe,
                                favorite = state.user.favorites.any { it.recipeId == recipe.id },
                                preferences = state.user.preferences,
                                restoredStep = restored,
                                dishAi = dishAi,
                                onFavorite = { vm.favorite(recipe.id) },
                                onBack = { nav.popBackStack() },
                                onRead = { openPage(Reader(recipe.id, it)) },
                                onCook = { openPage(Cooking(recipe.id)) },
                                onVisit = { vm.visit(recipe.id) },
                                onPhotoVisibilityChange = { detailPhotoVisible = it },
                                favoriteCount =
                                (state.favoriteExamples[recipe.id] ?: 0) +
                                    if (state.user.favorites.any { it.recipeId == recipe.id }) 1 else 0,
                                ratingContent = { RatingSummary(communityState) { community.open("rating") } },
                                nutritionContent = { NutritionSection(communityState) { community.open("nutrition") } },
                                communityContent = { CommunitySection(communityState, community) },
                            )
                            CommunityDialogs(communityState, community)
                        }
                    }
                    composable<Reader> { readerEntry ->
                        val args = readerEntry.toRoute<Reader>()
                        val recipe = state.recipes.find { it.id == args.recipeId }
                        if (recipe != null) {
                            ReaderScreen(
                                recipe,
                                args.initialStepId,
                                state.user.preferences,
                                container.cookSessions,
                                args.showTimers,
                            ) { step ->
                                nav.previousBackStackEntry?.savedStateHandle?.set("readStep", step)
                                nav.popBackStack()
                            }
                        } else {
                            EmptyState("没有找到这道菜", "请返回重新选择。", "返回", { nav.popBackStack() })
                        }
                    }
                    composable<Cooking> { cookingEntry ->
                        val args = cookingEntry.toRoute<Cooking>()
                        val recipe = state.recipes.find { it.id == args.recipeId }
                        if (recipe == null) {
                            EmptyState("没有找到这道菜", "内容可能已更新，请返回重新选择。", "返回", { nav.popBackStack() })
                        } else {
                            val titles = remember(state.recipes) {
                                state.recipes.associate { it.id to it.title }
                            }
                            val cooking: CookingViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        CookingViewModel(
                                            recipe = recipe,
                                            sessions = container.cookSessions,
                                            ai = container.ai,
                                            book = container.playbook,
                                            userData = container.userData,
                                            titles = titles,
                                            overlays = container.aiOverlays,
                                            enricher = container.enricher,
                                        )
                                    }
                                },
                            )
                            CookingScreen(
                                recipe = recipe,
                                vm = cooking,
                                preferences = state.user.preferences,
                                onBack = { nav.popBackStack() },
                                onComplete = {
                                    nav.navigate(Feedback(recipe.id)) {
                                        popUpTo<Cooking> { inclusive = true }
                                    }
                                },
                                onDeleteNote = vm::deleteDishNote,
                            )
                        }
                    }
                    composable<Feedback> { feedbackEntry ->
                        val args = feedbackEntry.toRoute<Feedback>()
                        val recipe = state.recipes.find { it.id == args.recipeId }
                        if (recipe == null) {
                            EmptyState("没有找到这道菜", "内容可能已更新，请返回重新选择。", "返回", { nav.popBackStack() })
                        } else {
                            val feedback: FeedbackViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { FeedbackViewModel(container.noteSuggester) }
                                },
                            )
                            FeedbackScreen(
                                recipe = recipe,
                                existingNotes = state.user.notesFor(recipe.id),
                                voiceEnabled = state.user.preferences.voiceInputEnabled,
                                vm = feedback,
                                onBack = { nav.popBackStack() },
                                onCover = { uri -> vm.setRecipeCover(recipe.id, uri) },
                                onSave = { text, raw ->
                                    vm.saveDishNote(recipe.id, text, raw)
                                    nav.popBackStack()
                                },
                            )
                        }
                    }
                    composable<Notes> {
                        DishNotesScreen(
                            notes = state.user.dishNotes,
                            recipes = state.recipes,
                            onBack = { nav.popBackStack() },
                            onOpen = { openPage(RecipeDetail(it)) },
                            onEdit = vm::editDishNote,
                            onDelete = vm::deleteDishNote,
                            onAdd = vm::addDishNote,
                            onFind = {
                                nav.getBackStackEntry<Home>().savedStateHandle["findRequest"] = System.nanoTime()
                                nav.popBackStack<Home>(inclusive = false)
                            },
                        )
                    }
                    composable<Settings> { settingsEntry ->
                        SettingsScreen(
                            settingsEntry.toRoute<Settings>().section, state.user.preferences,
                            state.user.favorites.size, state.user.recent.size, state.user.dishNotes.size,
                            state.recipes.filterNot { it.id.startsWith(USER_RECIPE_ID_PREFIX) },
                            { openPage(Settings(it)) }, { nav.popBackStack() },
                            vm::preferences, vm::clearRecent, vm::clearFavorites, vm::resetPreferences,
                            vm::clearAiOverlay,
                            onNotes = { openPage(Notes) },
                        )
                    }
                    composable<MyRecipes> {
                        MyRecipesScreen(
                            state,
                            onBack = { nav.popBackStack() },
                            onAdd = { openPage(RecipeEditor()) },
                            onEdit = { openPage(RecipeEditor(it)) },
                            onOpen = { openPage(RecipeDetail(it)) },
                            onCook = { openPage(Cooking(it)) },
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
    onFoodPreferences: () -> Unit,
    onProfile: () -> Unit,
    onNotes: () -> Unit,
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
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                            state = state,
                            onSettings = onSettings,
                            onRecent = onRecent,
                            onMyRecipes = onMyRecipes,
                            onFoodPreferences = onFoodPreferences,
                            onOpen = onOpen,
                            onFavorite = vm::favorite,
                            onFind = { selectTab(0) },
                            onProfile = onProfile,
                            onNotes = onNotes,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodSetupHost(container: AppContainer, editing: Boolean, onCancel: () -> Unit = {}) {
    val setup: FoodProfileViewModel = viewModel(
        key = if (editing) "edit-food-preferences" else "first-food-preferences",
        factory = viewModelFactory {
            initializer { FoodProfileViewModel(container.userData, createSavedStateHandle(), editing) }
        },
    )
    val state by setup.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done && editing) onCancel() }
    FoodProfileScreen(setup, state, onCancel)
}
