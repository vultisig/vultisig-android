package com.vultisig.wallet.ui.components.v2.bottomsheets.navhost

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass


/**
 * Custom navigation host specifically designed for bottom sheet navigation.
 *
 * This component provides smooth height animations during navigation transitions within bottom sheets.
 * Using the default Jetpack Compose Navigation component causes lag and delayed animations when
 * the bottom sheet content height changes during navigation, as it doesn't properly coordinate
 * with the bottom sheet's height animation system.
 *
 * This custom implementation uses [animateContentSize] to smoothly animate height changes
 * when navigating between different composables, providing a seamless user experience.
 *
 * @param modifier Modifier to be applied to the navigation host container
 * @param navController Custom navigation controller for managing bottom sheet navigation state
 * @param content Lambda to define the navigation graph with composable destinations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VsBottomSheetNavHost(
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    navController: VsBottomSheetNavController,
    content: VsBottomSheetNavGraphBuilder.() -> Unit
) {
    val builder = remember { VsBottomSheetNavGraphBuilder() }
    builder.content()

    val currentRouteData = navController.currentRouteData
    val currentRouteClass = currentRouteData::class.qualifiedName ?: ""

    var isExpanded by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(sheetState.currentValue) {
        isExpanded = sheetState.currentValue != SheetValue.Hidden
    }

    if (builder.hasRoute(currentRouteClass)) {
        val currentContent = builder.getContent(
            currentRouteClass,
            currentRouteData
        )
        Box(
            modifier = if (isExpanded) modifier.animateContentSize() else modifier,
            content = { currentContent() }
        )
    }
}

internal class VsBottomSheetNavGraphBuilder {
    private val routes = mutableMapOf<String, @Composable (Any) -> Unit>()

    private fun <T : Any> composable(
        routeClass: KClass<T>,
        content: @Composable (T) -> Unit
    ) {
        val className = routeClass.qualifiedName
            ?: throw IllegalArgumentException("Route class must have a qualified name")
        routes[className] = { routeData ->
            @Suppress("UNCHECKED_CAST")
            if (routeClass.isInstance(routeData)) {
                content(routeData as T)
            }
        }
    }

    inline fun <reified T : Any> VsBottomSheetNavGraphBuilder.composable(
        noinline content: @Composable (T) -> Unit
    ) {
        composable(T::class, content)
    }
    fun getContent(routeClass: String, routeData: Any): @Composable () -> Unit = {
        routes.getValue(routeClass)(routeData)
    }

    fun hasRoute(routeClass: String): Boolean = routes.containsKey(routeClass)
}

@Stable
internal class VsBottomSheetNavController(initialRoute: Any) {

    private val json = Json { ignoreUnknownKeys = true }
    private val _backStack = mutableStateListOf<RouteEntry>()
    private var _currentRouteData = mutableStateOf(initialRoute)

    val currentRouteData: Any by _currentRouteData
    val backStack: List<Any> get() = _backStack.map { it.data }
    val canGoBack: Boolean get() = _backStack.size > 1

    data class RouteEntry(
        val data: Any,
        val serializedData: String,
        val className: String
    )

    init {
        if (_backStack.isEmpty()) {
            val entry = createRouteEntry(initialRoute)
            _backStack.add(entry)
            _currentRouteData.value = initialRoute
        }
    }

    private fun createRouteEntry(route: Any): RouteEntry {
        return RouteEntry(
            data = route,
            serializedData = try {
                json.encodeToString(route)
            } catch (_: Exception) {
                route.toString()
            },
            className = route::class.qualifiedName ?: route::class.simpleName ?: "Unknown"
        )
    }

    private fun RouteEntry.isSameRoute(other: RouteEntry): Boolean {
        return this.className == other.className && this.serializedData == other.serializedData
    }

    fun navigate(route: Any) {
        val newEntry = createRouteEntry(route)
        val currentEntry = _backStack.lastOrNull()

        if (currentEntry == null || !newEntry.isSameRoute(currentEntry)) {
            _backStack.add(newEntry)
            _currentRouteData.value = route
        }
    }

    fun navigateAndClearStack(route: Any) {
        _backStack.clear()
        val entry = createRouteEntry(route)
        _backStack.add(entry)
        _currentRouteData.value = route
    }

    fun popBackStack(): Boolean {
        return if (_backStack.size > 1) {
            _backStack.removeLastOrNull()
            val lastEntry = _backStack.lastOrNull()
            if (lastEntry != null) {
                _currentRouteData.value = lastEntry.data
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    inline fun <reified T : Any> popBackStackTo(route: T): Boolean {
        val targetEntry = createRouteEntry(route)
        val index = _backStack.indexOfLast { it.isSameRoute(targetEntry) }

        return if (index != -1) {
            while (_backStack.size > index + 1) {
                _backStack.removeLastOrNull()
            }
            _currentRouteData.value = _backStack[index].data
            true
        } else {
            false
        }
    }

    inline fun <reified T : Any> popBackStackToClass(): Boolean {
        val targetClassName = T::class.qualifiedName
        val index = _backStack.indexOfLast { it.className == targetClassName }

        return if (index != -1) {
            // Remove all routes after the target route
            while (_backStack.size > index + 1) {
                _backStack.removeLastOrNull()
            }
            _currentRouteData.value = _backStack[index].data
            true
        } else {
            false
        }
    }
}

@Composable
internal fun rememberVsBottomSheetNavController(initialRoute: Any) =
    remember { VsBottomSheetNavController(initialRoute) }