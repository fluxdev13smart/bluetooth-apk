package com.example.btchat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.btchat.core.Engine
import com.example.btchat.core.Link
import com.example.btchat.core.Peer
import kotlinx.coroutines.delay

@Composable
fun BtRoot(perms: Perms, act: Actions) {
    BtTheme {
        val st by Engine.state.collectAsState()
        val lastPeer = remember { arrayOfNulls<Peer>(1) }
        (st.link as? Link.Connected)?.let { lastPeer[0] = it.peer }

        Box(Modifier.fillMaxSize().background(P.bg)) {
            AnimatedContent(
                targetState = st.link is Link.Connected,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(smooth()) { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally(smooth()) { -it / 5 } + fadeOut())
                    } else {
                        (slideInHorizontally(smooth()) { -it / 5 } + fadeIn()) togetherWith
                            (slideOutHorizontally(smooth()) { it / 3 } + fadeOut())
                    }
                },
                label = "screens"
            ) { connected ->
                if (connected) SessionScreen(st, lastPeer[0] ?: Peer("", ""), act) else HomeScreen(st, perms, act)
            }
            AnimatedVisibility(
                visible = Nav.saved,
                enter = slideInVertically(smooth()) { it / 6 } + fadeIn(),
                exit = slideOutVertically(quick()) { it / 6 } + fadeOut()
            ) { SavedScreen(st.link is Link.Connected) }
            NoticeBar(st.notice)
        }
    }
}

@Composable
private fun NoticeBar(notice: String?) {
    var last by remember { mutableStateOf("") }
    LaunchedEffect(notice) {
        if (notice != null) {
            last = notice
            delay(4500)
            Engine.dismissNotice()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = notice != null,
            enter = slideInVertically(bouncy()) { -it } + fadeIn(),
            exit = slideOutVertically(quick()) { -it } + fadeOut()
        ) {
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(P.text)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) { BtText(last, Ty.small, P.bg) }
        }
    }
}
