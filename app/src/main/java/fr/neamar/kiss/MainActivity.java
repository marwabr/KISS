package fr.neamar.kiss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.forwarder.ForwarderManager;
import fr.neamar.kiss.searcher.HistorySearcher;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.searcher.QuerySearcher;
import fr.neamar.kiss.searcher.Searcher;
import fr.neamar.kiss.searcher.TagsSearcher;
import fr.neamar.kiss.searcher.UntaggedSearcher;
import fr.neamar.kiss.ui.AnimatedListView;
import fr.neamar.kiss.ui.KeyboardScrollHider;
import fr.neamar.kiss.ui.ListPopup;
import fr.neamar.kiss.ui.SearchEditText;
import fr.neamar.kiss.utils.SystemUiVisibilityHelper;

public class MainActivity extends Activity implements QueryInterface, KeyboardScrollHider.KeyboardHandler, View.OnTouchListener {

    public static final String START_LOAD = "fr.neamar.summon.START_LOAD";
    public static final String LOAD_OVER = "fr.neamar.summon.LOAD_OVER";
    public static final String FULL_LOAD_OVER = "fr.neamar.summon.FULL_LOAD_OVER";

    private static final String TAG = "MainActivity";

    /**
     * Adapter to display records
     */
    public RecordAdapter adapter;

    /**
     * Store user preferences
     */
    public SharedPreferences prefs;

    /**
     * Receive events from providers
     */
    private BroadcastReceiver mReceiver;

    /**
     * View for the Search text
     */
    public SearchEditText searchEditText;

    /**
     * Main list view
     */
    public AnimatedListView list;
    public View listContainer;
    /**
     * View to display when list is empty
     */
    public View emptyListView;
    /**
     * Utility for automatically hiding the keyboard when scrolling down
     */
    private KeyboardScrollHider hider;

    /**
     * The ViewGroup that wraps the buttons at the right hand side of the searchEditText
     */
    public ViewGroup rightHandSideButtonsWrapper;
    /**
     * Menu button
     */
    public View menuButton;
    /**
     * Kiss bar
     */
    public View kissBar;
    /**
     * Favorites bar. Can be either the favorites within the KISS bar,
     * or the external favorites bar (default)
     */
    public ViewGroup favoritesBar;
    /**
     * Progress bar displayed when loading
     */
    private View loaderSpinner;

    /**
     * The ViewGroup that wraps the buttons at the left hand side of the searchEditText
     */
    public ViewGroup leftHandSideButtonsWrapper;
    /**
     * Launcher button, can be clicked to display all apps
     */
    public View launcherButton;

    /**
     * Launcher button's white counterpart, which appears when launcher button is clicked
     */
    public View whiteLauncherButton;
    /**
     * "X" button to empty the search field
     */
    private View clearButton;

    /**
     * Task launched on text change
     */
    private Searcher searchTask;

    /**
     * SystemUiVisibility helper
     */
    private SystemUiVisibilityHelper systemUiVisibilityHelper;

    /**
     * Is the KISS bar currently displayed?
     * (flag updated before animation is over)
     */
    private boolean isDisplayingKissBar = false;

    private PopupWindow mPopup;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        KissApplication.getApplication(this).initDataHandler();

        /*
         * Initialize preferences
         */
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        /*
         * Set the view and store all useful components
         */
        setContentView(R.layout.main);
        this.list = this.findViewById(android.R.id.list);
        this.listContainer = (View) this.list.getParent();
        this.emptyListView = this.findViewById(android.R.id.empty);
        this.kissBar = findViewById(R.id.mainKissbar);
        this.rightHandSideButtonsWrapper = findViewById(R.id.rightHandSideButtonsWrapper);
        this.menuButton = findViewById(R.id.menuButton);
        this.searchEditText = findViewById(R.id.searchEditText);
        this.loaderSpinner = findViewById(R.id.loaderBar);
        this.leftHandSideButtonsWrapper = findViewById(R.id.leftHandSideButtonsWrapper);
        this.launcherButton = findViewById(R.id.launcherButton);
        this.whiteLauncherButton = findViewById(R.id.whiteLauncherButton);
        this.clearButton = findViewById(R.id.clearButton);

        // Create adapter for records
        this.adapter = new RecordAdapter(this, new ArrayList<>());
        this.list.setAdapter(this.adapter);

        // Listen to changes
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                // Auto left-trim text.
                if (s.length() > 0 && s.charAt(0) == ' ')
                    s.delete(0, 1);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isViewingAllApps()) {
                    displayKissBar(false, false);
                }
                String text = s.toString();
                updateSearchRecords(text);
                displayClearOnInput();
            }
        });

        this.listContainer.setVisibility(View.VISIBLE);
        systemUiVisibilityHelper = new SystemUiVisibilityHelper(this);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Restart if required,
     * Hide the kissbar by default
     */
    @SuppressLint("CommitPrefEdits")
    protected void onResume() {
        Log.d(TAG, "onResume()");

        if (prefs.getBoolean("require-layout-update", false)) {
            super.onResume();
            Log.i(TAG, "Restarting app after setting changes");
            // Restart current activity to refresh view, since some preferences
            // may require using a new UI
            prefs.edit().putBoolean("require-layout-update", false).apply();
            this.recreate();
            return;
        }

        dismissPopup();

        if (KissApplication.getApplication(this).getDataHandler().allProvidersHaveLoaded) {
            displayLoader(false);
            onFavoriteChange();
        }

        // We need to update the history in case an external event created new items
        // (for instance, installed a new app, got a phone call or simply clicked on a favorite)
        updateSearchRecords();
        displayClearOnInput();

        if (isViewingAllApps()) {
            displayKissBar(false);
        }


        super.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(this.mReceiver);
    }


    @Override
    public boolean onKeyDown(int keycode, @NonNull KeyEvent e) {
        return super.onKeyDown(keycode, e);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Display menu, on short or long press.
     *
     * @param menuButton "kebab" menu (3 dots)
     */
    public void onMenuButtonClicked(View menuButton) {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    }

    /**
     * Clear text content when touching the cross button
     */
    @SuppressWarnings("UnusedParameters")
    public void onClearButtonClicked(View clearButton) {
        searchEditText.setText("");
    }

    /**
     * Display KISS menu
     */
    public void onLauncherButtonClicked(View launcherButton) {
        updateSearchRecords();
        // Display or hide the kiss bar, according to current view tag (showMenu / hideMenu).
        // displayKissBar(launcherButton.getTag().equals("showMenu"));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    private void displayClearOnInput() {
    }

    public void displayLoader(Boolean display) {
    }

    public void onFavoriteChange() {
        launchOccurred();
    }

    private void displayKissBar(Boolean display) {
        this.displayKissBar(display, true);
    }

    private void displayKissBar(boolean display, boolean clearSearchText) {
    }

    public void updateSearchRecords() {
        updateSearchRecords(searchEditText.getText().toString());
    }

    /**
     * This function gets called on query changes.
     * It will ask all the providers for data
     * This function is not called for non search-related changes! Have a look at onDataSetChanged() if that's what you're looking for :)
     *
     * @param query the query on which to search
     */
    private void updateSearchRecords(String query) {

        runTask(new QuerySearcher(this, query));

    }

    public void runTask(Searcher task) {
        searchTask = task;
        searchTask.executeOnExecutor(Searcher.SEARCH_THREAD);
    }

    public void resetTask() {
        if (searchTask != null) {
            searchTask.cancel(true);
            searchTask = null;
        }
    }

    /**
     * Call this function when we're leaving the activity after clicking a search result
     * to clear the search list.
     * We can't use onPause(), since it may be called for a configuration change
     */
    @Override
    public void launchOccurred() {

    }

    public void registerPopup(ListPopup popup) {

    }


    public void showKeyboard() {

    }

    @Override
    public void hideKeyboard() {

    }

    @Override
    public void applyScrollSystemUi() {
    }

    /**
     * Check if history / search or app list is visible
     *
     * @return true of history, false on app list
     */
    public boolean isViewingSearchResults() {
        return !isDisplayingKissBar;
    }

    public boolean isViewingAllApps() {
        return isDisplayingKissBar;
    }

    public void beforeListChange() {
    }

    public void afterListChange() {
    }

    public void dismissPopup() {
        if (mPopup != null)
            mPopup.dismiss();
    }

    public void showMatchingTags(String tag) {
        runTask(new TagsSearcher(this, tag));

        clearButton.setVisibility(View.VISIBLE);
        menuButton.setVisibility(View.INVISIBLE);
    }

    public void showUntagged() {
        runTask(new UntaggedSearcher(this));

        clearButton.setVisibility(View.VISIBLE);
        menuButton.setVisibility(View.INVISIBLE);
    }

    public void showHistory() {
        runTask(new HistorySearcher(this));

        clearButton.setVisibility(View.VISIBLE);
        menuButton.setVisibility(View.INVISIBLE);
    }

    public boolean isKissDefaultLauncher() {
        String homePackage;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = getPackageManager();
            final ResolveInfo mInfo = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            homePackage = mInfo.activityInfo.packageName;
        } catch (Exception e) {
            homePackage = "unknown";
        }

        return homePackage.equals(this.getPackageName());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }
}
