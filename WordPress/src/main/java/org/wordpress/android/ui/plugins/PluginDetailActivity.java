package org.wordpress.android.ui.plugins;

import android.animation.ObjectAnimator;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.PluginActionBuilder;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.model.plugin.DualPluginModel;
import org.wordpress.android.fluxc.model.plugin.SitePluginModel;
import org.wordpress.android.fluxc.model.plugin.WPOrgPluginModel;
import org.wordpress.android.fluxc.store.PluginStore;
import org.wordpress.android.fluxc.store.PluginStore.ConfigureSitePluginPayload;
import org.wordpress.android.fluxc.store.PluginStore.DeleteSitePluginPayload;
import org.wordpress.android.fluxc.store.PluginStore.InstallSitePluginPayload;
import org.wordpress.android.fluxc.store.PluginStore.OnSitePluginConfigured;
import org.wordpress.android.fluxc.store.PluginStore.OnSitePluginDeleted;
import org.wordpress.android.fluxc.store.PluginStore.OnSitePluginInstalled;
import org.wordpress.android.fluxc.store.PluginStore.OnSitePluginUpdated;
import org.wordpress.android.fluxc.store.PluginStore.UpdateSitePluginPayload;
import org.wordpress.android.ui.ActivityLauncher;
import org.wordpress.android.util.AnalyticsUtils;
import org.wordpress.android.util.AniUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.DisplayUtils;
import org.wordpress.android.util.FormatUtils;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.SiteUtils;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.util.ToastUtils.Duration;
import org.wordpress.android.util.WPLinkMovementMethod;
import org.wordpress.android.widgets.WPNetworkImageView;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.inject.Inject;

import static org.wordpress.android.widgets.WPNetworkImageView.ImageType.PHOTO;
import static org.wordpress.android.widgets.WPNetworkImageView.ImageType.PLUGIN_ICON;

public class PluginDetailActivity extends AppCompatActivity {
    public static final String KEY_PLUGIN_SLUG = "KEY_PLUGIN_SLUG";
    private static final String KEY_IS_CONFIGURING_PLUGIN = "KEY_IS_CONFIGURING_PLUGIN";
    private static final String KEY_IS_UPDATING_PLUGIN = "KEY_IS_UPDATING_PLUGIN";
    private static final String KEY_IS_REMOVING_PLUGIN = "KEY_IS_REMOVING_PLUGIN";
    private static final String KEY_IS_ACTIVE = "KEY_IS_ACTIVE";
    private static final String KEY_IS_AUTO_UPDATE_ENABLED = "KEY_IS_AUTO_UPDATE_ENABLED";
    private static final String KEY_IS_SHOWING_REMOVE_PLUGIN_CONFIRMATION_DIALOG
            = "KEY_IS_SHOWING_REMOVE_PLUGIN_CONFIRMATION_DIALOG";

    private SiteModel mSite;
    private String mSlug;
    private DualPluginModel mDualPlugin;

    private ViewGroup mContainer;
    private TextView mTitleTextView;
    private TextView mByLineTextView;
    private TextView mVersionTopTextView;
    private TextView mVersionBottomTextView;
    private TextView mUpdateButton;
    private TextView mInstallButton;
    private Switch mSwitchActive;
    private Switch mSwitchAutoupdates;
    private ProgressDialog mRemovePluginProgressDialog;

    protected TextView mDescriptionTextView;
    protected ImageView mDescriptionChevron;
    protected TextView mInstallationTextView;
    protected ImageView mInstallationChevron;
    protected TextView mWhatsNewTextView;
    protected ImageView mWhatsNewChevron;
    protected TextView mFaqTextView;
    protected ImageView mFaqChevron;

    private WPNetworkImageView mImageBanner;
    private WPNetworkImageView mImageIcon;

    private boolean mIsConfiguringPlugin;
    private boolean mIsUpdatingPlugin;
    private boolean mIsRemovingPlugin;
    protected boolean mIsShowingRemovePluginConfirmationDialog;

    // These flags reflects the UI state
    protected boolean mIsActive;
    protected boolean mIsAutoUpdateEnabled;

    @Inject PluginStore mPluginStore;
    @Inject Dispatcher mDispatcher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getApplication()).component().inject(this);
        mDispatcher.register(this);

        if (savedInstanceState == null) {
            mSite = (SiteModel) getIntent().getSerializableExtra(WordPress.SITE);
            mSlug = getIntent().getStringExtra(KEY_PLUGIN_SLUG);
        } else {
            mSite = (SiteModel) savedInstanceState.getSerializable(WordPress.SITE);
            mSlug = savedInstanceState.getString(KEY_PLUGIN_SLUG);
        }

        if (mSite == null) {
            ToastUtils.showToast(this, R.string.blog_not_found);
            finish();
            return;
        }

        if (TextUtils.isEmpty(mSlug)) {
            ToastUtils.showToast(this, R.string.plugin_not_found);
            finish();
            return;
        }

        refreshPluginFromStore();

        if (savedInstanceState == null) {
            mIsActive = mDualPlugin.getSitePlugin() != null && mDualPlugin.getSitePlugin().isActive();
            mIsAutoUpdateEnabled = getSitePlugin() != null && getSitePlugin().isAutoUpdateEnabled();
        } else {
            mIsConfiguringPlugin = savedInstanceState.getBoolean(KEY_IS_CONFIGURING_PLUGIN);
            mIsUpdatingPlugin = savedInstanceState.getBoolean(KEY_IS_UPDATING_PLUGIN);
            mIsRemovingPlugin = savedInstanceState.getBoolean(KEY_IS_REMOVING_PLUGIN);
            mIsActive = savedInstanceState.getBoolean(KEY_IS_ACTIVE);
            mIsAutoUpdateEnabled = savedInstanceState.getBoolean(KEY_IS_AUTO_UPDATE_ENABLED);
            mIsShowingRemovePluginConfirmationDialog =
                    savedInstanceState.getBoolean(KEY_IS_SHOWING_REMOVE_PLUGIN_CONFIRMATION_DIALOG);
        }

        setContentView(R.layout.plugin_detail_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(null);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setElevation(0);
        }

        setupViews();

        if (mIsShowingRemovePluginConfirmationDialog) {
            // Show remove plugin confirmation dialog if it's dismissed while activity is re-created
            confirmRemovePlugin();
        } else if (mIsRemovingPlugin) {
            // Show remove plugin progress dialog if it's dismissed while activity is re-created
            showRemovePluginProgressDialog();
        }
    }

    @Override
    protected void onDestroy() {
        // Even though the progress dialog will be destroyed, when it's re-created sometimes the spinner
        // would get stuck. This seems to be helping with that.
        if (mRemovePluginProgressDialog != null && mRemovePluginProgressDialog.isShowing()) {
            mRemovePluginProgressDialog.cancel();
        }
        mDispatcher.unregister(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.plugin_detail, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean showTrash = canPluginBeDisabledOrRemoved();
        menu.findItem(R.id.menu_trash).setVisible(showTrash);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isPluginStateChangedSinceLastConfigurationDispatch()) {
                // It looks like we have some unsaved changes, we need to force a configuration dispatch since the
                // user is leaving the page
                dispatchConfigurePluginAction(true);
            }
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.menu_trash) {
            confirmRemovePlugin();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(WordPress.SITE, mSite);
        outState.putString(KEY_PLUGIN_SLUG, mSlug);
        outState.putBoolean(KEY_IS_CONFIGURING_PLUGIN, mIsConfiguringPlugin);
        outState.putBoolean(KEY_IS_UPDATING_PLUGIN, mIsUpdatingPlugin);
        outState.putBoolean(KEY_IS_REMOVING_PLUGIN, mIsRemovingPlugin);
        outState.putBoolean(KEY_IS_ACTIVE, mIsActive);
        outState.putBoolean(KEY_IS_AUTO_UPDATE_ENABLED, mIsAutoUpdateEnabled);
        outState.putBoolean(KEY_IS_SHOWING_REMOVE_PLUGIN_CONFIRMATION_DIALOG, mIsShowingRemovePluginConfirmationDialog);
    }

    // UI Helpers

    private void setupViews() {
        mContainer = findViewById(R.id.plugin_detail_container);
        mTitleTextView = findViewById(R.id.text_title);
        mByLineTextView = findViewById(R.id.text_byline);
        mVersionTopTextView = findViewById(R.id.plugin_version_top);
        mVersionBottomTextView = findViewById(R.id.plugin_version_bottom);
        mUpdateButton = findViewById(R.id.plugin_btn_update);
        mInstallButton = findViewById(R.id.plugin_btn_install);
        mSwitchActive = findViewById(R.id.plugin_state_active);
        mSwitchAutoupdates = findViewById(R.id.plugin_state_autoupdates);
        mImageBanner = findViewById(R.id.image_banner);
        mImageIcon = findViewById(R.id.image_icon);

        // vector drawable has to be assigned at runtime for backwards compatibility
        Drawable rightDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_info_outline_grey_dark_18dp);
        mVersionTopTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, rightDrawable, null);

        mDescriptionTextView = findViewById(R.id.plugin_description_text);
        mDescriptionChevron = findViewById(R.id.plugin_description_chevron);
        findViewById(R.id.plugin_description_container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleText(mDescriptionTextView, mDescriptionChevron);
            }
        });

        mInstallationTextView = findViewById(R.id.plugin_installation_text);
        mInstallationChevron = findViewById(R.id.plugin_installation_chevron);
        findViewById(R.id.plugin_installation_container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleText(mInstallationTextView, mInstallationChevron);
            }
        });

        mWhatsNewTextView = findViewById(R.id.plugin_whatsnew_text);
        mWhatsNewChevron = findViewById(R.id.plugin_whatsnew_chevron);
        findViewById(R.id.plugin_whatsnew_container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleText(mWhatsNewTextView, mWhatsNewChevron);
            }
        });

        // expand description if this plugin isn't installed, otherwise expand "what's new" if
        // this is an installed plugin and there's an update available
        if (getSitePlugin() == null) {
            toggleText(mDescriptionTextView, mDescriptionChevron);
        } else if (PluginUtils.isUpdateAvailable(getSitePlugin(), getWPOrgPlugin())) {
            toggleText(mWhatsNewTextView, mWhatsNewChevron);
        }

        mFaqTextView = findViewById(R.id.plugin_faq_text);
        mFaqChevron = findViewById(R.id.plugin_faq_chevron);
        findViewById(R.id.plugin_faq_container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleText(mFaqTextView, mFaqChevron);
            }
        });

        mVersionTopTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPluginInfoPopup();
            }
        });

        mSwitchActive.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (compoundButton.isPressed()) {
                    mIsActive = b;
                    dispatchConfigurePluginAction(false);
                }
            }
        });

        mSwitchAutoupdates.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (compoundButton.isPressed()) {
                    mIsAutoUpdateEnabled = b;
                    dispatchConfigurePluginAction(false);
                }
            }
        });

        View settingsView = findViewById(R.id.plugin_settings_page);
        if (canShowSettings()) {
            settingsView.setVisibility(View.VISIBLE);
            settingsView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openUrl(getSitePlugin().getSettingsUrl());
                }
            });
        } else {
            settingsView.setVisibility(View.GONE);
        }

        findViewById(R.id.plugin_wp_org_page).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openUrl(getWpOrgPluginUrl());
            }
        });

        findViewById(R.id.plugin_home_page).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = null;
                if (getSitePlugin() != null) {
                    url = getSitePlugin().getPluginUrl();
                } else if (getWPOrgPlugin() != null) {
                    url = getWPOrgPlugin().getHomepageUrl();
                }
                openUrl(url);
            }
        });

        findViewById(R.id.read_reviews_container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openUrl(getWpOrgReviewsUrl());
            }
        });

        // set the height of the gradient scrim that appears atop the banner image
        int toolbarHeight = DisplayUtils.getActionBarHeight(this);
        ImageView imgScrim = findViewById(R.id.image_gradient_scrim);
        imgScrim.getLayoutParams().height = toolbarHeight * 2;

        refreshViews();
    }

    private void refreshViews() {
        boolean hasPlugin = getSitePlugin() != null || getWPOrgPlugin() != null;
        View scrollView = findViewById(R.id.scroll_view);
        if (hasPlugin && scrollView.getVisibility() != View.VISIBLE) {
            AniUtils.fadeIn(scrollView, AniUtils.Duration.MEDIUM);
        } else if (!hasPlugin) {
            scrollView.setVisibility(View.GONE);
        }

        if (getWPOrgPlugin() != null) {
            mTitleTextView.setText(getWPOrgPlugin().getName());
            mImageBanner.setImageUrl(getWPOrgPlugin().getBanner(), PHOTO);
            mImageIcon.setImageUrl(getWPOrgPlugin().getIcon(), PLUGIN_ICON);

            setCollapsibleHtmlText(mDescriptionTextView, getWPOrgPlugin().getDescriptionAsHtml());
            setCollapsibleHtmlText(mInstallationTextView, getWPOrgPlugin().getInstallationInstructionsAsHtml());
            setCollapsibleHtmlText(mWhatsNewTextView, getWPOrgPlugin().getWhatsNewAsHtml());
            setCollapsibleHtmlText(mFaqTextView, getWPOrgPlugin().getFaqAsHtml());

            mByLineTextView.setMovementMethod(WPLinkMovementMethod.getInstance());
            mByLineTextView.setText(Html.fromHtml(getWPOrgPlugin().getAuthorAsHtml()));
        } else if (getSitePlugin() != null) {
            mTitleTextView.setText(getSitePlugin().getDisplayName());

            if (TextUtils.isEmpty(getSitePlugin().getAuthorUrl())) {
                mByLineTextView.setText(String.format(getString(R.string.plugin_byline), getSitePlugin().getAuthorName()));
            } else {
                String authorLink = "<a href='" + getSitePlugin().getAuthorUrl() + "'>" + getSitePlugin().getAuthorName() + "</a>";
                String byline = String.format(getString(R.string.plugin_byline), authorLink);
                mByLineTextView.setMovementMethod(WPLinkMovementMethod.getInstance());
                mByLineTextView.setText(Html.fromHtml(byline));
            }
        }

        if (!canPluginBeDisabledOrRemoved()) {
            findViewById(R.id.plugin_state_active_container).setVisibility(View.GONE);
        } else if (getSitePlugin() != null) {
            mSwitchActive.setChecked(mIsActive);
        }
        mSwitchAutoupdates.setChecked(mIsAutoUpdateEnabled);

        findViewById(R.id.plugin_card_site).setVisibility(getSitePlugin() != null ? View.VISIBLE : View.GONE);
        refreshPluginVersionViews();
        refreshRatingsViews();
    }

    private void setCollapsibleHtmlText(@NonNull TextView textView, @Nullable String htmlText) {
        if (!TextUtils.isEmpty(htmlText)) {
            textView.setTextColor(getResources().getColor(R.color.grey_dark));
            textView.setMovementMethod(WPLinkMovementMethod.getInstance());
            textView.setText(Html.fromHtml(htmlText));
        } else {
            textView.setTextColor(getResources().getColor(R.color.grey_lighten_10));
            textView.setText(R.string.plugin_empty_text);
        }
    }

    private void refreshPluginVersionViews() {
        if (getSitePlugin() != null) {
            String pluginVersion = TextUtils.isEmpty(getSitePlugin().getVersion()) ? "?" : getSitePlugin().getVersion();
            String installedVersion;

            if (PluginUtils.isUpdateAvailable(getSitePlugin(), getWPOrgPlugin())) {
                installedVersion = String.format(getString(R.string.plugin_installed_version), pluginVersion);
                String availableVersion = String.format(getString(R.string.plugin_available_version), getWPOrgPlugin().getVersion());
                mVersionTopTextView.setText(availableVersion);
                mVersionBottomTextView.setText(installedVersion);
                mVersionBottomTextView.setVisibility(View.VISIBLE);
            } else {
                installedVersion = String.format(getString(R.string.plugin_version), pluginVersion);
                mVersionTopTextView.setText(installedVersion);
                mVersionBottomTextView.setVisibility(View.GONE);
            }
        } else if (getWPOrgPlugin() != null) {
            String version = String.format(getString(R.string.plugin_version), getWPOrgPlugin().getVersion());
            mVersionTopTextView.setText(version);
            mVersionBottomTextView.setVisibility(View.GONE);
        }

        refreshUpdateVersionViews();
    }

    private void refreshUpdateVersionViews() {
        if (getSitePlugin() != null) {
            mInstallButton.setVisibility(View.GONE);
            boolean isUpdateAvailable = PluginUtils.isUpdateAvailable(getSitePlugin(), getWPOrgPlugin());
            boolean canUpdate = isUpdateAvailable && !mIsUpdatingPlugin;
            mUpdateButton.setVisibility(canUpdate ? View.VISIBLE : View.GONE);
            findViewById(R.id.plugin_installed).setVisibility(isUpdateAvailable || mIsUpdatingPlugin ? View.GONE : View.VISIBLE);
            if (canUpdate) {
                mUpdateButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dispatchUpdatePluginAction();
                    }
                });
            }
        } else if (getWPOrgPlugin() != null) {
            mUpdateButton.setVisibility(View.GONE);
            mInstallButton.setVisibility(View.VISIBLE);
            mInstallButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dispatchInstallPluginAction();
                }
            });
        }

        findViewById(R.id.plugin_update_progress_bar).setVisibility(mIsUpdatingPlugin ? View.VISIBLE : View.GONE);
    }

    private void refreshRatingsViews() {
        if (getWPOrgPlugin() == null) return;

        int numRatingsTotal = getWPOrgPlugin().getNumberOfRatings();

        TextView txtNumRatings = findViewById(R.id.text_num_ratings);
        String numRatings = FormatUtils.formatInt(numRatingsTotal);
        txtNumRatings.setText(String.format(getString(R.string.plugin_num_ratings), numRatings));

        TextView txtNumDownloads = findViewById(R.id.text_num_downloads);
        String numDownloads = FormatUtils.formatInt(getWPOrgPlugin().getDownloadCount());
        txtNumDownloads.setText(String.format(getString(R.string.plugin_num_downloads), numDownloads));

        setRatingsProgressBar(R.id.progress5, getWPOrgPlugin().getNumberOfRatingsOfFive(), numRatingsTotal);
        setRatingsProgressBar(R.id.progress4, getWPOrgPlugin().getNumberOfRatingsOfFour(), numRatingsTotal);
        setRatingsProgressBar(R.id.progress3, getWPOrgPlugin().getNumberOfRatingsOfThree(), numRatingsTotal);
        setRatingsProgressBar(R.id.progress2, getWPOrgPlugin().getNumberOfRatingsOfTwo(), numRatingsTotal);
        setRatingsProgressBar(R.id.progress1, getWPOrgPlugin().getNumberOfRatingsOfOne(), numRatingsTotal);

        RatingBar ratingBar = findViewById(R.id.rating_bar);
        ratingBar.setRating(PluginUtils.getAverageStarRating(getWPOrgPlugin()));
    }

    private void setRatingsProgressBar(@IdRes int progressResId, int numRatingsForStar, int numRatingsTotal) {
        ProgressBar bar = findViewById(progressResId);
        bar.setMax(numRatingsTotal);
        bar.setProgress(numRatingsForStar);
    }

    private static final String KEY_LABEL = "label";
    private static final String KEY_TEXT = "text";

    private String timespanFromUpdateDate(@NonNull String lastUpdated) {
        // ex: 2017-12-13 2:55pm GMT
        if (lastUpdated.endsWith(" GMT")) {
            lastUpdated = lastUpdated.substring(0, lastUpdated.length() - 4);
        }
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        try {
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = formatter.parse(lastUpdated);
            return DateTimeUtils.javaDateToTimeSpan(date, this);
        } catch (ParseException var2) {
            return "?";
        }
    }

    protected void showPluginInfoPopup() {
        if (getWPOrgPlugin() == null) return;

        List<Map<String, String>> data = new ArrayList<>();
        int[] to = { R.id.text1, R.id.text2 };
        String[] from = { KEY_LABEL, KEY_TEXT };
        String[] labels = {
                getString(R.string.plugin_info_version),
                getString(R.string.plugin_info_lastupdated),
                getString(R.string.plugin_info_requires_version),
                getString(R.string.plugin_info_your_version)
        };

        Map<String,String> mapVersion = new HashMap<>();
        mapVersion.put(KEY_LABEL, labels[0]);
        mapVersion.put(KEY_TEXT, StringUtils.notNullStr(getWPOrgPlugin().getVersion()));
        data.add(mapVersion);

        Map<String,String> mapUpdated = new HashMap<>();
        mapUpdated.put(KEY_LABEL, labels[1]);
        mapUpdated.put(KEY_TEXT, timespanFromUpdateDate(StringUtils.notNullStr(getWPOrgPlugin().getLastUpdated())));
        data.add(mapUpdated);

        Map<String,String> mapRequiredVer = new HashMap<>();
        mapRequiredVer.put(KEY_LABEL, labels[2]);
        mapRequiredVer.put(KEY_TEXT, StringUtils.notNullStr(getWPOrgPlugin().getRequiredWordPressVersion()));
        data.add(mapRequiredVer);

        Map<String,String> mapThisVer = new HashMap<>();
        mapThisVer.put(KEY_LABEL, labels[3]);
        mapThisVer.put(KEY_TEXT, !TextUtils.isEmpty(mSite.getSoftwareVersion()) ? mSite.getSoftwareVersion() : "?");
        data.add(mapThisVer);

        SimpleAdapter adapter = new SimpleAdapter(this,
                data,
                R.layout.plugin_info_row,
                from,
                to);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();

    }

    protected void toggleText(@NonNull final TextView textView, @NonNull ImageView chevron) {
        AniUtils.Duration duration = AniUtils.Duration.SHORT;
        boolean isExpanded = textView.getVisibility() == View.VISIBLE;
        if (isExpanded) {
            AniUtils.fadeOut(textView, duration);
        } else {
            AniUtils.fadeIn(textView, duration);
        }

        float startRotate = isExpanded ? -180f : 0f;
        float endRotate = isExpanded ? 0f : -180f;
        ObjectAnimator animRotate = ObjectAnimator.ofFloat(chevron, View.ROTATION, startRotate, endRotate);
        animRotate.setDuration(duration.toMillis(this));
        animRotate.start();
    }

    protected void openUrl(@Nullable String url) {
        if (url != null) {
            ActivityLauncher.openUrlExternal(this, url);
        }
    }

    private void confirmRemovePlugin() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Calypso_AlertDialog);
        builder.setTitle(getResources().getText(R.string.plugin_remove_dialog_title));
        String confirmationMessage = getString(R.string.plugin_remove_dialog_message,
                getPluginDisplayName(),
                SiteUtils.getSiteNameOrHomeURL(mSite));
        builder.setMessage(confirmationMessage);
        builder.setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mIsShowingRemovePluginConfirmationDialog = false;
                disableAndRemovePlugin();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mIsShowingRemovePluginConfirmationDialog = false;
            }
        });
        builder.setCancelable(true);
        builder.create();
        mIsShowingRemovePluginConfirmationDialog = true;
        builder.show();
    }

    private void showSuccessfulUpdateSnackbar() {
        if (getSitePlugin() == null) {
            return;
        }
        Snackbar.make(mContainer,
                getString(R.string.plugin_updated_successfully, getSitePlugin().getDisplayName()),
                Snackbar.LENGTH_LONG)
                .show();
    }

    private void showSuccessfulInstallSnackbar() {
        if (getWPOrgPlugin() == null) {
            return;
        }
        Snackbar.make(mContainer,
                getString(R.string.plugin_installed_successfully, getPluginDisplayName()),
                Snackbar.LENGTH_LONG)
                .show();
    }

    private void showUpdateFailedSnackbar() {
        Snackbar.make(mContainer,
                getString(R.string.plugin_updated_failed, getPluginDisplayName()),
                Snackbar.LENGTH_LONG)
                .setAction(R.string.retry, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dispatchUpdatePluginAction();
                    }
                })
                .show();
    }

    private void showInstallFailedSnackbar() {
        Snackbar.make(mContainer,
                getString(R.string.plugin_installed_failed, getPluginDisplayName()),
                Snackbar.LENGTH_LONG)
                .setAction(R.string.retry, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dispatchInstallPluginAction();
                    }
                })
                .show();
    }

    private void showPluginRemoveFailedSnackbar() {
        Snackbar.make(mContainer,
                getString(R.string.plugin_remove_failed, getPluginDisplayName()),
                Snackbar.LENGTH_LONG)
                .show();
    }

    private void showRemovePluginProgressDialog() {
        mRemovePluginProgressDialog = new ProgressDialog(this);
        mRemovePluginProgressDialog.setCancelable(false);
        mRemovePluginProgressDialog.setIndeterminate(true);
        // Even though we are deactivating the plugin to make sure it's disabled on the server side, since the user
        // sees that the plugin is disabled, it'd be confusing to say we are disabling the plugin
        String message = mIsActive
                ? getString(R.string.plugin_disable_progress_dialog_message, getPluginDisplayName())
                : getRemovingPluginMessage();
        mRemovePluginProgressDialog.setMessage(message);
        mRemovePluginProgressDialog.show();
    }

    private void cancelRemovePluginProgressDialog() {
        if (mRemovePluginProgressDialog != null && mRemovePluginProgressDialog.isShowing()) {
            mRemovePluginProgressDialog.cancel();
        }
    }

    // Network Helpers

    protected void dispatchConfigurePluginAction(boolean forceUpdate) {
        if (!NetworkUtils.checkConnection(this)) {
            return;
        }
        if (!forceUpdate && mIsConfiguringPlugin) {
            return;
        }
        if (getSitePlugin() == null) {
            return;
        }
        mIsConfiguringPlugin = true;
        getSitePlugin().setIsActive(mIsActive);
        getSitePlugin().setIsAutoUpdateEnabled(mIsAutoUpdateEnabled);
        mDispatcher.dispatch(PluginActionBuilder.newConfigureSitePluginAction(
                new ConfigureSitePluginPayload(mSite, getSitePlugin())));
    }

    protected void dispatchUpdatePluginAction() {
        if (!NetworkUtils.checkConnection(this)) {
            return;
        }
        if (!PluginUtils.isUpdateAvailable(getSitePlugin(), getWPOrgPlugin()) || mIsUpdatingPlugin) {
            return;
        }

        mIsUpdatingPlugin = true;
        refreshUpdateVersionViews();
        UpdateSitePluginPayload payload = new UpdateSitePluginPayload(mSite, getSitePlugin());
        mDispatcher.dispatch(PluginActionBuilder.newUpdateSitePluginAction(payload));
    }

    protected void dispatchInstallPluginAction() {
        if (!NetworkUtils.checkConnection(this)) {
            return;
        }

        mIsUpdatingPlugin = true;
        refreshUpdateVersionViews();
        PluginStore.InstallSitePluginPayload payload = new InstallSitePluginPayload(mSite, mSlug);
        mDispatcher.dispatch(PluginActionBuilder.newInstallSitePluginAction(payload));
    }

    protected void dispatchRemovePluginAction() {
        if (!NetworkUtils.checkConnection(this)) {
            return;
        }
        mRemovePluginProgressDialog.setMessage(getRemovingPluginMessage());
        DeleteSitePluginPayload payload = new DeleteSitePluginPayload(mSite, getSitePlugin());
        mDispatcher.dispatch(PluginActionBuilder.newDeleteSitePluginAction(payload));
    }

    protected void disableAndRemovePlugin() {
        // This is only a sanity check as the remove button should not be visible. It's important to disable removing
        // plugins in certain cases, so we should still make this sanity check
        if (!canPluginBeDisabledOrRemoved()) {
            return;
        }
        // We need to make sure that plugin is disabled before attempting to remove it
        mIsRemovingPlugin = true;
        showRemovePluginProgressDialog();
        mIsActive = false;
        dispatchConfigurePluginAction(false);
    }

    // FluxC callbacks

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSitePluginConfigured(OnSitePluginConfigured event) {
        if (isFinishing()) return;

        if (mSite.getId() != event.site.getId() || !mSlug.equals(event.slug)) {
            // Not the event we are interested in
            return;
        }

        mIsConfiguringPlugin = false;
        if (event.isError()) {
            // The plugin was already removed in remote, there is no need to show an error to the user
            if (mIsRemovingPlugin &&
                    event.error.type == PluginStore.ConfigureSitePluginErrorType.UNKNOWN_PLUGIN) {
                // We still need to dispatch the remove plugin action to remove the local copy
                // and complete the flow gracefully
                // We can ignore `!mSitePlugin.isActive()` check here since the plugin is not installed anymore on remote
                dispatchRemovePluginAction();
                return;
            }

            ToastUtils.showToast(this, getString(R.string.plugin_configuration_failed, event.error.message));

            // Refresh the UI to plugin's last known state
            refreshPluginFromStore();
            if (getSitePlugin() == null) {
                return;
            }
            mIsActive = getSitePlugin().isActive();
            mIsAutoUpdateEnabled = getSitePlugin().isAutoUpdateEnabled();
            refreshViews();

            if (mIsRemovingPlugin) {
                mIsRemovingPlugin = false;
                cancelRemovePluginProgressDialog();
                showPluginRemoveFailedSnackbar();
            }
            return;
        }

        refreshPluginFromStore();
        if (getSitePlugin() == null) {
            return;
        }

        // The plugin state has been changed while a configuration network call is going on, we need to dispatch another
        // configure plugin action since we don't allow multiple configure actions to happen at the same time
        // This might happen either because user changed the state or a remove plugin action has started
        if (isPluginStateChangedSinceLastConfigurationDispatch()) {
            // The plugin's state in UI has priority over the one in DB as we'll dispatch another configuration change
            // to make sure UI is reflected correctly in network and DB
            dispatchConfigurePluginAction(false);
        } else if (mIsRemovingPlugin && !getSitePlugin().isActive()) {
            // We don't want to trigger the remove plugin action before configuration changes are reflected in network
            dispatchRemovePluginAction();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWPOrgPluginFetched(PluginStore.OnWPOrgPluginFetched event) {
        if (isFinishing()) return;

        if (event.isError()) {
            AppLog.e(AppLog.T.PLUGINS, "An error occurred while fetching wporg plugin with type: "
                    + event.error.type);
        } else {
            refreshPluginFromStore();
            refreshViews();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSitePluginUpdated(OnSitePluginUpdated event) {
        if (isFinishing()) return;

        if (mSite.getId() != event.site.getId() || !mSlug.equals(event.slug)) {
            // Not the event we are interested in
            return;
        }

        mIsUpdatingPlugin = false;
        if (event.isError()) {
            AppLog.e(AppLog.T.PLUGINS, "An error occurred while updating the plugin with type: "
                    + event.error.type);
            refreshPluginVersionViews();
            showUpdateFailedSnackbar();
            return;
        }

        refreshPluginFromStore();
        refreshViews();
        showSuccessfulUpdateSnackbar();

        AnalyticsUtils.trackWithSiteDetails(AnalyticsTracker.Stat.PLUGIN_UPDATED, mSite);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void OnSitePluginInstalled(OnSitePluginInstalled event) {
        if (isFinishing()) return;

        if (mSite.getId() != event.site.getId() || getWPOrgPlugin() == null
                || !getWPOrgPlugin().getName().equals(event.pluginName)) {
            // Not the event we are interested in
            return;
        }

        mIsUpdatingPlugin = false;
        if (event.isError()) {
            AppLog.e(AppLog.T.PLUGINS, "An error occurred while installing the plugin with type: "
                    + event.error.type);
            refreshPluginVersionViews();
            showInstallFailedSnackbar();
            return;
        }

        refreshPluginFromStore();
        refreshViews();
        showSuccessfulInstallSnackbar();
        invalidateOptionsMenu();

        AnalyticsUtils.trackWithSiteDetails(AnalyticsTracker.Stat.PLUGIN_INSTALLED, mSite);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSitePluginDeleted(OnSitePluginDeleted event) {
        if (isFinishing()) return;

        if (mSite.getId() != event.site.getId() || !mSlug.equals(event.slug)) {
            // Not the event we are interested in
            return;
        }

        mIsRemovingPlugin = false;
        cancelRemovePluginProgressDialog();
        if (event.isError()) {
            AppLog.e(AppLog.T.PLUGINS, "An error occurred while removing the plugin with type: "
                    + event.error.type);
            String toastMessage = getString(R.string.plugin_updated_failed_detailed,
                    getPluginDisplayName(), event.error.message);
            ToastUtils.showToast(this, toastMessage, Duration.LONG);
            return;
        }
        AnalyticsUtils.trackWithSiteDetails(AnalyticsTracker.Stat.PLUGIN_REMOVED, mSite);

        // Plugin removed we need to go back to the plugin list
        String toastMessage = getString(R.string.plugin_removed_successfully, getPluginDisplayName());
        ToastUtils.showToast(this, toastMessage, Duration.LONG);
        finish();
    }

    // Utils

    private void refreshPluginFromStore() {
        mDualPlugin = mPluginStore.getDualPluginBySlug(mSite, mSlug);
    }

    protected @Nullable SitePluginModel getSitePlugin() {
        return mDualPlugin != null ? mDualPlugin.getSitePlugin() : null;
    }

    protected @Nullable WPOrgPluginModel getWPOrgPlugin() {
        return mDualPlugin != null ? mDualPlugin.getWPOrgPlugin() : null;
    }

    private @Nullable String getPluginDisplayName() {
        if (getSitePlugin() != null) {
            return getSitePlugin().getDisplayName();
        }
        if (getWPOrgPlugin() != null) {
            return getWPOrgPlugin().getName();
        }
        return null;
    }

    protected String getWpOrgPluginUrl() {
        return "https://wordpress.org/plugins/" + mSlug;
    }

    protected String getWpOrgReviewsUrl() {
        return "https://wordpress.org/plugins/" + mSlug + "/#reviews";
    }

    private String getRemovingPluginMessage() {
        return getString(R.string.plugin_remove_progress_dialog_message, getPluginDisplayName());
    }

    private boolean canPluginBeDisabledOrRemoved() {
        if (getSitePlugin() == null) {
            return false;
        }

        String pluginName = getSitePlugin().getName();
        // Disable removing jetpack as the site will stop working in the client
        if (pluginName.equals("jetpack/jetpack")) {
            return false;
        }
        // Disable removing akismet and vaultpress for AT sites
        return !mSite.isAutomatedTransfer()
                || (!pluginName.equals("akismet/akismet") && !pluginName.equals("vaultpress/vaultpress"));
    }

    // only show settings for active plugins on .org sites
    private boolean canShowSettings() {
        return getSitePlugin() != null
                && getSitePlugin().isActive()
                && !mSite.isJetpackConnected()
                && !TextUtils.isEmpty(getSitePlugin().getSettingsUrl());
    }

    private boolean isPluginStateChangedSinceLastConfigurationDispatch() {
        if (getSitePlugin() == null) {
            return false;
        }
        return getSitePlugin().isActive() != mIsActive || getSitePlugin().isAutoUpdateEnabled() != mIsAutoUpdateEnabled;
    }
}
