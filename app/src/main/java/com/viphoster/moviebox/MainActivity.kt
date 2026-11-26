package com.viphoster.moviebox
import androidx.compose.material3.TextButton

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.viphoster.moviebox.data.*
import com.viphoster.moviebox.ui.VideoPlayerScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF0D1013),
                surface = Color(0xFF151920),
                primary = Color(0xFF2563EB)
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1013)) {
                    MovieBoxApp()
                }
            }
        }
    }
}

@Composable
fun MovieBoxApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mbx_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var catalog by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var profile by remember { mutableStateOf(UserProfileData()) }
    var loading by remember { mutableStateOf(false) }

    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var playingStream by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showProfile by remember { mutableStateOf(false) }

    fun saveToken(newTok: String) {
        token = newTok
        prefs.edit().putString("token", newTok).apply()
    }

    suspend fun loadAllData(tok: String) {
        if (tok.isBlank()) return
        loading = true
        try {
            val bearer = "Bearer $tok"
            val userInfo = ApiClient.api.getMe(bearer)
            if (!userInfo.authenticated && userInfo.role.isNullOrBlank()) {
                saveToken("")
                me = null
                return
            }
            me = userInfo
            catalog = ApiClient.api.getCatalog(bearer).items
            profile = ApiClient.api.getUserProfile(bearer).resolved()
        } catch (e: Exception) {
            Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(token) {
        if (token.isNotBlank()) loadAllData(token)
    }

    fun toggleWatchlist(mediaId: String, target: String) {
        val curWatching = profile.watching.toMutableList()
        val curWatched = profile.watched.toMutableList()
        val curPlan = profile.planToWatch.toMutableList()
        if (target == "watched") {
            if (curWatched.contains(mediaId)) curWatched.remove(mediaId)
            else { curWatched.add(mediaId); curPlan.remove(mediaId); curWatching.remove(mediaId) }
        } else if (target == "plan") {
            if (curPlan.contains(mediaId)) curPlan.remove(mediaId)
            else { curPlan.add(mediaId); curWatched.remove(mediaId) }
        } else if (target == "watching") {
            if (!curWatching.contains(mediaId) && !curWatched.contains(mediaId)) curWatching.add(mediaId)
        }
        val updated = UserProfileData(curWatching, curWatched, curPlan)
        profile = updated
        scope.launch {
            try { ApiClient.api.saveUserProfile("Bearer $token", updated) } catch (_: Exception) {}
        }
    }

    playingStream?.let { (fileId, title) ->
        VideoPlayerScreen(title = title, fileId = fileId, token = token, onClose = { playingStream = null })
        return
    }

    if (token.isBlank() || me == null) {
        LoginScreen(loading = loading, onSuccess = { newTok -> saveToken(newTok) })
        return
    }

    val isAdmin = me?.role == "admin"

    if (showProfile) {
        ProfileScreen(
            me = me!!,
            catalog = catalog,
            profile = profile,
            onBack = { showProfile = false },
            onSelectMedia = { showProfile = false; selectedMedia = it },
            onLogout = { saveToken(""); me = null; showProfile = false }
        )
        return
    }

    selectedMedia?.let { item ->
        MediaDetailScreen(
            item = item,
            catalog = catalog,
            profile = profile,
            onBack = { selectedMedia = null },
            onPlay = { fileId, epTitle ->
                toggleWatchlist(item.id, "watching")
                playingStream = fileId to epTitle
            },
            onToggleWatched = { toggleWatchlist(item.id, "watched") },
            onTogglePlan = { toggleWatchlist(item.id, "plan") },
            onOpenRecommended = { selectedMedia = it }
        )
        return
    }

    CatalogScreen(
        catalog = catalog,
        isAdmin = isAdmin,
        loading = loading,
        onRefresh = { scope.launch { loadAllData(token) } },
        onOpenProfile = { showProfile = true },
        onSelectMedia = { selectedMedia = it }
    )
}

@Composable
fun LoginScreen(loading: Boolean, onSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adminPass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingCredential by remember { mutableStateOf<String?>(null) }
    var inviteCode by remember { mutableStateOf("") }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: ""
            if (idToken.isNotBlank()) {
                scope.launch {
                    busy = true
                    try {
                        val res = ApiClient.api.authGoogle(GoogleAuthRequest(credential = idToken))
                        if (res.status == "needs_invite") pendingCredential = idToken
                        else if (!res.token.isNullOrBlank()) onSuccess(res.token)
                        else Toast.makeText(context, res.detail ?: "Login failed", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally { busy = false }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MovieBox Cloud", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text("Personal Cloud Cinema", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(28.dp))

        if (pendingCredential != null) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it.uppercase() },
                label = { Text("Enter Invite Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val res = ApiClient.api.bindGoogleInvite(
                                GoogleBindRequest(credential = pendingCredential!!, code = inviteCode.trim())
                            )
                            if (!res.token.isNullOrBlank()) onSuccess(res.token)
                            else Toast.makeText(context, res.detail ?: "Invalid invite code", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally { busy = false }
                    }
                },
                enabled = !busy && inviteCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Activate Invite") }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val cfg = ApiClient.api.getAuthConfig()
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(cfg.googleClientId ?: "")
                                .requestEmail()
                                .build()
                            googleLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Config error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally { busy = false }
                    }
                },
                enabled = !busy && !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Continue with Google", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(20.dp))

            var passVisible by remember { mutableStateOf(false) }
            var loginError by remember { mutableStateOf("") }
            OutlinedTextField(
                value = adminPass,
                onValueChange = { adminPass = it; loginError = "" },
                label = { Text("Admin Password") },
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { passVisible = !passVisible }) {
                        Text(if (passVisible) "Hide" else "Show")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val res = ApiClient.api.adminLogin(LoginRequest(adminPass))
                            if (!res.token.isNullOrBlank()) onSuccess(res.token)
                            else { loginError = res.detail ?: "Wrong password"; Toast.makeText(context, loginError, Toast.LENGTH_SHORT).show() }
                        } catch (e: Exception) {
                            loginError = "Login error: ${e.message}"; Toast.makeText(context, loginError, Toast.LENGTH_SHORT).show()
                        } finally { busy = false }
                    }
                },
                enabled = !busy && adminPass.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text(if (busy) "Logging in..." else "Admin Login") }
            if (loginError.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(loginError, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CatalogScreen(
    catalog: List<MediaItem>,
    isAdmin: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectMedia: (MediaItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("All") }
    val tabs = remember(isAdmin) {
        if (isAdmin) listOf("All", "Anime", "Movies", "Series", "18+", "Admin")
        else listOf("All", "Anime", "Movies", "Series")
    }
    val filtered = remember(catalog, query, selectedCat, isAdmin) {
        catalog.filter { item ->
            if (!isAdmin && (item.public == false || item.isAdult == true)) return@filter false
            val matchesCat = when (selectedCat) {
                "All" -> true
                "18+" -> item.isAdult == true || item.category.equals("18+", true) || item.tags.orEmpty().any { it.equals("18+", true) }
                "Anime" -> item.category.equals("Anime", true) || (item.tags?.any { it.equals("ANIME", true) } == true)
                "Movies" -> item.category.equals("Movies", true) || (item.episodes.isNullOrEmpty() && item.isAdult != true)
                "Series" -> item.category.equals("Series", true) || !item.episodes.isNullOrEmpty()
                else -> true
            }
            val matchesQuery = query.isBlank() ||
                item.displayTitle().contains(query, ignoreCase = true) ||
                (item.tags?.any { it.contains(query, ignoreCase = true) } == true)
            matchesCat && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("MovieBox Cloud", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1E242E))
            ) { Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White) }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search movies, anime, series, tags...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                FilterChip(selected = selectedCat == tab, onClick = { selectedCat = tab }, label = { Text(tab, fontSize = 12.sp) })
            }
        }

        if (selectedCat == "Admin" && isAdmin) {
            val tok = remember { SessionStore(context).getToken() ?: "" }
            AdminPanel(token = tok, catalog = catalog, onRefresh = onRefresh)
        } else if (loading && catalog.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { item ->
                    MediaPosterCard(item = item, onClick = { onSelectMedia(item) })
                }
            }
        }
    }
}

@Composable
fun MediaPosterCard(item: MediaItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF151920))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color(0xFF1E242E))) {
            AsyncImage(
                model = item.displayPoster(),
                contentDescription = item.displayTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.displayRating() > 0) {
                Text(
                    text = "★ ${"%.1f".format(item.displayRating())}",
                    color = Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            val badge = if (!item.episodes.isNullOrEmpty()) "${item.episodes.size} Eps" else (item.category ?: "Movie")
            Text(
                text = badge,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2563EB).copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(item.displayTitle(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!item.year.isNullOrBlank()) Text(item.year, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
fun MediaDetailScreen(
    item: MediaItem,
    catalog: List<MediaItem>,
    profile: UserProfileData,
    onBack: () -> Unit,
    onPlay: (String, String) -> Unit,
    onToggleWatched: () -> Unit,
    onTogglePlan: () -> Unit,
    onOpenRecommended: (MediaItem) -> Unit
) {
    BackHandler { onBack() }
    val scrollState = rememberScrollState()
    LaunchedEffect(item.id) { scrollState.scrollTo(0) }

    val isWatched = profile.watched.contains(item.id)
    val isPlan = profile.planToWatch.contains(item.id)
    val recommended = remember(item.id, catalog) {
        val baseTags = (item.tags ?: emptyList()).map { it.uppercase() }.toSet()
        catalog.filter { it.id != item.id && it.public != false }
            .sortedByDescending { cand ->
                val cTags = (cand.tags ?: emptyList()).map { it.uppercase() }
                var score = cTags.count { baseTags.contains(it) } * 4
                if (cand.category.equals(item.category, true)) score += 3
                score
            }.take(6)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).background(Color(0xFF0D1013))) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = item.displayPoster(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(26.dp).alpha(0.22f)
            )
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0D1013)))
                )
            )
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f))
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.displayTitle(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        val meta = listOfNotNull(
                            item.year?.takeIf { it.isNotBlank() },
                            item.category?.takeIf { it.isNotBlank() },
                            item.displayRating().takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" }
                        ).joinToString(" • ")
                        Text(meta, fontSize = 12.sp, color = Color(0xFFFBBF24))
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (item.tags ?: emptyList()).take(6).forEach { tag ->
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    color = Color(0xFF93C5FD),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E3A8A).copy(alpha = 0.45f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    AsyncImage(
                        model = item.displayPoster(),
                        contentDescription = item.displayTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(104.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    )
                }

                if (item.displayOverview().isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(item.displayOverview(), fontSize = 13.sp, color = Color(0xFFD1D5DB), lineHeight = 19.sp)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onPlay(item.playableFileId(), item.displayTitle()) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("PLAY", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onToggleWatched,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isWatched) Color(0xFF166534).copy(alpha = 0.45f) else Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) { Text(if (isWatched) "✓ Watched" else "Watched", fontSize = 13.sp, color = Color.White) }

                    OutlinedButton(
                        onClick = onTogglePlan,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isPlan) Color(0xFF1E3A8A).copy(alpha = 0.55f) else Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) { Text(if (isPlan) "✓ Plan To Watch" else "Plan To Watch", fontSize = 13.sp, color = Color.White) }
                }
            }
        }

        val eps = item.episodes ?: emptyList()
        if (eps.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("EPISODES (${eps.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                eps.chunked(4).forEach { rowEps ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowEps.forEachIndexed { idx, ep ->
                            val label = ep.title ?: ep.name ?: "E${ep.episode ?: (idx + 1)}"
                            OutlinedButton(
                                onClick = { onPlay(ep.id, "${item.displayTitle()} - $label") },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        repeat(4 - rowEps.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (recommended.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MORE LIKE THIS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                recommended.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { rec -> Box(Modifier.weight(1f)) { MediaPosterCard(item = rec, onClick = { onOpenRecommended(rec) }) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    me: MeResponse,
    catalog: List<MediaItem>,
    profile: UserProfileData,
    onBack: () -> Unit,
    onSelectMedia: (MediaItem) -> Unit,
    onLogout: () -> Unit
) {
    BackHandler { onBack() }
    var tab by remember { mutableStateOf("Watching") }
    val listIds = when (tab) {
        "Watching" -> profile.watching
        "Watched" -> profile.watched
        else -> profile.planToWatch
    }
    val items = remember(listIds, catalog) { catalog.filter { listIds.contains(it.id) } }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Column {
                    Text(me.name ?: "User Profile", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(me.email ?: (me.role ?: "Guest"), fontSize = 12.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = Color(0xFFEF4444)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Watching" to profile.watching.size, "Watched" to profile.watched.size, "Plan To Watch" to profile.planToWatch.size)
                .forEach { (label, count) ->
                    FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text("$label ($count)", fontSize = 12.sp) })
                }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No titles in $tab yet", color = Color.Gray, fontSize = 14.sp) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item -> MediaPosterCard(item = item, onClick = { onSelectMedia(item) }) }
            }
        }
    }
}


@Composable
fun AdminPanel(
    token: String,
    catalog: List<MediaItem>,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val bearer = remember(token) { "Bearer $token" }

    val list = remember(catalog, search) {
        val q = search.trim()
        if (q.isEmpty()) catalog
        else catalog.filter { it.displayTitle().contains(q, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            ApiClient.api.adminSync(bearer)
                            Toast.makeText(context, "Drive sync started", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally { busy = false }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Sync Drive", fontSize = 12.sp) }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            ApiClient.api.adminPublicAll(bearer)
                            Toast.makeText(context, "All items set Public", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally { busy = false }
                    }
                }
            ) { Text("Public All", fontSize = 12.sp) }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            ApiClient.api.adminPrivateAll(bearer)
                            Toast.makeText(context, "All items set Private", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally { busy = false }
                    }
                }
            ) { Text("Private All", fontSize = 12.sp) }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            singleLine = true,
            placeholder = { Text("Filter admin items (${catalog.size})...", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { "adm_" + it.id }) { item ->
                val isPub = item.public != false
                val is18 = item.isAdult == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF151920))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayTitle(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.category ?: "Media"} • ${if (isPub) "Public" else "Private"}${if (is18) " • 18+" else ""}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }
                    FilterChip(
                        selected = isPub,
                        onClick = {
                            scope.launch {
                                try {
                                    ApiClient.api.setMediaVisibility(
                                        bearer,
                                        item.playableFileId(),
                                        mapOf("public" to !isPub)
                                    )
                                    onRefresh()
                                } catch (_: Exception) {}
                            }
                        },
                        label = { Text(if (isPub) "Public" else "Private", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = is18,
                        onClick = {
                            scope.launch {
                                try {
                                    ApiClient.api.setMediaAdult(
                                        bearer,
                                        item.playableFileId(),
                                        mapOf("is_adult" to !is18)
                                    )
                                    onRefresh()
                                } catch (_: Exception) {}
                            }
                        },
                        label = { Text("18+", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}
