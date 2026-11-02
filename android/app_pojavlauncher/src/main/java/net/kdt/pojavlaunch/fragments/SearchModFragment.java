package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    private View mOverlay;
    private float mOverlayTopCache; // Padding cache reduce resource lookup

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;

    private ModpackApi modpackApi;

    private final SearchFilters mSearchFilters;

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        // Amatista: la sección de contenido empieza en mods
        mSearchFilters.contentType = Constants.TYPE_MOD;
        mSearchFilters.isModpack = false;
    }

    /* Orden del selector de tipo */
    private static final int[] TYPES = {Constants.TYPE_MOD, Constants.TYPE_MODPACK, Constants.TYPE_RESOURCEPACK, Constants.TYPE_SHADER};
    /* Valores del selector de cargador (el primero = cualquiera) */
    private static final String[] LOADERS = {null, "forge", "neoforge", "fabric", "quilt"};

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Amatista: la clave de CurseForge se puede poner en Ajustes > Varios
        String cfKey = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString("amatista_curseforge_key", "").trim();
        if (cfKey.isEmpty()) cfKey = context.getString(R.string.curseforge_api_key);
        modpackApi = new CommonApi(cfKey);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);

        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        // Amatista: buscador que busca mientras escribes (con una pequeña pausa) y botón para borrar
        View clearButton = view.findViewById(R.id.search_mod_clear);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable delayedSearch = () -> { if (isAdded() && getView() != null) searchMods(mSearchEditText.getText().toString()); };
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                handler.removeCallbacks(delayedSearch);
                handler.postDelayed(delayedSearch, 450);
            }
        });
        clearButton.setOnClickListener(v -> mSearchEditText.setText(""));
        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            handler.removeCallbacks(delayedSearch);
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return actionId == EditorInfo.IME_ACTION_SEARCH;
        });

        mOverlay.post(()->{
           int overlayHeight = mOverlay.getHeight();
           mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
                   mRecyclerview.getPaddingTop() + overlayHeight,
                   mRecyclerview.getPaddingRight(),
                   mRecyclerview.getPaddingBottom());
        });
        mFilterButton.setOnClickListener(v -> displayFilterDialog());

        Spinner typeSpinner = view.findViewById(R.id.search_mod_type_spinner);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.amatista_content_types));
        typeSpinner.setAdapter(typeAdapter);
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i] == mSearchFilters.contentType) typeSpinner.setSelection(i, false);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                int type = TYPES[position];
                if (type == mSearchFilters.contentType) return;
                mSearchFilters.contentType = type;
                mSearchFilters.isModpack = type == Constants.TYPE_MODPACK;
                searchMods(mSearchEditText.getText().toString());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Al volver de una ficha se conserva la búsqueda
        searchMods(mSearchFilters.name);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        // setup the view behavior
        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);

            assert mSelectVersionButton != null;
            assert mSelectedVersion != null;
            assert mApplyButton != null;

            // Setup the expendable list behavior
            mSelectVersionButton.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true, (id, snapshot)-> mSelectedVersion.setText(id)));

            // Apply visually all the current settings
            mSelectedVersion.setText(mSearchFilters.mcVersion);

            // Cargador: solo para mods y modpacks
            Spinner loaderSpinner = dialog.findViewById(R.id.search_mod_loader_spinner);
            View loaderLabel = dialog.findViewById(R.id.search_mod_loader_textview);
            boolean loaderVisible = mSearchFilters.contentType == Constants.TYPE_MOD || mSearchFilters.contentType == Constants.TYPE_MODPACK;
            assert loaderSpinner != null && loaderLabel != null;
            loaderSpinner.setVisibility(loaderVisible ? View.VISIBLE : View.GONE);
            loaderLabel.setVisibility(loaderVisible ? View.VISIBLE : View.GONE);
            loaderSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                    getResources().getStringArray(R.array.amatista_content_loaders)));
            for (int i = 0; i < LOADERS.length; i++) {
                if (LOADERS[i] == null ? mSearchFilters.loader == null : LOADERS[i].equals(mSearchFilters.loader)) {
                    loaderSpinner.setSelection(i);
                }
            }
            mSelectedVersion.setOnLongClickListener(v -> { mSelectedVersion.setText(""); return true; });

            // Apply the new settings
            mApplyButton.setOnClickListener(v -> {
                mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                mSearchFilters.loader = LOADERS[Math.max(0, loaderSpinner.getSelectedItemPosition())];
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
        });


        dialog.show();
    }
}
