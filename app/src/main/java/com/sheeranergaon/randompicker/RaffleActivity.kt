package com.sheeranergaon.randompicker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class RaffleActivity : AppCompatActivity() {

    private lateinit var bubbleContainer: LinearLayout
    private lateinit var tvWinner: TextView
    private lateinit var btnBackHome: View

    private val bubbles = mutableListOf<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var winnerChosen = false

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

        bubbleContainer = findViewById(R.id.bubbleContainer)
        tvWinner = findViewById(R.id.tvWinner)
        btnBackHome = findViewById(R.id.btnBackHome)

        btnBackHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        val names = intent.getStringArrayExtra("EXTRA_NAMES")?.toList() ?: emptyList()
        if (names.isEmpty()) {
            finish()
            return
        }

        showNameBubbles(names)

        // Start the "swirl" effect
        startCrazyAnimation()

        // Choose winner after 3–5 seconds
        val delayMillis = Random.nextLong(3000L, 5000L)
        handler.postDelayed({ chooseWinner() }, delayMillis)
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

                // 🌈 Random bubble background here:
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

        // how far they are allowed to roam
        val maxOffsetX = screenWidth * 0.4f      // left/right
        val maxOffsetY = screenHeight * 0.7f     // top → almost bottom

        bubbles.forEach { bubble ->
            // 🔸 give each bubble a random starting offset on screen
            bubble.translationX = (Random.nextFloat() - 0.5f) * maxOffsetX * 2f   // -maxX .. +maxX
            bubble.translationY = Random.nextFloat() * maxOffsetY                 // 0 .. maxY

            animateBubbleCrazy(bubble, maxOffsetX, maxOffsetY)
        }
    }

    private fun animateBubbleCrazy(view: View, maxOffsetX: Float, maxOffsetY: Float) {
        if (winnerChosen) return

        // 🔸 new random target anywhere in allowed area
        val targetX = (Random.nextFloat() - 0.5f) * maxOffsetX * 2f   // -maxX .. +maxX
        val targetY = Random.nextFloat() * maxOffsetY                 // 0 .. maxY

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

        // Stop all animations
        bubbles.forEach { it.animate().cancel() }

        // Fade out others
        bubbles.forEachIndexed { index, bubble ->
            if (index != winnerIndex) {
                bubble.animate()
                    .alpha(0f)
                    .setDuration(400L)
                    .start()
            }
        }

        // Make winner bubble stand out a bit
        winnerBubble.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300L)
            .start()

        // Put winner text at the top
        tvWinner.text = "Winner: $winnerName"
        tvWinner.visibility = View.VISIBLE

        btnBackHome.visibility = View.VISIBLE
    }

}
