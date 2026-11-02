package net.kdt.pojavlaunch.utils;


import static net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_FORCE_ENGLISH;

import android.content.*;
import android.content.res.*;
import android.os.Build;
import android.os.LocaleList;

import androidx.preference.*;
import java.util.*;

public class LocaleUtils extends ContextWrapper {

    public LocaleUtils(Context base) {
        super(base);
    }

    public static ContextWrapper setLocale(Context context) {
        if (DEFAULT_PREF == null) {
            DEFAULT_PREF = PreferenceManager.getDefaultSharedPreferences(context);
            // Too early to initialize all prefs here, as this is called by PojavApplication
            // before storage checks are done and before the storage paths are initialized.
            // So only initialize PREF_FORCE_ENGLISH for the check below.
            PREF_FORCE_ENGLISH = DEFAULT_PREF.getBoolean("force_english", false);
        }

        // Amatista Launcher: todo en español salvo que se active "forzar inglés".
        {
            Locale target = PREF_FORCE_ENGLISH ? Locale.ENGLISH : new Locale("es");
            Resources resources = context.getResources();
            Configuration configuration = resources.getConfiguration();

            configuration.setLocale(target);
            if (PREF_FORCE_ENGLISH) Locale.setDefault(target); // no tocar formatos numéricos de la JVM
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                LocaleList localeList = new LocaleList(target);
                if (PREF_FORCE_ENGLISH) LocaleList.setDefault(localeList);
                configuration.setLocales(localeList);
            }

            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1){
                context = context.createConfigurationContext(configuration);
            }
        }

        return new LocaleUtils(context);
    }
}
