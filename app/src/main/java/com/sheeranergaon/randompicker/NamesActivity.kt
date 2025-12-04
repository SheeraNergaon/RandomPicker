package com.sheeranergaon.randompicker

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NamesActivity : AppCompatActivity() {

    private lateinit var containerNames: LinearLayout
    private lateinit var btnStartRaffle: Button
    private val nameFields = mutableListOf<EditText>()
    private var count: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_names)

        containerNames = findViewById(R.id.containerNames)
        btnStartRaffle = findViewById(R.id.btnStartRaffle)

        count = intent.getIntExtra("EXTRA_COUNT", 0)

        if (count <= 0) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        createNameFields(count)

        btnStartRaffle.setOnClickListener {
            val names = nameFields.map { it.text.toString().trim() }
                .filter { it.isNotEmpty() }

            if (names.size < 1) {
                Toast.makeText(this, "Enter at least one name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, RaffleActivity::class.java)
            intent.putExtra("EXTRA_NAMES", names.toTypedArray())
            startActivity(intent)
        }
    }

    private fun createNameFields(count: Int) {
        nameFields.clear()
        containerNames.removeAllViews()

        val margin = (8 * resources.displayMetrics.density).toInt()

        for (i in 1..count) {
            val et = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { lp ->
                    (lp as LinearLayout.LayoutParams).setMargins(0, margin, 0, margin)
                }
                hint = "Name $i"
            }
            containerNames.addView(et)
            nameFields.add(et)
        }
    }
}
