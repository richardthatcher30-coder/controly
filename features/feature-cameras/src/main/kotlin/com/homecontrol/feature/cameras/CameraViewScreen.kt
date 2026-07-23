package com.homecontrol.feature.cameras

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraViewScreen(
    onBack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.camera?.name ?: "Camera") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                StreamUiState.Loading -> CircularProgressIndicator()
                is StreamUiState.Ready -> RtspPlayer(rtspUrl = state.rtspUrl)
                is StreamUiState.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                    OutlinedButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun RtspPlayer(rtspUrl: String) {
    val context = LocalContext.current
    var playbackError by remember { mutableStateOf<String?>(null) }

    val player = remember {
        // The ONVIF-reported address is already correct (fixed separately in
        // OnvifClient), but this exact camera's RTSP server itself embeds
        // "localhost" as the SDP session's Content-Base for the SETUP/PLAY
        // requests that follow the initial DESCRIBE — a firmware bug one
        // level deeper than the ONVIF response. A custom SocketFactory is
        // the one hook Media3 exposes into every socket the RTSP client
        // opens, letting every one of those get silently redirected to the
        // camera's real address regardless of which stage of the RTSP
        // handshake asked for "localhost".
        val realHost = Uri.parse(rtspUrl).host
        val socketFactory = if (realHost != null) LocalhostRedirectingSocketFactory(realHost) else SocketFactory.getDefault()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(RtspMediaSource.Factory().setSocketFactory(socketFactory))
            .build()
            .apply {
                // Without this, a playback-level failure (as opposed to the
                // ONVIF resolution step, which already has its own error
                // state) just leaves a frozen black surface with nothing
                // telling the user why — exactly what happened with this
                // camera's localhost-URI firmware bug before it was caught.
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = error.cause?.message ?: error.message
                    }
                })
                setMediaItem(MediaItem.fromUri(Uri.parse(rtspUrl)))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val error = playbackError
    if (error != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Video playback failed: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
        }
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                }
            },
        )
    }
}

/** Redirects any connection aimed at localhost/127.0.0.1 to [realHost] instead — see the comment where this is constructed for why that's needed. */
private class LocalhostRedirectingSocketFactory(private val realHost: String) : SocketFactory() {

    override fun createSocket(): Socket {
        android.util.Log.d("CameraViewScreen", "SocketFactory.createSocket() [no-arg]")
        return Socket()
    }

    override fun createSocket(host: String, port: Int): Socket {
        android.util.Log.d("CameraViewScreen", "SocketFactory.createSocket(host=$host, port=$port) -> ${rewrite(host)}")
        return Socket(rewrite(host), port)
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        android.util.Log.d("CameraViewScreen", "SocketFactory.createSocket(host=$host, port=$port, local) -> ${rewrite(host)}")
        return Socket(rewrite(host), port, localHost, localPort)
    }

    override fun createSocket(address: InetAddress, port: Int): Socket {
        android.util.Log.d("CameraViewScreen", "SocketFactory.createSocket(address=$address, port=$port)")
        return Socket(address, port)
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        android.util.Log.d("CameraViewScreen", "SocketFactory.createSocket(address=$address, port=$port, local)")
        return Socket(address, port, localAddress, localPort)
    }

    // The camera's SDP actually has an *empty* host (e.g. "rtsp://:554/..."),
    // confirmed via logging — Java's Socket/InetAddress silently treats an
    // empty host as the loopback address, which is why the error reported
    // "localhost/127.0.0.1" even though the literal string was never that.
    private fun rewrite(host: String): String =
        if (host.isEmpty() || host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") realHost else host
}
