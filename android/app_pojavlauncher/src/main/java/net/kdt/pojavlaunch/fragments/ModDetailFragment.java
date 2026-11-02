package net.kdt.pojavlaunch.fragments;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ContentInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.DependencyResolver;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.WebImageLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;

import java.util.Arrays;

/**
 * Amatista: ficha grande de un elemento de Modrinth / CurseForge:
 * imagen, nombre y creador; versión e instalar; dependencias; fotos.
 */
public class ModDetailFragment extends Fragment {
    public static final String TAG = "AmatistaModDetail";

    /* El elemento y la API se pasan por aquí (no son Parcelable). */
    private static ModItem sPendingItem;
    private static ModpackApi sPendingApi;

    public static void open(androidx.fragment.app.FragmentActivity activity, ModItem item, ModpackApi api) {
        sPendingItem = item;
        sPendingApi = api;
        Tools.swapFragment(activity, ModDetailFragment.class, TAG, null);
    }

    private ModItem mItem;
    private ModpackApi mApi;
    private ModDetail mDetail;
    private int mDepsToken = 0;

    private Spinner mVersionSpinner;
    private Button mInstallButton;
    private TextView mDepsView;
    private TextView mErrorView;

    public ModDetailFragment() {
        super(R.layout.fragment_mod_detail);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mItem = sPendingItem;
        mApi = sPendingApi;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (mItem == null || mApi == null) { // p. ej. la app se recreó: volver atrás
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }
        ImageView icon = view.findViewById(R.id.detail_icon);
        TextView title = view.findViewById(R.id.detail_title);
        TextView author = view.findViewById(R.id.detail_author);
        TextView source = view.findViewById(R.id.detail_source);
        TextView description = view.findViewById(R.id.detail_description);
        mVersionSpinner = view.findViewById(R.id.detail_version_spinner);
        mInstallButton = view.findViewById(R.id.detail_install_button);
        mDepsView = view.findViewById(R.id.detail_dependencies);
        mErrorView = view.findViewById(R.id.detail_error);

        title.setText(mItem.title);
        if (mItem.author != null && !mItem.author.isEmpty()) {
            author.setText(getString(R.string.amatista_detail_by, mItem.author));
        } else {
            author.setVisibility(View.GONE);
        }
        source.setText(sourceLabel());
        description.setText(mItem.description);

        int iconPx = dp(88);
        WebImageLoader.load(mItem.imageUrl, iconPx, bitmap -> {
            if (!isAdded()) return;
            RoundedBitmapDrawable d = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
            d.setCornerRadius(dp(16));
            icon.setImageDrawable(d);
        });

        setupGallery(view);

        // Versiones (cargando…)
        mVersionSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.global_waiting)}));
        mVersionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) { loadDependencies(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        mInstallButton.setOnClickListener(v -> install());

        final ModItem item = mItem;
        PojavApplication.sExecutorService.execute(() -> {
            ModDetail detail = null;
            try { detail = mApi.getModDetails(item); } catch (Throwable ignored) {}
            final ModDetail result = detail;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                mDetail = result;
                if (result == null || result.versionNames == null || result.versionNames.length == 0) {
                    mErrorView.setVisibility(View.VISIBLE);
                    mVersionSpinner.setAdapter(null);
                    return;
                }
                mVersionSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                        Arrays.asList(result.versionNames)));
                mInstallButton.setEnabled(true);
                loadDependencies(mVersionSpinner.getSelectedItemPosition());
            });
        });
    }

    private String sourceLabel() {
        String type;
        switch (mItem.contentType) {
            case Constants.TYPE_MODPACK: type = getString(R.string.amatista_type_modpack); break;
            case Constants.TYPE_RESOURCEPACK: type = getString(R.string.amatista_type_resourcepack); break;
            case Constants.TYPE_SHADER: type = getString(R.string.amatista_type_shader); break;
            default: type = getString(R.string.amatista_type_mod);
        }
        String src = mItem.apiSource == Constants.SOURCE_CURSEFORGE ? "CurseForge" : "Modrinth";
        return type + "  ·  " + src;
    }

    private void install() {
        if (mDetail == null) return;
        int index = mVersionSpinner.getSelectedItemPosition();
        if (mDetail.contentType != Constants.TYPE_MODPACK) {
            ContentInstaller.pickProfileAndInstall(requireContext(), mDetail, index);
        } else {
            mApi.handleInstallation(requireContext().getApplicationContext(), mDetail, index);
        }
    }

    private void loadDependencies(int versionIndex) {
        final ModDetail detail = mDetail;
        if (detail == null || detail.isModpack || versionIndex < 0) {
            mDepsView.setVisibility(View.GONE);
            return;
        }
        final int token = ++mDepsToken;
        final Context ctx = requireContext().getApplicationContext();
        mDepsView.setVisibility(View.VISIBLE);
        mDepsView.setText(R.string.amatista_deps_loading);
        PojavApplication.sExecutorService.execute(() -> {
            DependencyResolver.Result result = DependencyResolver.resolve(ctx, detail, versionIndex);
            Tools.runOnUiThread(() -> {
                if (token != mDepsToken || !isAdded() || getView() == null) return;
                if (result == null) {
                    mDepsView.setText(R.string.amatista_deps_error);
                    return;
                }
                if (result.required.isEmpty() && result.optional.isEmpty()) {
                    mDepsView.setText(R.string.amatista_deps_none);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                if (!result.required.isEmpty()) {
                    sb.append(ctx.getString(R.string.amatista_deps_required));
                    for (String n : result.required) sb.append("\n  •  ").append(n);
                } else {
                    sb.append(ctx.getString(R.string.amatista_deps_none));
                }
                if (!result.optional.isEmpty()) {
                    sb.append("\n\n").append(ctx.getString(R.string.amatista_deps_optional));
                    for (String n : result.optional) sb.append("\n  ◦  ").append(n);
                }
                mDepsView.setText(sb.toString());
            });
        });
    }

    private void setupGallery(View root) {
        String[] gallery = mItem.gallery;
        if (gallery == null || gallery.length == 0) return;
        root.findViewById(R.id.detail_gallery_title).setVisibility(View.VISIBLE);
        root.findViewById(R.id.detail_gallery_scroll).setVisibility(View.VISIBLE);
        LinearLayout row = root.findViewById(R.id.detail_gallery);
        int w = dp(240), h = dp(135);
        for (String url : gallery) {
            ImageView image = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMarginEnd(dp(10));
            image.setLayoutParams(lp);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundResource(R.drawable.bg_amatista_deps);
            image.setClipToOutline(true);
            image.setOnClickListener(v -> showFullImage(url));
            row.addView(image);
            WebImageLoader.load(url, image, w);
        }
    }

    /** Foto en grande al tocarla. */
    private void showFullImage(String url) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ImageView image = new ImageView(requireContext());
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(image);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        WebImageLoader.load(url, image, getResources().getDisplayMetrics().widthPixels);
        dialog.show();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
