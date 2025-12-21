package com.sheeranergaon.randompicker

import android.content.Intent
import android.os.Bundle
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    // FREEMIUM LIMIT SETTINGS
    private val MAX_FREE_GROUPS = 4
    private val MAX_QUICK_NAMES = 3

    companion object {
        var isPremiumSession = false
    }

    // AD MOB
    private var rewardedAd: RewardedAd? = null
    private var isQuickLimitUnlocked = false
    private val REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917" // Test ID

    private lateinit var containerGroups: LinearLayout
    private lateinit var btnCreateGroup: Button
    private lateinit var etQuickName: EditText
    private lateinit var containerQuickNames: LinearLayout
    private lateinit var btnStartRaffle: View
    private lateinit var btnLogout: View

    private lateinit var auth: FirebaseAuth
    private lateinit var groupsRef: DatabaseReference
    private var userId: String? = null

    data class Group(val id: String = "", var name: String = "", val members: MutableList<String> = mutableListOf())
    private val groups = mutableListOf<Group>()
    private val quickNames = mutableListOf<String>()
    private var selectedGroupId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        if (currentUser == null) { goToLogin(); return }
        userId = currentUser.uid

        groupsRef = FirebaseDatabase.getInstance().reference.child("RandomPicker").child("users").child(userId!!).child("groups")
        listenForGroups()

        btnCreateGroup.setOnClickListener {
            if (isPremiumSession || groups.size < MAX_FREE_GROUPS) {
                showCreateGroupDialog()
            } else {
                showPremiumPopup()
            }
        }

        // SIMULATION TOGGLE: Long press to reset Premium
        btnCreateGroup.setOnLongClickListener {
            isPremiumSession = !isPremiumSession
            if (isPremiumSession) {
                rewardedAd = null
                Toast.makeText(this, "PREMIUM UNLOCKED (Simulation)", Toast.LENGTH_SHORT).show()
            } else {
                isQuickLimitUnlocked = false
                loadRewardedAd()
                Toast.makeText(this, "PREMIUM REMOVED (Simulation)", Toast.LENGTH_SHORT).show()
            }
            true
        }

        etQuickName.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawables = etQuickName.compoundDrawables[2]
                if (drawables != null && event.rawX >= (etQuickName.right - drawables.bounds.width() - etQuickName.paddingEnd)) {
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

        btnStartRaffle.setOnClickListener { startRaffleBasedOnSelection() }
        btnLogout.setOnClickListener { onLogoutClicked() }
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
        Toast.makeText(this, "Welcome to Premium! All features unlocked.", Toast.LENGTH_LONG).show()
        showCreateGroupDialog()
    }

    private fun startRaffleWithNames(names: List<String>) {
        val intent = Intent(this, RaffleActivity::class.java)
        intent.putExtra("EXTRA_NAMES", names.toTypedArray())
        // No longer strictly needed but good practice to keep the intent extra
        intent.putExtra("IS_PREMIUM", isPremiumSession)
        startActivity(intent)
    }

    private fun listenForGroups() {
        groupsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                groups.clear()
                for (groupSnap in snapshot.children) {
                    val id = groupSnap.key ?: continue
                    val name = groupSnap.child("name").getValue(String::class.java) ?: ""
                    val membersList = mutableListOf<String>()
                    val membersSnap = groupSnap.child("members")
                    for (memberChild in membersSnap.children) {
                        val memberName = memberChild.getValue(String::class.java)
                        if (!memberName.isNullOrBlank()) membersList.add(memberName)
                    }
                    groups.add(Group(id = id, name = name, members = membersList))
                }
                renderGroups()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
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

            etNewMember.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val drawables = etNewMember.compoundDrawables[2]
                    if (drawables != null && event.rawX >= (etNewMember.right - drawables.bounds.width() - etNewMember.paddingEnd)) {
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

    private fun createGroupInFirebase(name: String) {
        val newRef = groupsRef.push()
        newRef.setValue(mapOf("name" to name, "members" to emptyList<String>()))
    }

    private fun saveGroupMembersToFirebase(group: Group) {
        if (group.id.isNotEmpty()) groupsRef.child(group.id).child("members").setValue(group.members)
    }

    private fun showGroupOptionsDialog(group: Group) {
        AlertDialog.Builder(this)
            .setTitle(group.name)
            .setItems(arrayOf("Rename group", "Delete group")) { _, which ->
                if (which == 0) showRenameGroupDialog(group) else showDeleteGroupConfirmDialog(group)
            }
            .show()
    }

    private fun showRenameGroupDialog(group: Group) {
        val editText = EditText(this).apply { setText(group.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename group")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) groupsRef.child(group.id).child("name").setValue(newName)
            }
            .show()
    }

    private fun showDeleteGroupConfirmDialog(group: Group) {
        AlertDialog.Builder(this)
            .setTitle("Delete group")
            .setMessage("Are you sure?")
            .setPositiveButton("Delete") { _, _ -> groupsRef.child(group.id).removeValue() }
            .show()
    }

    private fun showRemoveMemberDialog(group: Group, memberName: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove member")
            .setPositiveButton("Remove") { _, _ ->
                group.members.remove(memberName)
                saveGroupMembersToFirebase(group)
            }
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

    private fun startRaffleBasedOnSelection() {
        val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
        when {
            selectedGroup != null -> {
                if (selectedGroup.members.isEmpty()) Toast.makeText(this, "Group empty", Toast.LENGTH_SHORT).show()
                else startRaffleWithNames(selectedGroup.members)
            }
            quickNames.isNotEmpty() -> startRaffleWithNames(quickNames)
            else -> Toast.makeText(this, "Select group or add names", Toast.LENGTH_SHORT).show()
        }
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
}