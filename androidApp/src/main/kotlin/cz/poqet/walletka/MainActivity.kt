package cz.poqet.walletka

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.arkivanov.decompose.defaultComponentContext
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.data.WalletConfig
import cz.poqet.walletka.platform.ActivityBridge
import cz.poqet.walletka.platform.createRootComponent
import cz.poqet.walletka.ui.RootContent

class MainActivity : ComponentActivity() {

    private val activityBridge = ActivityBridge()
    private lateinit var root: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        activityBridge.attach(this)

        root = createRootComponent(
            componentContext = defaultComponentContext(),
            context = this,
            config = WalletConfig(
                googleIssuerId = BuildConfig.GOOGLE_ISSUER_ID,
                googleIssuerEmail = BuildConfig.GOOGLE_ISSUER_EMAIL,
            ),
            activityBridge = activityBridge,
        )
        // On recreation the shared image is already part of the restored navigation state.
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            RootContent(root)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Deprecated("PayClient.savePasses reports through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!activityBridge.onActivityResult(requestCode, resultCode, data)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        activityBridge.detach(this)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        uri?.let { root.onImageReceived(it.toString()) }
    }
}
