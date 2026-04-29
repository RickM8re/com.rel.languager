package com.rel.languager

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.rel.languager.Constants.PREF_APP_LANGUAGE_MAP
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class ActivityMain : AppCompatActivity() {
    private var pref: SharedPreferences? = null

    private lateinit var appListRecyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noAppsText: TextView
    private lateinit var saveButton: Button
    private val enabledApps = mutableListOf<ApplicationInfo>()
    private val languageMappings = mutableMapOf<String, String>()
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var hasUnsavedChanges = false

    private var mXposedService: XposedService? = null

    private val serviceListener = object : XposedServiceHelper.OnServiceListener {

        // 框架绑定成功时触发
        override fun onServiceBind(service: XposedService) {
            mXposedService = service
            pref = service.getRemotePreferences("SHARED_PREF_FILE_NAME")
            alertDialog.dismiss()
        }

        // 框架服务意外断开/死亡时触发
        override fun onServiceDied(service: XposedService) {
            mXposedService = null
        }
    }

    lateinit var alertDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        alertDialog = AlertDialog.Builder(this)
            .setMessage(R.string.module_not_enabled)
            .setPositiveButton(R.string.close) { _, _ -> finish() }
            .setCancelable(false).create()
        XposedServiceHelper.registerListener(serviceListener)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (pref == null) {
            try {
                alertDialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        initializeViews()
        setupListeners()
        loadLanguageMappings()
        loadEnabledApps()

        setupBackPressHandling()
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges) {
                    AlertDialog.Builder(this@ActivityMain)
                        .setTitle(R.string.unsaved_changes_title)
                        .setMessage(R.string.unsaved_changes_message)
                        .setPositiveButton(R.string.save_and_exit) { _, _ ->
                            saveLanguageMappings()
                            finish()
                        }
                        .setNegativeButton(R.string.discard_and_exit) { _, _ ->
                            finish()
                        }
                        .setNeutralButton(R.string.cancel, null)
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    private fun initializeViews() {
        appListRecyclerView = findViewById(R.id.app_list)
        searchView = findViewById(R.id.search_view)
        loadingProgress = findViewById(R.id.loading_progress)
        noAppsText = findViewById(R.id.no_apps_text)
        saveButton = findViewById(R.id.save_button)

        appListRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        searchView.apply {
            isSubmitButtonEnabled = false
            isFocusable = true
            isIconified = false
            clearFocus()

            val searchText = findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            searchText?.apply {
                setTextColor(ContextCompat.getColor(this@ActivityMain, android.R.color.white))
                setHintTextColor(ContextCompat.getColor(this@ActivityMain, R.color.teal_200))
                try {
                    val cursorDrawableField = TextView::class.java.getDeclaredField("mCursorDrawableRes")
                    cursorDrawableField.isAccessible = true
                    cursorDrawableField.set(this, R.drawable.white_cursor)
                } catch (e: Exception) {
                }
            }

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    filterApps(query)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterApps(newText)
                    return true
                }
            })
        }

        saveButton.setOnClickListener {
            saveLanguageMappings()
        }
    }

    private fun loadLanguageMappings() {
        languageMappings.clear()
        pref?.let { preferences ->
            val mappings = LanguageUtils.getAllLanguageMappings(preferences)
            languageMappings.putAll(mappings)
        }

        hasUnsavedChanges = false
    }

    private fun saveLanguageMappings() {
        try {
            val addingScope = mutableListOf<String>()
            pref?.let { preferences ->
                preferences.edit {

                    val allPrefs = preferences.all
                    for (key in allPrefs.keys) {
                        if (key == PREF_APP_LANGUAGE_MAP) {
                            remove(key)
                        }
                    }

                    for ((packageName, languageCode) in languageMappings) {
                        if (languageCode != Constants.DEFAULT_LANGUAGE) {
                            putString(packageName, languageCode)
                            addingScope.add(packageName)
                        }
                        else{
                            remove(packageName)
                        }
                    }

                }
                mXposedService?.apply {
                    val requestingScope = addingScope.minus(scope)
                    requestScope(requestingScope, object : XposedService.OnScopeEventListener {
                        override fun onScopeRequestApproved(approved: List<String?>) {
                            Toast.makeText(
                                this@ActivityMain,
                                "Added success",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        override fun onScopeRequestFailed(message: String) {
                            Toast.makeText(
                                this@ActivityMain,
                                "Denied scope request, make sure added manually later.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
                }
            }

            Snackbar.make(
                findViewById(R.id.root_view_for_snackbar),
                R.string.settings_saved,
                Snackbar.LENGTH_SHORT
            ).show()

            hasUnsavedChanges = false
        } catch (e: Exception) {
            Snackbar.make(
                findViewById(R.id.root_view_for_snackbar),
                R.string.error_saving_settings,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadEnabledApps() {
        loadingProgress.visibility = View.VISIBLE
        appListRecyclerView.visibility = View.GONE
        noAppsText.visibility = View.GONE

        mainScope.launch {
            try {
                val enabledAppsList = withContext(Dispatchers.IO) {
                    getEnabledApps()
                }

                enabledApps.clear()
                enabledApps.addAll(enabledAppsList)

                if (enabledApps.isEmpty()) {
                    loadingProgress.visibility = View.GONE
                    noAppsText.visibility = View.VISIBLE
                    noAppsText.text = getString(R.string.no_apps_found)
                } else {
                    val adapter = AppLanguageAdapter(
                        this@ActivityMain,
                        enabledApps,
                        languageMappings
                    ) { packageName, languageCode ->
                        languageMappings[packageName] = languageCode
                        hasUnsavedChanges = true
                    }

                    appListRecyclerView.adapter = adapter
                    loadingProgress.visibility = View.GONE
                    appListRecyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
                noAppsText.visibility = View.VISIBLE
                noAppsText.text = getString(R.string.error_loading_apps)
            }
        }
    }

    private suspend fun getEnabledApps(): List<ApplicationInfo> = withContext(Dispatchers.IO) {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)

        return@withContext installedApps.filter { app ->
            app.enabled && !app.packageName.equals(packageName)
        }.sortedBy {
            pm.getApplicationLabel(it).toString().lowercase()
        }
    }

    private fun filterApps(query: String?) {
        val adapter = appListRecyclerView.adapter as? AppLanguageAdapter ?: return

        if (query.isNullOrBlank()) {
            adapter.updateList(enabledApps)
            return
        }

        val filteredApps = enabledApps.filter { appInfo ->
            val appName = packageManager.getApplicationLabel(appInfo).toString().lowercase()
            val packageName = appInfo.packageName.lowercase()
            val searchQuery = query.lowercase()

            appName.contains(searchQuery) || packageName.contains(searchQuery)
        }

        adapter.updateList(filteredApps)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AppLanguageAdapter.REQUEST_LANGUAGE_SELECTION && resultCode == RESULT_OK) {
            data?.getStringExtra(LanguageSelectionActivity.RESULT_LANGUAGE_CODE)?.let { languageCode ->
                // Get the package name from the data
                val packageName = data.getStringExtra(LanguageSelectionActivity.EXTRA_PACKAGE_NAME) ?: return@let

                // Update the language mapping
                languageMappings[packageName] = languageCode
                hasUnsavedChanges = true

                // Refresh the adapter to show the updated language
                (appListRecyclerView.adapter as? AppLanguageAdapter)?.notifyDataSetChanged()
            }
        }
    }
}
