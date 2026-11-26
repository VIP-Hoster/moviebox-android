package com.viphoster.moviebox.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.viphoster.moviebox.data.ApiClient
import okhttp3.OkHttpClient

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setLandscapeFullscreen(activity: Activity?, fullscreen: Boolean) {
    val act = activity ?: return
    val window = act.window ?: return
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    title: String,
    fileId: String,
    token: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isFullscreen by remember { mutableStateOf(false) }

    val handleExit: () -> Unit = {
        if (isFullscreen) {
            isFullscreen = false
            setLandscapeFullscreen(activity, false)
        } else {
            setLandscapeFullscreen(activity, false)
            onClose()
        }
    }

    BackHandler { handleExit() }

    val streamUrl = remember(fileId, token) {
        val encodedTok = Uri.encode(token)
        "${ApiClient.BASE_URL}/stream/$fileId?token=$encodedTok"
    }

    val exoPlayer = remember(streamUrl) {
        val authClient: OkHttpClient = ApiClient.okHttp.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(req)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(authClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(
                            context,
                            "Playback error: ${error.errorCodeName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
                setMediaItem(ExoMediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            setLandscapeFullscreen(activity, false)
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (isFullscreen) Modifier else Modifier.systemBarsPadding())
    ) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = handleExit) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        isFullscreen = true
                        setLandscapeFullscreen(activity, true)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Fullscreen", fontSize = 12.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowSubtitleButton(true)
                        setFullscreenButtonClickListener { full ->
                            isFullscreen = full
                            setLandscapeFullscreen(activity, full)
                        }
                    }
                },
                update = { view ->
                    view.setFullscreenButtonState(isFullscreen)
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isFullscreen) {
                IconButton(
                    onClick = {
                        isFullscreen = false
                        setLandscapeFullscreen(activity, false)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
