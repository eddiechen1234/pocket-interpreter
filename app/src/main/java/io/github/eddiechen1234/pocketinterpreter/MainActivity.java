package io.github.eddiechen1234.pocketinterpreter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.icu.text.Transliterator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 隨身口譯 Pocket Interpreter — text, voice and face-to-face conversation translation.
 *
 * Translation: Google ML Kit on-device models (downloaded once per language, then offline);
 * MyMemory (api.mymemory.translated.net) is used only while a model is still downloading
 * or for languages ML Kit does not support. No ads, no analytics, no account.
 */
public class MainActivity extends Activity {

    // {code used by translate APIs / TTS / speech recognizer, display name}
    static final String[][] LANGS = {
        {"zh-TW", "中文（繁體）"}, {"en", "英文"}, {"ja", "日文"}, {"ko", "韓文"},
        {"zh-CN", "中文（簡體）"}, {"th", "泰文"}, {"vi", "越南文"}, {"id", "印尼文"},
        {"ms", "馬來文"}, {"tl", "菲律賓文"}, {"fr", "法文"}, {"de", "德文"},
        {"es", "西班牙文"}, {"it", "義大利文"}, {"pt", "葡萄牙文"}, {"ru", "俄文"},
        {"ar", "阿拉伯文"}, {"hi", "印地文"}, {"tr", "土耳其文"}, {"nl", "荷蘭文"},
        {"pl", "波蘭文"}, {"sv", "瑞典文"}, {"da", "丹麥文"}, {"fi", "芬蘭文"},
        {"no", "挪威文"}, {"cs", "捷克文"}, {"el", "希臘文"}, {"he", "希伯來文"},
        {"hu", "匈牙利文"}, {"ro", "羅馬尼亞文"}, {"uk", "烏克蘭文"}, {"bn", "孟加拉文"},
        {"ta", "坦米爾文"}, {"ur", "烏爾都文"}, {"fa", "波斯文"}, {"my", "緬甸文"},
        {"km", "高棉文"}, {"lo", "寮文"}, {"ne", "尼泊爾文"}, {"mn", "蒙古文"},
        {"sw", "史瓦希里文"}
    };

    // Source language of the phrasebook is Traditional Chinese (index 0).
    static final String[] PHRASES = {
        "你好", "早安", "晚安", "謝謝", "不好意思", "請問多少錢？", "廁所在哪裡？",
        "我聽不懂", "可以幫我一下嗎？", "你會說英文嗎？", "很高興認識你", "再見",
        "飯店在哪裡？", "我想要一杯咖啡", "請給我帳單", "請幫我叫計程車", "現在幾點？",
        "火車站在哪裡？", "我是觀光客", "這是我的護照", "可以給我菜單嗎？",
        "哪裡可以換錢？", "可以算便宜一點嗎？", "可以刷卡嗎？", "我對花生過敏",
        "請帶我去這個地址", "附近有藥局嗎？", "請說慢一點"
    };

    // Palette
    static final int C_BG = 0xFFF4F6FA, C_CARD = 0xFFFFFFFF, C_PRIMARY = 0xFF2563EB,
            C_PRIMARY_DARK = 0xFF1D4ED8, C_ACCENT = 0xFF0EA5A4, C_TEXT = 0xFF111827,
            C_SUB = 0xFF6B7280, C_LINE = 0xFFE5E7EB, C_DANGER = 0xFFDC2626, C_CHIP = 0xFFEFF4FF;

    static final int PERM_REQ = 1;

    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService net = Executors.newSingleThreadExecutor();
    SharedPreferences prefs;
    TextToSpeech tts;
    boolean ttsReady = false;
    SpeechRecognizer sr;
    TextView listeningButton; // the mic button that is currently active (null = idle)
    String listeningLabel;

    TextView status;
    TextView[] tabButtons = new TextView[4];
    View[] tabViews = new View[4];

    // Text tab
    Spinner tFrom, tTo;
    EditText input;
    TextView tResult;
    // Voice tab
    Spinner vFrom, vTo;
    TextView vHeard, vResult, vMic;
    // Conversation tab
    Spinner cA, cB;
    TextView cTextA, cTextB, cMicA, cMicB;
    LinearLayout panelA, panelB;
    // Phrasebook
    Spinner pTo;
    TextView pSrc, pResult;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vt", MODE_PRIVATE);
        // Draw edge-to-edge on every version (required from targetSdk 35); insets are applied in buildUi()
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        buildUi();
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            public void onInit(int s) {
                ttsReady = s == TextToSpeech.SUCCESS;
                if (!ttsReady) setStatus("語音朗讀引擎無法啟動", true);
            }
        });
        if (checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, PERM_REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERM_REQ) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        setStatus(ok ? "已取得麥克風權限" : "沒有麥克風權限，語音功能無法使用", !ok);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopListening();
        if (tts != null) tts.stop();
    }

    @Override
    protected void onDestroy() {
        if (sr != null) { sr.destroy(); sr = null; }
        if (tts != null) tts.shutdown();
        net.shutdownNow();
        synchronized (this) {
            for (Translator t : translators.values()) t.close();
            translators.clear();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    GradientDrawable round(int color, float radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    StateListDrawable pressable(int color, int pressed, float radiusDp, int stroke) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(pressed, radiusDp, stroke));
        s.addState(new int[]{}, round(color, radiusDp, stroke));
        return s;
    }

    TextView button(String text, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(14), dp(11), dp(14), dp(11));
        b.setClickable(true);
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackground(pressable(C_PRIMARY, C_PRIMARY_DARK, 12, 0));
        } else {
            b.setTextColor(C_PRIMARY);
            b.setBackground(pressable(C_CHIP, 0xFFDDE7FF, 12, 0));
        }
        return b;
    }

    TextView micButton(String text) {
        TextView b = button(text, true);
        b.setTextSize(18);
        b.setPadding(dp(16), dp(18), dp(16), dp(18));
        b.setBackground(pressable(C_ACCENT, 0xFF0B8584, 40, 0));
        return b;
    }

    TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(C_SUB);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    TextView body(float size) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(C_TEXT);
        t.setTextIsSelectable(true);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(C_CARD, 16, C_LINE));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setElevation(dp(1));
        return c;
    }

    LinearLayout.LayoutParams mw(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    LinearLayout.LayoutParams weight(float w, int leftDp) {
        // w == 0 means "natural width"; a 0px width with no weight would make the view invisible
        LinearLayout.LayoutParams lp = w > 0
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(leftDp);
        return lp;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    Spinner langSpinner(final String key, int def) {
        Spinner s = new Spinner(this);
        String[] names = new String[LANGS.length];
        for (int i = 0; i < LANGS.length; i++) names[i] = LANGS[i][1];
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(ad);
        int saved = prefs.getInt(key, def);
        s.setSelection(saved >= 0 && saved < LANGS.length ? saved : def);
        s.setBackground(round(C_CHIP, 12, 0));
        s.setPadding(dp(4), dp(2), dp(4), dp(2));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                prefs.edit().putInt(key, pos).apply();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        return s;
    }

    /** "from  ⇄  to" row. */
    LinearLayout langRow(Spinner a, Spinner b) {
        LinearLayout r = row();
        r.addView(a, weight(1, 0));
        TextView swap = button("⇄", false);
        swap.setTextSize(18);
        swap.setPadding(dp(12), dp(6), dp(12), dp(6));
        swap.setOnClickListener(swapListener(a, b));
        r.addView(swap, weight(0, 8));
        r.addView(b, weight(1, 8));
        return r;
    }

    View.OnClickListener swapListener(final Spinner a, final Spinner b) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                int x = a.getSelectedItemPosition();
                a.setSelection(b.getSelectedItemPosition());
                b.setSelection(x);
            }
        };
    }

    ScrollView scroll(View child) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(child);
        return sv;
    }

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // Header
        final LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(C_PRIMARY);
        header.setPadding(dp(20), dp(16), dp(20), dp(12));
        LinearLayout titleRow = row();
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("可離線翻譯・無廣告・無追蹤 ・ v" + versionName());
        sub.setTextSize(12);
        sub.setTextColor(0xCCFFFFFF);
        titles.addView(sub);
        titleRow.addView(titles, weight(1, 0));
        TextView info = new TextView(this);
        info.setText("ⓘ");
        info.setTextSize(22);
        info.setTextColor(Color.WHITE);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(10), dp(4), dp(4), dp(4));
        info.setContentDescription("關於與隱私權");
        info.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showAbout(); }
        });
        titleRow.addView(info);
        header.addView(titleRow);

        // Tabs (segmented control)
        LinearLayout tabs = row();
        tabs.setBackground(round(0x33FFFFFF, 14, 0));
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] names = {"文字", "語音", "對話", "常用語"};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            TextView b = new TextView(this);
            b.setText(names[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(15);
            b.setPadding(0, dp(8), 0, dp(8));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { selectTab(t); }
            });
            tabButtons[i] = b;
            tabs.addView(b, weight(1, 0));
        }
        header.addView(tabs, mw(12));
        root.addView(header);

        tabViews[0] = buildTextTab();
        tabViews[1] = buildVoiceTab();
        tabViews[2] = buildConversationTab();
        tabViews[3] = buildPhraseTab();
        FrameLayout content = new FrameLayout(this);
        for (View v : tabViews) content.addView(v);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(C_SUB);
        status.setBackgroundColor(C_CARD);
        status.setPadding(dp(16), dp(10), dp(16), dp(10));
        status.setText("就緒");
        root.addView(status);

        // Edge-to-edge: header color runs under the status bar, content stays clear of bars, cutouts and keyboard
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                    android.graphics.Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    android.graphics.Insets ime = in.getInsets(WindowInsets.Type.ime());
                    v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
                    header.setPadding(dp(20), dp(16) + bars.top, dp(20), dp(12));
                    return in;
                }
            });
        } else {
            getWindow().setStatusBarColor(C_PRIMARY_DARK); // older versions keep the classic, non edge-to-edge window
        }

        setContentView(root);
        selectTab(prefs.getInt("tab", 0));
    }

    void selectTab(int t) {
        if (t < 0 || t > 3) t = 0;
        stopListening();
        prefs.edit().putInt("tab", t).apply();
        for (int i = 0; i < tabViews.length; i++) {
            boolean on = i == t;
            tabViews[i].setVisibility(on ? View.VISIBLE : View.GONE);
            tabButtons[i].setTextColor(on ? C_PRIMARY : Color.WHITE);
            tabButtons[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabButtons[i].setBackground(on ? round(Color.WHITE, 10, 0) : null);
        }
        hideKeyboard();
    }

    View buildTextTab() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(16), dp(16), dp(16));

        tFrom = langSpinner("t_from", 0);
        tTo = langSpinner("t_to", 1);
        v.addView(langRow(tFrom, tTo));

        LinearLayout inCard = card();
        input = new EditText(this);
        input.setHint("輸入要翻譯的文字");
        input.setTextSize(17);
        input.setTextColor(C_TEXT);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setBackground(null);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inCard.addView(input);
        LinearLayout inActions = row();
        TextView clear = button("清除", false);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { input.setText(""); tResult.setText(""); }
        });
        inActions.addView(clear, weight(1, 0));
        TextView go = button("翻譯", true);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { doTextTranslate(); }
        });
        inActions.addView(go, weight(2, 8));
        inCard.addView(inActions, mw(8));
        v.addView(inCard, mw(12));

        LinearLayout outCard = card();
        outCard.addView(label("翻譯結果"));
        tResult = body(20);
        outCard.addView(tResult);
        outCard.addView(resultActions(tResult, tTo), mw(10));
        v.addView(outCard, mw(12));
        return scroll(v);
    }

    /** 朗讀 / 複製 buttons under a result. */
    LinearLayout resultActions(final TextView src, final Spinner lang) {
        LinearLayout r = row();
        TextView speak = button("🔊 朗讀", false);
        speak.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { speak(src.getText().toString(), lang.getSelectedItemPosition()); }
        });
        r.addView(speak, weight(1, 0));
        TextView copy = button("複製", false);
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copy(src.getText().toString()); }
        });
        r.addView(copy, weight(1, 8));
        return r;
    }

    View buildVoiceTab() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(16), dp(16), dp(16));

        vFrom = langSpinner("v_from", 0);
        vTo = langSpinner("v_to", 1);
        v.addView(langRow(vFrom, vTo));

        vMic = micButton("🎤 按一下開始說話");
        vMic.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                if (listeningButton == vMic) { stopListening(); return; }
                listen(vMic, vFrom.getSelectedItemPosition(), new Heard() {
                    public void on(String heard) {
                        vHeard.setText(heard);
                        vResult.setText("翻譯中…");
                        translateAsync(heard, vFrom.getSelectedItemPosition(), vTo.getSelectedItemPosition(), vResult, true);
                    }
                });
            }
        });
        v.addView(vMic, mw(16));

        LinearLayout heardCard = card();
        heardCard.addView(label("你說的"));
        vHeard = body(17);
        vHeard.setTextColor(C_SUB);
        heardCard.addView(vHeard);
        v.addView(heardCard, mw(16));

        LinearLayout outCard = card();
        outCard.addView(label("翻譯結果"));
        vResult = body(22);
        outCard.addView(vResult);
        outCard.addView(resultActions(vResult, vTo), mw(10));
        v.addView(outCard, mw(12));
        return scroll(v);
    }

    /**
     * Two-sided layout for a phone lying flat between two people.
     * Panel A (top) is rotated 180° for the person across the table; panel B (bottom) faces the phone holder.
     * Each panel always shows the latest message in that panel's own language.
     */
    View buildConversationTab() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));

        cA = langSpinner("c_a", 1);
        cB = langSpinner("c_b", 0);

        cTextA = body(22);
        cTextB = body(22);
        panelA = convPanel(cA, cTextA, true);
        panelB = convPanel(cB, cTextB, false);
        panelA.setRotation(prefs.getBoolean("c_flip", false) ? 0f : 180f);

        LinearLayout mid = row();
        mid.setPadding(0, dp(8), 0, dp(8));
        TextView flip = button("↕ 翻轉上方", false);
        flip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                boolean upright = panelA.getRotation() == 180f;
                panelA.setRotation(upright ? 0f : 180f);
                prefs.edit().putBoolean("c_flip", upright).apply();
            }
        });
        mid.addView(flip, weight(1, 0));
        TextView swap = button("⇄ 交換語言", false);
        swap.setOnClickListener(swapListener(cA, cB));
        mid.addView(swap, weight(1, 8));
        TextView clear = button("清除", false);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { cTextA.setText(""); cTextB.setText(""); }
        });
        mid.addView(clear, weight(1, 8));

        v.addView(panelA, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        v.addView(mid);
        v.addView(panelB, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return v;
    }

    LinearLayout convPanel(final Spinner lang, final TextView text, final boolean isA) {
        LinearLayout p = card();
        p.setBackground(round(isA ? 0xFFF0FDFA : C_CARD, 18, C_LINE));
        p.addView(lang);

        ScrollView sv = new ScrollView(this);
        text.setPadding(0, dp(10), 0, dp(10));
        text.setHint("按麥克風說話，翻譯會出現在對方那一側；點這裡的文字也可以再唸一次");
        text.setHintTextColor(0xFF9CA3AF);
        sv.addView(text);
        p.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // replay: this side's text, in this side's language (after the other person speaks it is the translation)
        final View.OnClickListener replay = new View.OnClickListener() {
            public void onClick(View x) {
                String t = text.getText().toString();
                if (t.trim().isEmpty()) { setStatus("這一側還沒有內容可以唸", true); return; }
                speak(t, lang.getSelectedItemPosition());
            }
        };
        text.setTextIsSelectable(false);
        text.setOnClickListener(replay);

        final TextView mic = micButton("🎤 說話");
        if (isA) cMicA = mic; else cMicB = mic;
        mic.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                if (listeningButton == mic) { stopListening(); return; }
                final Spinner other = isA ? cB : cA;
                final TextView otherText = isA ? cTextB : cTextA;
                listen(mic, lang.getSelectedItemPosition(), new Heard() {
                    public void on(String heard) {
                        text.setText(heard);           // own side: what I said, in my language
                        otherText.setText("翻譯中…");   // other side: translation in their language
                        translateAsync(heard, lang.getSelectedItemPosition(), other.getSelectedItemPosition(), otherText, true);
                    }
                });
            }
        });
        LinearLayout actions = row();
        actions.addView(mic, weight(2, 0));
        TextView again = button("🔊 再唸一次", false);
        again.setTextSize(16);
        again.setPadding(dp(10), dp(18), dp(10), dp(18));
        again.setBackground(pressable(C_CHIP, 0xFFDDE7FF, 40, 0));
        again.setOnClickListener(replay);
        actions.addView(again, weight(1, 8));
        p.addView(actions, mw(8));
        return p;
    }

    View buildPhraseTab() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout r = row();
        r.addView(label("翻成"));
        pTo = langSpinner("p_to", 1);
        r.addView(pTo, weight(1, 8));
        v.addView(r);

        LinearLayout res = card();
        pSrc = label("點下面任一句即可翻譯並朗讀");
        res.addView(pSrc);
        pResult = body(22);
        res.addView(pResult);
        res.addView(resultActions(pResult, pTo), mw(10));
        v.addView(res, mw(12));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (final String phrase : PHRASES) {
            TextView item = new TextView(this);
            item.setText(phrase);
            item.setTextSize(16);
            item.setTextColor(C_TEXT);
            item.setPadding(dp(16), dp(14), dp(16), dp(14));
            item.setBackground(pressable(C_CARD, C_CHIP, 12, C_LINE));
            item.setClickable(true);
            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    pSrc.setText(phrase);
                    pResult.setText("翻譯中…");
                    translateAsync(phrase, 0, pTo.getSelectedItemPosition(), pResult, true);
                }
            });
            list.addView(item, mw(8));
        }
        v.addView(list, mw(4));
        return scroll(v);
    }

    // ------------------------------------------------------------------ actions

    void setStatus(String s, boolean error) {
        if (status == null) return;
        status.setText(s);
        status.setTextColor(error ? C_DANGER : C_SUB);
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && input != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    void copy(String s) {
        if (s.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("translation", s));
        setStatus("已複製", false);
    }

    void doTextTranslate() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) { setStatus("請先輸入文字", true); return; }
        hideKeyboard();
        tResult.setText("翻譯中…");
        translateAsync(q, tFrom.getSelectedItemPosition(), tTo.getSelectedItemPosition(), tResult, true);
    }

    /** Translate in background, write result into target, optionally speak it. */
    void translateAsync(final String q, final int from, final int to, final TextView target, final boolean speakIt) {
        setStatus("翻譯中…", false);
        net.submit(new Runnable() {
            public void run() {
                String r = null, err = null;
                try { r = translate(q, LANGS[from][0], LANGS[to][0]); }
                catch (Exception e) { err = e.getMessage(); }
                final String result = r, error = err;
                main.post(new Runnable() {
                    public void run() {
                        if (result != null) {
                            target.setText(result);
                            setStatus("完成（" + LANGS[from][1] + " → " + LANGS[to][1] + "）", false);
                            if (speakIt) speak(result, to);
                        } else {
                            target.setText("");
                            setStatus("翻譯失敗：" + error, true);
                        }
                    }
                });
            }
        });
    }

    static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "PocketInterpreter (Android)");
        return c;
    }

    // ------------------------------------------------------------------ translation engine

    static final String PRIVACY_URL = "https://eddiechen1234.github.io/pocket-interpreter/privacy.html";
    static final String SOURCE_URL = "https://github.com/eddiechen1234/pocket-interpreter";

    /** Open translators, one per language pair (ML Kit keeps the model in memory while open). */
    final HashMap<String, Translator> translators = new HashMap<String, Translator>();
    /** Model downloads in progress, per language pair. */
    final HashMap<String, Task<Void>> downloads = new HashMap<String, Task<Void>>();

    /** App language tag -> ML Kit language, or null when ML Kit has no model for it. */
    static String mlkitLang(String tag) {
        int dash = tag.indexOf('-');
        return TranslateLanguage.fromLanguageTag(dash > 0 ? tag.substring(0, dash) : tag);
    }

    String langName(String tag) {
        for (String[] l : LANGS) if (l[0].equals(tag)) return l[1];
        return tag;
    }

    void statusLater(final String msg, final boolean error) {
        main.post(new Runnable() { public void run() { setStatus(msg, error); } });
    }

    synchronized Translator translator(String a, String b) {
        String k = a + "|" + b;
        Translator t = translators.get(k);
        if (t == null) {
            t = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(a).setTargetLanguage(b).build());
            translators.put(k, t);
        }
        return t;
    }

    static boolean modelReady(String lang) throws Exception {
        return Tasks.await(RemoteModelManager.getInstance()
                .isModelDownloaded(new TranslateRemoteModel.Builder(lang).build()), 10, TimeUnit.SECONDS);
    }

    /** Starts (once) the model download for a pair and reports progress in the status bar. */
    synchronized Task<Void> download(final String from, final String to, String a, String b) {
        final String k = a + "|" + b;
        Task<Void> d = downloads.get(k);
        if (d != null) return d;
        d = translator(a, b).downloadModelIfNeeded(new DownloadConditions.Builder().build());
        downloads.put(k, d);
        d.addOnCompleteListener(new OnCompleteListener<Void>() {
            public void onComplete(Task<Void> t) {
                synchronized (MainActivity.this) { downloads.remove(k); }
                if (t.isSuccessful()) setStatus("離線翻譯模型下載完成（" + langName(from) + " ↔ " + langName(to) + "），之後不用網路也能翻譯", false);
                else setStatus("離線模型下載失敗，先使用線上翻譯；下次翻譯會再試", true);
            }
        });
        return d;
    }

    /** Simplified -> Traditional Chinese (ML Kit only outputs Simplified). Null if ICU is unavailable (< Android 10). */
    static String toHant(String s) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try { return Transliterator.getInstance("Hans-Hant").transliterate(s); } catch (Throwable e) { return null; }
    }

    static String toHans(String s) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try { return Transliterator.getInstance("Hant-Hans").transliterate(s); } catch (Throwable e) { return null; }
    }

    /** Runs on the background executor. */
    String translate(String q, String from, String to) throws Exception {
        if (from.equals(to)) return q;
        String a = mlkitLang(from), b = mlkitLang(to);

        // 繁體 <-> 簡體 only needs a character conversion
        if (a != null && a.equals(b)) {
            String r = "zh-TW".equals(to) ? toHant(q) : toHans(q);
            return r != null ? r : myMemory(q, from, to);
        }
        // A language ML Kit does not cover (e.g. Burmese, Khmer, Lao, Nepali, Mongolian): online only
        if (a == null || b == null) return myMemory(q, from, to);

        Translator tr = translator(a, b);
        if (modelReady(a) && modelReady(b)) return finish(Tasks.await(tr.translate(q), 30, TimeUnit.SECONDS), q, from, to);

        // First use of this pair: download the model in the background, translate online meanwhile
        Task<Void> d = download(from, to, a, b);
        statusLater("正在下載「" + langName(from) + " ↔ " + langName(to) + "」離線模型（約 30MB，只需一次），先用線上翻譯…", false);
        try {
            return myMemory(q, from, to);
        } catch (Exception online) {
            statusLater("線上翻譯無法使用（" + online.getMessage() + "），等待離線模型下載完成…", false);
            Tasks.await(d, 180, TimeUnit.SECONDS);
            return finish(Tasks.await(tr.translate(q), 30, TimeUnit.SECONDS), q, from, to);
        }
    }

    /** ML Kit result post-processing: Traditional Chinese output. */
    String finish(String r, String q, String from, String to) {
        if (!"zh-TW".equals(to)) return r.trim();
        String t = toHant(r);
        if (t != null) return t.trim();
        try { return myMemory(q, from, to); } catch (Exception e) { return r.trim(); }
    }

    /** MyMemory free API (no key). Accepts zh-TW / zh-CN tags directly. */
    static String myMemory(String q, String from, String to) throws Exception {
        HttpURLConnection c = open("https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(q, "UTF-8")
                + "&langpair=" + URLEncoder.encode(from + "|" + to, "UTF-8"));
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("線上翻譯 HTTP " + code);
        JSONObject o = new JSONObject(readAll(c.getInputStream()));
        int st = o.optInt("responseStatus", 200);
        String t = o.getJSONObject("responseData").optString("translatedText", "");
        if (st != 200 || t.isEmpty()) throw new Exception("線上翻譯：" + o.optString("responseDetails", "失敗"));
        return t.trim();
    }

    String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    void openUrl(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); }
        catch (Exception e) { setStatus("找不到可以開啟網頁的 App", true); }
    }

    void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name) + " v" + versionName())
                .setMessage("• 翻譯優先使用 Google ML Kit 離線模型，在手機上完成，文字不會上傳。每種語言第一次使用時會下載約 30MB 的模型。\n"
                        + "• 模型還沒下載好，或 ML Kit 不支援的語言（緬甸文、高棉文、寮文、尼泊爾文、蒙古文），會把要翻譯的文字送到 MyMemory 線上翻譯服務。\n"
                        + "• 語音辨識與朗讀使用手機內建的語音服務（通常是 Google）。\n"
                        + "• 沒有廣告、沒有分析追蹤、不需要帳號；設定只存在這支手機。\n\n"
                        + "開放原始碼（Apache-2.0）。")
                .setPositiveButton("關閉", null)
                .setNeutralButton("隱私權政策", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { openUrl(PRIVACY_URL); }
                })
                .setNegativeButton("原始碼", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { openUrl(SOURCE_URL); }
                })
                .show();
    }

    Locale locale(int idx) {
        return Locale.forLanguageTag(LANGS[idx][0]);
    }

    void speak(String text, int langIdx) {
        if (text == null || text.trim().isEmpty() || text.equals("翻譯中…")) return;
        if (tts == null || !ttsReady) { setStatus("語音朗讀引擎尚未就緒", true); return; }
        int r = tts.setLanguage(locale(langIdx));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            setStatus("手機沒有「" + LANGS[langIdx][1] + "」的語音資料，可到 設定 → 文字轉語音 下載", true);
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vt");
    }

    // ------------------------------------------------------------------ speech recognition

    interface Heard { void on(String text); }

    void stopListening() {
        if (sr != null) sr.cancel();
        resetMic();
    }

    void resetMic() {
        if (listeningButton != null) {
            listeningButton.setText(listeningLabel);
            listeningButton.setBackground(pressable(C_ACCENT, 0xFF0B8584, 40, 0));
            listeningButton = null;
        }
    }

    static String speechError(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NO_MATCH: return "沒聽清楚，請再說一次";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "沒有偵測到說話聲";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "語音辨識需要網路，請確認連線";
            case SpeechRecognizer.ERROR_AUDIO: return "麥克風錄音錯誤";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "沒有麥克風權限";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "辨識器忙碌中，請稍後再試";
            case 12: case 13: return "這個語言不支援語音辨識（可在 Google 語音設定下載離線語言）";
            default: return "語音辨識錯誤（代碼 " + e + "）";
        }
    }

    void listen(final TextView btn, final int fromIdx, final Heard cb) {
        if (checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, PERM_REQ);
            setStatus("需要麥克風權限", true);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("這支手機沒有語音辨識服務（請安裝或啟用 Google App）", true);
            return;
        }
        if (tts != null) tts.stop();
        stopListening();
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(this);
        listeningButton = btn;
        listeningLabel = btn.getText().toString();
        btn.setText("● 聆聽中…（再按一下停止）");
        btn.setBackground(round(C_DANGER, 40, 0));
        sr.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { setStatus("請說話（" + LANGS[fromIdx][1] + "）", false); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rmsdB) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() { setStatus("辨識中…", false); }
            public void onError(int error) { resetMic(); setStatus(speechError(error), true); }
            public void onResults(Bundle results) {
                resetMic();
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String heard = list != null && !list.isEmpty() ? list.get(0) : "";
                if (heard.trim().isEmpty()) { setStatus("沒聽清楚，請再說一次", true); return; }
                cb.on(heard.trim());
            }
            public void onPartialResults(Bundle partialResults) {}
            public void onEvent(int eventType, Bundle params) {}
        });
        String tag = LANGS[fromIdx][0];
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
        i.putExtra("android.speech.extra.ONLY_RETURN_LANGUAGE_PREFERENCE", true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        sr.startListening(i);
    }
}
