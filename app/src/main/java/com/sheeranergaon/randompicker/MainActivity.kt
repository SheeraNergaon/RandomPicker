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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var containerGroups: LinearLayout
    private lateinit var btnCreateGroup: Button

    private lateinit var etQuickName: EditText
    private lateinit var containerQuickNames: LinearLayout
    private lateinit var btnStartRaffle: View   // pill at the bottom
    private lateinit var btnLogout: View        // logout pill at the top

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var groupsRef: DatabaseReference
    private var userId: String? = null

    // Data
    data class Group(
        val id: String = "",
        var name: String = "",
        val members: MutableList<String> = mutableListOf()
    )

    private val groups = mutableListOf<Group>()
    private val quickNames = mutableListOf<String>()
    private var selectedGroupId: String? = null  // null = no saved group chosen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Find views ---
        containerGroups = findViewById(R.id.containerGroups)
        btnCreateGroup = findViewById(R.id.btnCreateGroup)

        etQuickName = findViewById(R.id.etQuickName)
        containerQuickNames = findViewById(R.id.containerQuickNames)
        btnStartRaffle = findViewById(R.id.btnStartQuickRaffle)

        // 🔹 Logout pill view (LinearLayout from XML)
        btnLogout = findViewById(R.id.btnLogout)

        // --- Firebase Auth ---
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }
        userId = currentUser.uid

        // --- Firebase DB ref: RandomPicker/users/<uid>/groups ---
        groupsRef = FirebaseDatabase.getInstance().reference
            .child("RandomPicker")
            .child("users")
            .child(userId!!)
            .child("groups")

        // Listen for groups from DB
        listenForGroups()

        // Create new group
        btnCreateGroup.setOnClickListener {
            showCreateGroupDialog()
        }

        // 🔹 Logout click
        btnLogout.setOnClickListener {
            onLogoutClicked(it)
        }

        // Random group: inline plus inside etQuickName
        etQuickName.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawables = etQuickName.compoundDrawables
                val drawableEnd = drawables[2] // right drawable
                if (drawableEnd != null) {
                    val drawableWidth = drawableEnd.bounds.width()
                    val touchAreaStart =
                        etQuickName.right - drawableWidth - etQuickName.paddingEnd

                    if (event.rawX >= touchAreaStart) {
                        val name = etQuickName.text.toString().trim()
                        if (name.isNotEmpty()) {
                            quickNames.add(name)
                            etQuickName.text.clear()
                            renderQuickNames()
                        } else {
                            Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
                        }
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        // Start Raffle pill
        btnStartRaffle.setOnClickListener {
            startRaffleBasedOnSelection()
        }
    }

    fun onLogoutClicked(view: View) {
        Toast.makeText(this, "Signing out...", Toast.LENGTH_SHORT).show()

        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                FirebaseAuth.getInstance().signOut()
                goToLogin()
            }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ---------------- FIREBASE LISTENER ----------------

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
                        if (!memberName.isNullOrBlank()) {
                            membersList.add(memberName)
                        }
                    }

                    groups.add(Group(id = id, name = name, members = membersList))
                }

                // If previously selected group was deleted, clear selection
                if (selectedGroupId != null && groups.none { it.id == selectedGroupId }) {
                    selectedGroupId = null
                }

                renderGroups()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load groups: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    // ---------------- GROUPS UI ----------------

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

            // Radio state
            val isSelected = (group.id == selectedGroupId)
            rbSelectGroup.isChecked = isSelected

            // Select this group (radio or click on name)
            val selectThisGroup: () -> Unit = {
                selectedGroupId = group.id
                renderGroups() // refresh radios
            }
            rbSelectGroup.setOnClickListener { selectThisGroup() }
            tvGroupName.setOnClickListener { selectThisGroup() }

            // Long press on group name → Rename / Delete
            tvGroupName.setOnLongClickListener {
                showGroupOptionsDialog(group)
                true
            }

            // Inline plus inside etNewMember
            etNewMember.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val drawables = etNewMember.compoundDrawables
                    val drawableEnd = drawables[2] // right drawable
                    if (drawableEnd != null) {
                        val drawableWidth = drawableEnd.bounds.width()
                        val touchAreaStart =
                            etNewMember.right - drawableWidth - etNewMember.paddingEnd

                        if (event.rawX >= touchAreaStart) {
                            val name = etNewMember.text.toString().trim()
                            if (name.isNotEmpty()) {
                                group.members.add(name)
                                etNewMember.text.clear()
                                saveGroupMembersToFirebase(group)
                            } else {
                                Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
                            }
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }

            // Members list
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

                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = (4 * resources.displayMetrics.density).toInt()
                layoutParams = params

                // Long press to remove member
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
                if (name.isEmpty()) {
                    Toast.makeText(this, "Group name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    createGroupInFirebase(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createGroupInFirebase(name: String) {
        val newRef = groupsRef.push()
        val groupData = mapOf(
            "name" to name,
            "members" to emptyList<String>()
        )
        newRef.setValue(groupData)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to create group", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRemoveMemberDialog(group: Group, memberName: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove member")
            .setMessage("Remove \"$memberName\" from ${group.name}?")
            .setPositiveButton("Remove") { _, _ ->
                group.members.remove(memberName)
                saveGroupMembersToFirebase(group)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveGroupMembersToFirebase(group: Group) {
        if (group.id.isEmpty()) return

        groupsRef.child(group.id).child("members")
            .setValue(group.members)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update members", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- GROUP OPTIONS: RENAME / DELETE ----------

    private fun showGroupOptionsDialog(group: Group) {
        val options = arrayOf("Rename group", "Delete group")

        AlertDialog.Builder(this)
            .setTitle(group.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(group)
                    1 -> showDeleteGroupConfirmDialog(group)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameGroupDialog(group: Group) {
        val editText = EditText(this).apply {
            hint = "New group name"
            setText(group.name)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename group")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                } else if (group.id.isNotEmpty()) {
                    groupsRef.child(group.id).child("name")
                        .setValue(newName)
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to rename group", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteGroupConfirmDialog(group: Group) {
        AlertDialog.Builder(this)
            .setTitle("Delete group")
            .setMessage("Delete \"${group.name}\" and all its members?")
            .setPositiveButton("Delete") { _, _ ->
                if (group.id.isNotEmpty()) {
                    groupsRef.child(group.id).removeValue()
                        .addOnSuccessListener {
                            if (selectedGroupId == group.id) {
                                selectedGroupId = null
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to delete group", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- RANDOM GROUP (NOT SAVED) ----------------

    private fun renderQuickNames() {
        containerQuickNames.removeAllViews()
        val margin = (8 * resources.displayMetrics.density).toInt()

        quickNames.forEach { name ->
            val tv = TextView(this).apply {
                text = name
                textSize = 14f
                setPadding(24, 12, 24, 12)
                setBackgroundResource(R.drawable.rounded_input)

                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
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

    // ---------------- START RAFFLE ----------------

    private fun startRaffleBasedOnSelection() {
        val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }

        when {
            selectedGroup != null -> {
                if (selectedGroup.members.isEmpty()) {
                    Toast.makeText(this, "Selected group has no members", Toast.LENGTH_SHORT).show()
                } else {
                    startRaffleWithNames(selectedGroup.members)
                }
            }

            quickNames.isNotEmpty() -> {
                startRaffleWithNames(quickNames)
            }

            else -> {
                Toast.makeText(
                    this,
                    "Select a saved group or add names to the random group first",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startRaffleWithNames(names: List<String>) {
        val intent = Intent(this, RaffleActivity::class.java)
        intent.putExtra("EXTRA_NAMES", names.toTypedArray())
        startActivity(intent)
    }
}
