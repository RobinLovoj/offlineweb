package com.lovoj.androidoffline.Offlinewebview

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.lovoj.androidoffline.LoginActivity
import com.lovoj.androidoffline.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import com.airbnb.lottie.LottieAnimationView

class OnboardingActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var dotsLayout: LinearLayout
    private lateinit var nextButton: ImageButton
    private lateinit var prefs: SharedPreferences
    private val slides = listOf(
        OnboardingSlide("lotf.json", "Enjoy Seamless Experience In 3D Offline Mode", "3D Mode Activated"),
        OnboardingSlide("lots.json", "Experience Smooth 3D Performance, Even Offline", "Immerse in 3D — No Internet Needed"),
        OnboardingSlide("lotth.json", "Seamless 3D Interaction, Anytime Anywhere", "Offline 3D Experience, Without Limits"),
        OnboardingSlide("lotfour.json", "Fast & Secure", "Your data stays on your device. Enjoy a fast, private experience.")
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        prefs = getSharedPreferences("offlineweb_prefs", MODE_PRIVATE)
        viewPager = findViewById(R.id.onboardingViewPager)
        dotsLayout = findViewById(R.id.dotsLayout)
        nextButton = findViewById(R.id.getStartedButton)

        viewPager.adapter = OnboardingAdapter(slides)
        addDots(0)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                addDots(position)
            }
        })

        nextButton.setOnClickListener {
            val pos = viewPager.currentItem
            if (pos < slides.lastIndex) {
                viewPager.currentItem = pos + 1
            } else {
                prefs.edit().putBoolean("onboarding_seen", true).apply()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun addDots(position: Int) {
        dotsLayout.removeAllViews()
        for (i in slides.indices) {
            val dot = com.airbnb.lottie.LottieAnimationView(this)
            dot.setImageDrawable(ContextCompat.getDrawable(this,
                if (i == position) R.drawable.onboarding_dot_active else R.drawable.onboarding_dot_inactive))
            val params = LinearLayout.LayoutParams(24, 24)
            params.setMargins(8, 0, 8, 0)
            dot.layoutParams = params
            dotsLayout.addView(dot)
        }
    }
}

data class OnboardingSlide(val lottieFile: String, val title: String, val desc: String)

class OnboardingAdapter(private val slides: List<OnboardingSlide>) : RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_slide, parent, false)
        return SlideViewHolder(view)
    }
    override fun getItemCount() = slides.size
    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) = holder.bind(slides[position])
    class SlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(slide: OnboardingSlide) {
            val lottieView = itemView.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.slideLottie)
            lottieView.setAnimation(slide.lottieFile)
            lottieView.playAnimation()
            itemView.findViewById<TextView>(R.id.slideTitle).text = slide.title
            itemView.findViewById<TextView>(R.id.slideDesc).text = slide.desc
        }
    }
} 