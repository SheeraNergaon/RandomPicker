package com.sheeranergaon.randompicker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlin.math.max
import kotlin.random.Random

class RaffleActivity : AppCompatActivity() {

    private lateinit var bubbleContainer: LinearLayout
    private lateinit var tvWinner: TextView
    private lateinit var btnBackHome: View

    private val bubbles = mutableListOf<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var winnerChosen = false
    private var isPremium = false

    // Maximum scale factor used in animation (1.15f in animateBubbleCrazy)
    private val MAX_SCALE = 1.15f
    // A small buffer to keep bubbles from touching the exact edge of the container
    private val EDGE_BUFFER_PX = 20f

    private var mInterstitialAd: InterstitialAd? = null

    private val bubbleBackgrounds = listOf(
        R.drawable.bubble_background_blue,
        R.drawable.bubble_background_pink,
        R.drawable.bubble_background_green,
        R.drawable.bubble_background_purple,
        R.drawable.bubble_background_orange
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raffle)

        isPremium = MainActivity.isPremiumSession

        bubbleContainer = findViewById(R.id.bubbleContainer)
        tvWinner = findViewById(R.id.tvWinner)
        btnBackHome = findViewById(R.id.btnBackHome)

        if (!isPremium) {
            loadInterstitialAd()
        }

        btnBackHome.setOnClickListener {
            showAdAndGoHome()
        }

        val names = intent.getStringArrayExtra("EXTRA_NAMES")?.toList() ?: emptyList()
        if (names.isEmpty()) {
            finish()
            return
        }

        showNameBubbles(names)
        // Wait for layout before starting animation so we know dimensions
        bubbleContainer.post {
            if (!isFinishing) {
                startCrazyAnimation()
            }
        }

        val delayMillis = Random.nextLong(3000L, 5000L)
        handler.postDelayed({ chooseWinner() }, delayMillis)
    }

    // ... [loadInterstitialAd, showAdAndGoHome, navigateToMainActivity remained unchanged] ...
    private fun loadInterstitialAd() {
        if (isPremium) return
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                    mInterstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() { navigateToMainActivity() }
                        override fun onAdFailedToShowFullScreenContent(p0: AdError) { navigateToMainActivity() }
                    }
                }
            })
    }

    private fun showAdAndGoHome() {
        if (isPremium || mInterstitialAd == null) {
            navigateToMainActivity()
        } else {
            mInterstitialAd?.show(this)
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun showNameBubbles(names: List<String>) {
        bubbleContainer.removeAllViews()
        bubbles.clear()

        val density = resources.displayMetrics.density
        // Reduce margin slightly so they don't start too spread out
        val margin = (4 * density).toInt()

        names.forEach { name ->
            val tv = TextView(this).apply {
                text = name
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(32, 16, 32, 16)
                background = getDrawable(bubbleBackgrounds.random())
                // Ensure they don't span full width initially
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(margin, margin, margin, margin)
                    gravity = Gravity.CENTER_HORIZONTAL // Center them initially
                }
            }

            bubbleContainer.addView(tv)
            bubbles.add(tv)
        }
    }

    // --- UPDATED ANIMATION LOGIC ---

    private fun startCrazyAnimation() {
        if (bubbles.isEmpty() || bubbleContainer.width == 0) return

        // Get actual parent dimensions, accounting for padding
        val parentW = bubbleContainer.width - bubbleContainer.paddingLeft - bubbleContainer.paddingRight
        val parentH = bubbleContainer.height - bubbleContainer.paddingTop - bubbleContainer.paddingBottom

        bubbles.forEach { bubble ->
            // Start animation loop
            animateBubbleCrazy(bubble, parentW, parentH)
        }
    }

    private fun animateBubbleCrazy(view: View, parentW: Int, parentH: Int) {
        if (winnerChosen) return

        // 1. Calculate safe bounds considering scale and buffer
        // We use MAX_SCALE because the animation might scale up to that size.
        val safeBubbleW = view.width * MAX_SCALE
        val safeBubbleH = view.height * MAX_SCALE

        // Calculate the maximum absolute X and Y coordinates the top-left corner
        // of the bubble can reach within the parent container.
        // Use max(0f, ...) to prevent negative ranges if parent is too small.
        val maxAvailableX = max(0f, parentW - safeBubbleW - EDGE_BUFFER_PX)
        val maxAvailableY = max(0f, parentH - safeBubbleH - EDGE_BUFFER_PX)
        val minAvailableX = EDGE_BUFFER_PX
        val minAvailableY = EDGE_BUFFER_PX

        // 2. Pick a random absolute position within those safe bounds
        // (These are coordinates relative to the parent's top-left corner)
        val randomAbsX = Random.nextFloat() * (maxAvailableX - minAvailableX) + minAvailableX
        val randomAbsY = Random.nextFloat() * (maxAvailableY - minAvailableY) + minAvailableY

        // 3. Convert absolute position to translation offset.
        // translationX = Desired Absolute X - Original Layout X position (view.left)
        // view.left handles the fact that views in LinearLayout start at different Y positions.
        val targetTx = randomAbsX - view.left
        val targetTy = randomAbsY - view.top

        val targetScale = if (Random.nextBoolean()) MAX_SCALE else 0.9f
        val duration = Random.nextLong(500L, 900L)

        view.animate()
            .translationX(targetTx)
            .translationY(targetTy)
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(duration)
            .withEndAction {
                if (!winnerChosen) {
                    // Loop the animation with parent dimensions
                    animateBubbleCrazy(view, parentW, parentH)
                }
            }
            .start()
    }

    private fun chooseWinner() {
        if (winnerChosen || bubbles.isEmpty()) return
        winnerChosen = true

        val winnerIndex = Random.nextInt(bubbles.size)
        val winnerBubble = bubbles[winnerIndex]
        val winnerName = winnerBubble.text.toString()

        // Stop all current animations immediately
        bubbles.forEach { it.animate().cancel() }

        bubbles.forEachIndexed { index, bubble ->
            if (index != winnerIndex) {
                // Fade out losers
                bubble.animate()
                    .alpha(0f)
                    // Reset scale so they don't fade out while huge
                    .scaleX(1f).scaleY(1f)
                    .setDuration(400L)
                    .start()
            }
        }

        // Emphasize winner based on its CURRENT position where it stopped
        winnerBubble.animate()
            .scaleX(1.3f) // Slightly bigger winner scale
            .scaleY(1.3f)
            .alpha(1f)
            // Optional: move winner to center (looks cleaner)
            .translationX(0f)
            .translationY(0f)
            .setDuration(500L)
            .start()

        tvWinner.text = "Winner: $winnerName"
        tvWinner.visibility = View.VISIBLE
        btnBackHome.visibility = View.VISIBLE
    }
}