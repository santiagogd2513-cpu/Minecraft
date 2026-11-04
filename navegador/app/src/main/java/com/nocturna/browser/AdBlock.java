package com.nocturna.browser;

import java.util.HashSet;
import java.util.Set;

/** Bloqueador sencillo de anuncios y rastreadores por dominio. */
final class AdBlock {
    private static final Set<String> HOSTS = new HashSet<>();

    static {
        String[] list = {
                "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
                "googletagservices.com", "googletagmanager.com", "adservice.google.com", "pagead2.googlesyndication.com",
                "adnxs.com", "adsrvr.org", "advertising.com", "adform.net", "criteo.com", "criteo.net", "taboola.com",
                "outbrain.com", "scorecardresearch.com", "quantserve.com", "moatads.com", "amazon-adsystem.com",
                "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "smartadserver.com",
                "yieldmo.com", "media.net", "zedo.com", "propellerads.com", "popads.net", "popcash.net",
                "adcash.com", "exoclick.com", "juicyads.com", "trafficjunky.net", "hotjar.com", "mixpanel.com",
                "facebook.net", "connect.facebook.net", "ads-twitter.com", "analytics.twitter.com", "bat.bing.com",
                "adcolony.com", "applovin.com", "unityads.unity3d.com", "chartboost.com", "inmobi.com",
                "mopub.com", "vungle.com", "admob.com", "onesignal.com", "pushcrew.com", "revcontent.com",
                "mgid.com", "adsterra.com", "hilltopads.net", "clickadu.com", "a-ads.com", "bidvertiser.com",
                "infolinks.com", "sharethrough.com", "teads.tv", "33across.com", "lijit.com", "sovrn.com",
                "gumgum.com", "indexww.com", "bidswitch.net", "contextweb.com", "spotxchange.com", "springserve.com",
        };
        for (String h : list) HOSTS.add(h);
    }

    private AdBlock() {}

    static boolean blocked(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        while (true) {
            if (HOSTS.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false;
            h = h.substring(dot + 1);
        }
    }
}
