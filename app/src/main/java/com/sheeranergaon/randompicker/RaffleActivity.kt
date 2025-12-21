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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlin.random.Random

class RaffleActivity : AppCompatActivity() {

    private lateinit var bubbleContainer: LinearLayout
    private lateinit var tvWinner: TextView
    private lateinit var btnBackHome: View

    private val bubbles = mutableListOf<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var winnerChosen = false
    private var isPremium = false

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
        startCrazyAnimation()

        val delayMillis = Random.nextLong(3000L, 5000L)
        handler.postDelayed({ chooseWinner() }, delayMillis)
    }

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
        val margin = (8 * density).toInt()

        names.forEach { name ->
            val tv = TextView(this).apply {
                text = name
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(32, 16, 32, 16)
                background = getDrawable(bubbleBackgrounds.random())
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = margin
                bottomMargin = margin
            }

            bubbleContainer.addView(tv, params)
            bubbles.add(tv)
        }
    }

    private fun startCrazyAnimation() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val maxOffsetX = screenWidth * 0.4f
        val maxOffsetY = screenHeight * 0.7f

        bubbles.forEach { bubble ->
            bubble.translationX = (Random.nextFloat() - 0.5f) * maxOffsetX * 2f
            bubble.translationY = Random.nextFloat() * maxOffsetY
            animateBubbleCrazy(bubble, maxOffsetX, maxOffsetY)
        }
    }

    private fun animateBubbleCrazy(view: View, maxOffsetX: Float, maxOffsetY: Float) {
        if (winnerChosen) return

        val targetX = (Random.nextFloat() - 0.5f) * maxOffsetX * 2f
        val targetY = Random.nextFloat() * maxOffsetY
        val targetScale = if (Random.nextBoolean()) 1.15f else 0.9f
        val duration = Random.nextLong(500L, 900L)

        view.animate()
            .translationX(targetX)
            .translationY(targetY)
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(duration)
            .withEndAction {
                if (!winnerChosen) {
                    animateBubbleCrazy(view, maxOffsetX, maxOffsetY)
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

        bubbles.forEach { it.animate().cancel() }

        bubbles.forEachIndexed { index, bubble ->
            if (index != winnerIndex) {
                bubble.animate()
                    .alpha(0f)
                    .setDuration(400L)
                    .start()
            }
        }

        winnerBubble.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300L)
            .start()

        tvWinner.text = "Winner: $winnerName"
        tvWinner.visibility = View.VISIBLE
        btnBackHome.visibility = View.VISIBLE
    }
}