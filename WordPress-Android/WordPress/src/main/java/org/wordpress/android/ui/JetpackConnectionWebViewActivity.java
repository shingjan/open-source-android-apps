package org.wordpress.android.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.login.LoginMode;
import org.wordpress.android.ui.accounts.LoginActivity;

import java.util.List;

import static org.wordpress.android.WordPress.SITE;
import static org.wordpress.android.ui.JetpackConnectionWebViewClient.JETPACK_CONNECTION_DEEPLINK;
import static org.wordpress.android.ui.RequestCodes.JETPACK_LOGIN;

/**
 * Activity that opens the Jetpack login flow and returns to StatsActivity when finished.
 * Use one of the static factory methods to start the flow.
 */
public class JetpackConnectionWebViewActivity extends WPWebViewActivity
        implements JetpackConnectionWebViewClient.JetpackConnectionWebViewClientListener {
    private static final String REDIRECT_PAGE_STATE_ITEM = "redirectPage";
    private static final String TRACKING_SOURCE_KEY = "tracking_source";

    public enum Source {
        STATS("stats"), NOTIFICATIONS("notifications");
        private final String mValue;

        Source(String value) {
            mValue = value;
        }

        @Nullable
        public static Source fromString(String value) {
            if (STATS.mValue.equals(value)) {
                return STATS;
            } else if (NOTIFICATIONS.mValue.equals(value)) {
                return NOTIFICATIONS;
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return mValue;
        }
    }

    private SiteModel mSite;
    private JetpackConnectionWebViewClient mWebViewClient;

    public static void startJetpackConnectionFlow(Context context, Source source, SiteModel site, boolean authorized) {
        String url = "https://wordpress.com/jetpack/connect?"
                     + "url=" + site.getUrl()
                     + "&mobile_redirect=" + JETPACK_CONNECTION_DEEPLINK
                     + "?source=" + source.toString();
        startJetpackConnectionFlow(context, url, site, authorized, source);
    }

    private static void startJetpackConnectionFlow(Context context,
                                                   String url,
                                                   SiteModel site,
                                                   boolean authorized,
                                                   Source source) {
        if (!checkContextAndUrl(context, url)) {
            return;
        }

        Intent intent = new Intent(context, JetpackConnectionWebViewActivity.class);
        intent.putExtra(WPWebViewActivity.URL_TO_LOAD, url);
        if (authorized) {
            intent.putExtra(WPWebViewActivity.USE_GLOBAL_WPCOM_USER, true);
            intent.putExtra(WPWebViewActivity.AUTHENTICATION_URL, WPCOM_LOGIN_URL);
        }
        if (site != null) {
            intent.putExtra(WordPress.SITE, site);
        }
        intent.putExtra(TRACKING_SOURCE_KEY, source);
        context.startActivity(intent);
        trackJetpackConnectionFlowStart(site, source);
    }

    private static void trackJetpackConnectionFlowStart(SiteModel site, Source source) {
        if (!site.isJetpackInstalled()) {
            JetpackConnectionUtils.trackWithSource(AnalyticsTracker.Stat.INSTALL_JETPACK_SELECTED, source);
        } else {
            JetpackConnectionUtils.trackWithSource(AnalyticsTracker.Stat.CONNECT_JETPACK_SELECTED, source);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mSite = (SiteModel) getIntent().getSerializableExtra(WordPress.SITE);
        // We need to get the site before calling super since it'll create the web client
        super.onCreate(savedInstanceState);
    }

    @Override
    protected WebViewClient createWebViewClient(List<String> allowedURL) {
        mWebViewClient = new JetpackConnectionWebViewClient(this, mSite.getUrl());
        return mWebViewClient;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == JETPACK_LOGIN && resultCode == RESULT_OK) {
            JetpackConnectionWebViewActivity
                    .startJetpackConnectionFlow(this, mWebViewClient.getRedirectPage(), mSite,
                                                mAccountStore.hasAccessToken(),
                                                (Source) getIntent().getSerializableExtra(TRACKING_SOURCE_KEY));
        }
        finish();
    }

    @Override
    protected void cancel() {
        JetpackConnectionUtils.trackWithSource(AnalyticsTracker.Stat.INSTALL_JETPACK_CANCELLED,
                                               (Source) getIntent().getSerializableExtra(TRACKING_SOURCE_KEY));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(REDIRECT_PAGE_STATE_ITEM, mWebViewClient.getRedirectPage());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mWebViewClient.setRedirectPage(savedInstanceState.getString(REDIRECT_PAGE_STATE_ITEM));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    // JetpackConnectionWebViewClientListener

    @Override
    public void onRequiresWPComLogin(WebView webView, String redirectPage) {
        String authenticationUrl = WPWebViewActivity.getSiteLoginUrl(mSite);
        String postData = WPWebViewActivity.getAuthenticationPostData(authenticationUrl, redirectPage,
                                                                      mSite.getUsername(), mSite.getPassword(),
                                                                      mAccountStore.getAccessToken());
        webView.postUrl(authenticationUrl, postData.getBytes());
    }

    @Override
    public void onRequiresJetpackLogin() {
        Intent loginIntent = new Intent(this, LoginActivity.class);
        LoginMode.JETPACK_STATS.putInto(loginIntent);
        startActivityForResult(loginIntent, JETPACK_LOGIN);
    }

    @Override
    public void onJetpackSuccessfullyConnected(Uri uri) {
        JetpackConnectionUtils.trackWithSource(AnalyticsTracker.Stat.INSTALL_JETPACK_COMPLETED,
                                               (Source) getIntent().getSerializableExtra(TRACKING_SOURCE_KEY));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.putExtra(SITE, mSite);
        startActivity(intent);
        finish();
    }
}
