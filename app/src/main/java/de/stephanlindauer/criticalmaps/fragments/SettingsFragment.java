package de.stephanlindauer.criticalmaps.fragments;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import javax.inject.Inject;

import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.R;
import de.stephanlindauer.criticalmaps.databinding.FragmentSettingsBinding;
import de.stephanlindauer.criticalmaps.prefs.SharedPrefsKeys;
import de.stephanlindauer.criticalmaps.provider.StorageLocationProvider;
import info.metadude.android.typedpreferences.BooleanPreference;
import timber.log.Timber;

public class SettingsFragment extends Fragment {
    @Inject
    StorageLocationProvider storageLocationProvider;

    @Inject
    SharedPreferences sharedPreferences;

    private FragmentSettingsBinding binding;

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        App.components().inject(this);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        updateClearCachePref();
        updateStorageGraph();
        updateChooseStoragePref();

        binding.settingsShowOnLockscreenCheckbox.setChecked(
                new BooleanPreference(sharedPreferences, SharedPrefsKeys.SHOW_ON_LOCKSCREEN).get());

        binding.settingsKeepScreenOnCheckbox.setChecked(
                new BooleanPreference(sharedPreferences, SharedPrefsKeys.KEEP_SCREEN_ON).get());

        binding.settingsMapRotationCheckbox.setChecked(
                !new BooleanPreference(sharedPreferences, SharedPrefsKeys.DISABLE_MAP_ROTATION).get());

        binding.settingsHighResTilesCheckbox.setChecked(
                new BooleanPreference(sharedPreferences, SharedPrefsKeys.USE_HIGH_RES_MAP_TILES).get());

        binding.settingsClearCacheButton.setOnClickListener(v -> handleClearCacheClicked());
        binding.settingsChooseStorageContainer.setOnClickListener(v -> handleChooseStorageClicked());

        binding.settingsShowOnLockscreenCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> handleShowOnLockscreenChecked(isChecked));
        binding.settingsKeepScreenOnCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> handleKeepScreenOnChecked(isChecked));
        binding.settingsMapRotationCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> handleDisableMapRotationChecked(isChecked));
        binding.settingsHighResTilesCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> handleUseHighResTilesChecked(isChecked));
    }

    private void updateStorageGraph() {
        StorageLocationProvider.StorageLocation currentStorageLocation =
                storageLocationProvider.getActiveStorageLocation();

        float usedPercentage =
                (float) currentStorageLocation.usedSpace / currentStorageLocation.totalSize;

        long tileSize = currentStorageLocation.getCacheSize();

        float tilePercentage = (float) tileSize / currentStorageLocation.totalSize;

        binding.settingsCacheUsedSpaceText.setText(String.format(getString(R.string.settings_cache_used_mb),
                Formatter.formatShortFileSize(getActivity(), currentStorageLocation.usedSpace)));
        binding.settingsCacheUsedCacheSpaceText.setText(String.format(getString(R.string.settings_cache_cache_mb),
                Formatter.formatShortFileSize(getActivity(), tileSize)));
        binding.settingsCacheFreeSpaceText.setText(String.format(getString(R.string.settings_cache_free_mb),
                Formatter.formatShortFileSize(getActivity(), currentStorageLocation.freeSpace)));

        binding.settingsCacheStoragespacegraph.setBarPercentagesAnimated(
                usedPercentage, tilePercentage);
    }

    private void updateClearCachePref() {
        long currentSize =
                storageLocationProvider.getActiveStorageLocation().getCacheSize();
        Timber.d("Current cache size: %s",
                Formatter.formatShortFileSize(getActivity(), currentSize));
        binding.settingsClearCacheSummaryText.setText(
                String.format(getString(R.string.settings_cache_currently_used),
                        Formatter.formatShortFileSize(getActivity(), currentSize)));
    }

    private void updateChooseStoragePref() {
        binding.settingsChooseStorageSummaryText.setText(
                storageLocationProvider.getActiveStorageLocation().displayName);
    }

    void handleClearCacheClicked() {
        storageLocationProvider.getActiveStorageLocation().clearCache();
        updateClearCachePref();
        updateStorageGraph();
    }

    void handleChooseStorageClicked() {
        ArrayList<StorageLocationProvider.StorageLocation> storageLocations =
                storageLocationProvider.getAllWritableStorageLocations();

        StorageLocationProvider.StorageLocation activeStorageLocation =
                storageLocationProvider.getActiveStorageLocation();

        int currentlyActive = 0;
        ArrayList<String> storageLocationNames = new ArrayList<>(4);

        for (int i = 0; i < storageLocations.size(); i++) {
            StorageLocationProvider.StorageLocation sL = storageLocations.get(i);
            storageLocationNames.add(sL.displayName + " " + String.format(
                    getString(R.string.settings_choose_storage_mb_free),
                    Formatter.formatShortFileSize(getActivity(), sL.freeSpace)));

            if (storageLocations.get(i).storagePath.equals(activeStorageLocation.storagePath)) {
                currentlyActive = i;
            }
        }

        Activity activity = getActivity();
        int finalCurrentlyActive = currentlyActive;
        //noinspection ConstantConditions
        new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                .setTitle(R.string.settings_choose_storage_choose_title)
                .setSingleChoiceItems(
                        storageLocationNames.toArray(new String[0]), currentlyActive, null)
                .setPositiveButton(R.string.ok, (dialog, id) -> {
                    int selectedStorage =
                            ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    if (selectedStorage == finalCurrentlyActive) {
                        return;
                    }

                    new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                            .setTitle(R.string.settings_choose_storage_confirm_title)
                            .setMessage(R.string.settings_choose_storage_confirm_message)
                            .setPositiveButton(R.string.settings_cache_clear, (dialog1, which) -> {
                                // clear old cache
                                activeStorageLocation.clearCache();
                                // set new storage
                                storageLocationProvider.setActiveStorageLocation(
                                        storageLocations.get(selectedStorage));
                                updateClearCachePref();
                                updateStorageGraph();
                                updateChooseStoragePref();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .create()
                            .show();

                })
                .create()
                .show();
    }

    void handleShowOnLockscreenChecked(boolean isChecked) {
        new BooleanPreference(
                sharedPreferences, SharedPrefsKeys.SHOW_ON_LOCKSCREEN).set(isChecked);
    }

    void handleKeepScreenOnChecked(boolean isChecked) {
        new BooleanPreference(
                sharedPreferences, SharedPrefsKeys.KEEP_SCREEN_ON).set(isChecked);
    }

    void handleDisableMapRotationChecked(boolean isChecked) {
        new BooleanPreference(
                sharedPreferences, SharedPrefsKeys.DISABLE_MAP_ROTATION).set(!isChecked);
    }

    void handleUseHighResTilesChecked(boolean isChecked) {
        new BooleanPreference(
                sharedPreferences, SharedPrefsKeys.USE_HIGH_RES_MAP_TILES).set(isChecked);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
