package com.sheeranergaon.randompicker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class Group(
    val id: String = "",
    var name: String = "",
    val members: MutableList<String> = mutableListOf()
)

class MainActivity : AppCompatActivity() {

    private val MAX_FREE_GROUPS = 4
    private val MAX_QUICK_NAMES = 3
    private val PREFS_NAME = "AppStats"
    private val KEY_RAFFLE_COUNT = "raffle_count"
    private val COLOR_THEME_ORANGE = "#BF360C"

    companion object {
        var isPremiumSession = false
    }

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var rewardedAd: RewardedAd? = null
    private var isQuickLimitUnlocked = false
    private val REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917"

    private lateinit var containerGroups: LinearLayout
    private lateinit var btnCreateGroup: Button
    private lateinit var etQuickName: EditText
    private lateinit var containerQuickNames: LinearLayout
    private lateinit var btnStartRaffle: View
    private lateinit var btnLogout: View

    private lateinit var auth: FirebaseAuth
    private var groupsRef: DatabaseReference? = null
    private var userId: String? = null

    private val groups = mutableListOf<Group>()
    private val quickNames = mutableListOf<String>()
    private var selectedGroupId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        MobileAds.initialize(this) {}
        loadRewardedAd()

        containerGroups = findViewById(R.id.containerGroups)
        btnCreateGroup = findViewById(R.id.btnCreateGroup)
        etQuickName = findViewById(R.id.etQuickName)
        containerQuickNames = findViewById(R.id.containerQuickNames)
        btnStartRaffle = findViewById(R.id.btnStartQuickRaffle)
        btnLogout = findViewById(R.id.btnLogout)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            goToLogin()
            return
        }

        userId = currentUser.uid
        groupsRef = FirebaseDatabase.getInstance().reference
            .child("RandomPicker")
            .child("users")
            .child(userId!!)
            .child("groups")

        listenForGroups()

        btnCreateGroup.setOnClickListener {
            if (isPremiumSession || groups.size < MAX_FREE_GROUPS) {
                showCreateGroupDialog()
            } else {
                showPremiumPopup()
            }
        }

        btnCreateGroup.setOnLongClickListener {
            isPremiumSession = !isPremiumSession
            Toast.makeText(this, "Premium: $isPremiumSession", Toast.LENGTH_SHORT).show()
            true
        }

        etQuickName.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = etQuickName.compoundDrawables[2]
                if (drawable != null && event.rawX >= (etQuickName.right - drawable.bounds.width() - etQuickName.paddingEnd)) {
                    val canAdd = isPremiumSession || isQuickLimitUnlocked || quickNames.size < MAX_QUICK_NAMES
                    if (canAdd) {
                        val name = etQuickName.text.toString().trim()
                        if (name.isNotEmpty()) {
                            quickNames.add(name)
                            etQuickName.text.clear()
                            renderQuickNames()
                        }
                    } else {
                        showWatchAdDialog()
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }

        btnStartRaffle.setOnClickListener { startRaffleLogic() }
        btnLogout.setOnClickListener { onLogoutClicked() }
        // A normal click logs you out, but a LONG PRESS triggers the crash demo
        btnLogout.setOnLongClickListener {
            throw RuntimeException("Secret Logout Crash: Success!")
            true
        }
        showNewVersionPopup()
    }



    private fun listenForGroups() {
        groupsRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                groups.clear()
                for (groupSnap in snapshot.children) {
                    val id = groupSnap.key ?: continue
                    val name = groupSnap.child("name").getValue(String::class.java) ?: "Unnamed"
                    val membersList = mutableListOf<String>()
                    val membersSnap = groupSnap.child("members")
                    if (membersSnap.exists()) {
                        for (memberChild in membersSnap.children) {
                            val memberName = memberChild.getValue(String::class.java)
                            if (!memberName.isNullOrBlank()) membersList.add(memberName)
                        }
                    }
                    groups.add(Group(id = id, name = name, members = membersList))
                }
                if (selectedGroupId == null && groups.isNotEmpty()) selectedGroupId = groups[0].id
                renderGroups()
            }
            override fun onCancelled(error: DatabaseError) { Log.e("FirebaseData", "Database Error: ${error.message}") }
        })
    }

    private fun createGroupInFirebase(name: String) {
        val newRef = groupsRef?.push()
        if (newRef != null) {
            val groupData = mapOf("name" to name, "members" to emptyList<String>())
            newRef.setValue(groupData).addOnSuccessListener {
                firebaseAnalytics.logEvent("group_created") {
                    param("group_name", name)
                }
            }
        }
    }

    private fun saveGroupMembersToFirebase(group: Group) {
        if (group.id.isNotEmpty()) groupsRef?.child(group.id)?.child("members")?.setValue(group.members)
    }

    private fun renderGroups() {
        containerGroups.removeAllViews()
        val inflater = LayoutInflater.from(this)
        groups.forEach { group ->
            val groupView = inflater.inflate(R.layout.item_group, containerGroups, false)
            val rbSelectGroup = groupView.findViewById<RadioButton>(R.id.rbSelectGroup)
            val tvGroupName = groupView.findViewById<TextView>(R.id.tvGroupName)
            val containerMembers = groupView.findViewById<LinearLayout>(R.id.containerMembers)
            val etNewMember = groupView.findViewById<EditText>(R.id.etNewMember)

            tvGroupName.text = group.name
            rbSelectGroup.isChecked = (group.id == selectedGroupId)

            val selectThisGroup: () -> Unit = {
                selectedGroupId = group.id
                renderGroups()
            }

            rbSelectGroup.setOnClickListener { selectThisGroup() }
            tvGroupName.setOnClickListener { selectThisGroup() }
            tvGroupName.setOnLongClickListener {
                showGroupOptionsDialog(group)
                true
            }

            etNewMember.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val drawable = etNewMember.compoundDrawables[2]
                    if (drawable != null && event.rawX >= (etNewMember.right - drawable.bounds.width() - etNewMember.paddingEnd)) {
                        val name = etNewMember.text.toString().trim()
                        if (name.isNotEmpty()) {
                            group.members.add(name)
                            etNewMember.text.clear()
                            saveGroupMembersToFirebase(group)
                        }
                        return@setOnTouchListener true
                    }
                }
                false
            }
            renderGroupMembers(containerMembers, group)
            containerGroups.addView(groupView)
        }
    }

    private fun renderGroupMembers(container: LinearLayout, group: Group) {
        container.removeAllViews()
        group.members.forEach { memberName ->
            val row = TextView(this).apply {
                text = memberName
                textSize = 16f
                setPadding(16, 12, 16, 12)
                setBackgroundResource(R.drawable.rounded_input)
                val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.topMargin = (4 * resources.displayMetrics.density).toInt()
                layoutParams = params
                setOnLongClickListener {
                    showRemoveMemberDialog(group, memberName)
                    true
                }
            }
            container.addView(row)
        }
    }

    private fun showCreateGroupDialog() {
        val editText = EditText(this).apply { hint = "Group name" }
        AlertDialog.Builder(this)
            .setTitle("Create new group")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) createGroupInFirebase(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderQuickNames() {
        containerQuickNames.removeAllViews()
        val margin = (8 * resources.displayMetrics.density).toInt()
        quickNames.forEach { name ->
            val tv = TextView(this).apply {
                text = name
                textSize = 14f
                setPadding(24, 12, 24, 12)
                setBackgroundResource(R.drawable.rounded_input)
                val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.rightMargin = margin
                layoutParams = params
                setOnLongClickListener {
                    quickNames.remove(name)
                    renderQuickNames()
                    true
                }
            }
            containerQuickNames.addView(tv)
        }
    }

    private fun loadRewardedAd() {
        if (isPremiumSession) return
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, REWARDED_AD_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() { loadRewardedAd() }
                }
            }
        })
    }

    private fun showWatchAdDialog() {
        AlertDialog.Builder(this)
            .setTitle("Limit Reached")
            .setMessage("Watch a video or unlock Premium for unlimited names!")
            .setPositiveButton("Watch Video") { _, _ -> showRewardedAd() }
            .setNeutralButton("Get Premium") { _, _ -> showPremiumPopup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRewardedAd() {
        if (rewardedAd != null) {
            rewardedAd?.show(this) {
                isQuickLimitUnlocked = true
                Toast.makeText(this, "Limit removed for this list!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Ad loading...", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    private fun showPremiumPopup() {
        AlertDialog.Builder(this)
            .setTitle("Go Premium 🚀")
            .setMessage("Unlocks:\n• Unlimited Groups\n• Unlimited Names\n• NO ADS")
            .setPositiveButton("Get Premium (25₪)") { _, _ -> simulatePurchase() }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    private fun simulatePurchase() {
        isPremiumSession = true
        rewardedAd = null
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LEVEL_UP) {
            param(FirebaseAnalytics.Param.LEVEL_NAME, "premium_unlocked")
        }
        Toast.makeText(this, "Welcome to Premium!", Toast.LENGTH_LONG).show()
        showCreateGroupDialog()
    }

    private fun onLogoutClicked() {
        AlertDialog.Builder(this).setTitle("Sign Out").setPositiveButton("Yes") { _, _ ->
            AuthUI.getInstance().signOut(this).addOnCompleteListener { goToLogin() }
        }.setNegativeButton("No", null).show()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun startRaffleLogic() {
        val namesToRaffle: List<String>?
        val raffleSource: String

        if (quickNames.isNotEmpty()) {
            namesToRaffle = quickNames
            raffleSource = "quick_list"
        } else if (selectedGroupId != null) {
            val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
            namesToRaffle = selectedGroup?.members
            raffleSource = "saved_group"
        } else {
            namesToRaffle = null
            raffleSource = "none"
        }

        if (namesToRaffle.isNullOrEmpty()) {
            Toast.makeText(this, "Please add random names or select a group", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseAnalytics.logEvent("perform_raffle") {
            param("source", raffleSource)
            param("item_count", namesToRaffle.size.toLong())
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val totalRaffles = prefs.getInt(KEY_RAFFLE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_RAFFLE_COUNT, totalRaffles).apply()

        when {
            totalRaffles % 3 == 0 -> showMandatoryShareDialog(namesToRaffle)
            totalRaffles % 4 == 0 -> showMandatoryRateDialog(namesToRaffle)
            else -> startRaffleActivity(namesToRaffle)
        }
    }

    private fun showMandatoryShareDialog(names: List<String>) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_share, null)
        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<Button>(R.id.btnSure).setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out this Random Picker app!")
            }, "Share via"))
            dialog.dismiss()
            startRaffleActivity(names)
        }
        view.findViewById<Button>(R.id.btnNoThanks).setOnClickListener {
            dialog.dismiss()
            startRaffleActivity(names)
        }
        dialog.show()
    }

    private fun showMandatoryRateDialog(names: List<String>) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_rate, null)
        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<Button>(R.id.btnContinue).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
            catch (e: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
            dialog.dismiss()
            startRaffleActivity(names)
        }
        view.findViewById<TextView>(R.id.btnCancelRate).setOnClickListener {
            dialog.dismiss()
            startRaffleActivity(names)
        }
        dialog.show()
    }

    private fun startRaffleActivity(names: List<String>) {
        val intent = Intent(this, RaffleActivity::class.java)
        intent.putExtra("EXTRA_NAMES", names.toTypedArray())
        intent.putExtra("IS_PREMIUM", isPremiumSession)
        startActivity(intent)
    }

    private fun showGroupOptionsDialog(group: Group) {
        val options = arrayOf("Rename group", "Delete group")
        val builder = AlertDialog.Builder(this)
        builder.setItems(options) { _, which -> if (which == 0) showRenameGroupDialog(group) else showDeleteGroupConfirmDialog(group) }
        builder.show()
    }

    private fun showRenameGroupDialog(group: Group) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 20, 60, 20) }
        val editText = EditText(this).apply { setText(group.name); background = resources.getDrawable(R.drawable.rounded_input); setPadding(40, 40, 40, 40) }
        container.addView(editText)
        AlertDialog.Builder(this).setTitle("Rename Group").setView(container)
            .setPositiveButton("SAVE") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) groupsRef?.child(group.id)?.child("name")?.setValue(newName)
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun showDeleteGroupConfirmDialog(group: Group) {
        AlertDialog.Builder(this).setTitle("Delete Group")
            .setMessage("Are you sure you want to delete '${group.name}'?")
            .setPositiveButton("DELETE") { _, _ -> groupsRef?.child(group.id)?.removeValue() }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun showRemoveMemberDialog(group: Group, memberName: String) {
        AlertDialog.Builder(this).setTitle("Remove Member")
            .setMessage("Remove '$memberName' from the list?")
            .setPositiveButton("REMOVE") { _, _ ->
                group.members.remove(memberName)
                saveGroupMembersToFirebase(group)
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun showNewVersionPopup() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasSeen = prefs.getBoolean("seen_fake_update_v1", false)
        if (!hasSeen) {
            AlertDialog.Builder(this).setTitle("What’s new?")
                .setMessage("○ Save more than 4 groups unlimited!\n\n○ Change background color with premium!")
                .setPositiveButton("Got it!") { _, _ -> prefs.edit().putBoolean("seen_fake_update_v1", true).apply() }
                .show()
        }
    }
}