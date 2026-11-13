package com.nocturna.browser;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsSessionToken;

import java.util.List;

/**
 * Le dice a Android y a las demás apps que Nocturna puede abrir «pestañas personalizadas». No hace nada especial:
 * las apps piden la pestaña y Nocturna la abre como cualquier enlace, en su propia pantalla.
 */
public final class CustomTabsHost extends CustomTabsService {
    @Override
    protected boolean warmup(long flags) {
        return true;
    }

    @Override
    protected boolean newSession(@NonNull CustomTabsSessionToken sessionToken) {
        return true;
    }

    @Override
    protected boolean mayLaunchUrl(@NonNull CustomTabsSessionToken sessionToken, @Nullable Uri url,
                                   @Nullable Bundle extras, @Nullable List<Bundle> otherLikelyBundles) {
        return false;
    }

    @Override
    protected Bundle extraCommand(@NonNull String commandName, @Nullable Bundle args) {
        return null;
    }

    @Override
    protected boolean updateVisuals(@NonNull CustomTabsSessionToken sessionToken, @Nullable Bundle bundle) {
        return false;
    }

    @Override
    protected boolean requestPostMessageChannel(@NonNull CustomTabsSessionToken sessionToken, @NonNull Uri postMessageOrigin) {
        return false;
    }

    @Override
    protected int postMessage(@NonNull CustomTabsSessionToken sessionToken, @NonNull String message, @Nullable Bundle extras) {
        return RESULT_FAILURE_DISALLOWED;
    }

    @Override
    protected boolean validateRelationship(@NonNull CustomTabsSessionToken sessionToken, int relation,
                                           @NonNull Uri origin, @Nullable Bundle extras) {
        return false;
    }

    @Override
    protected boolean receiveFile(@NonNull CustomTabsSessionToken sessionToken, @NonNull Uri uri, int purpose, @Nullable Bundle extras) {
        return false;
    }
}
