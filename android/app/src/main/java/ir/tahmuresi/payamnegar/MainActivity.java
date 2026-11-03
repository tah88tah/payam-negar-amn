package ir.tahmuresi.payamnegar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_APP_PERMISSIONS = 5101;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 5102;
    private static final int REQUEST_PICK_CONTACT = 5103;
    private static final String PREF_NOTIFICATION_PROMPTED = "notification_permission_prompted";
    private static final int NAVY = Color.rgb(39, 52, 49);
    private static final int GOLD = Color.rgb(166, 106, 76);
    private static final int PAGE = Color.rgb(244, 241, 235);
    private static final int INCOMING = Color.rgb(255, 255, 255);
    private static final int OUTGOING = Color.rgb(229, 237, 230);
    private static final int TEXT = Color.rgb(40, 51, 47);
    private static final int MUTED = Color.rgb(113, 125, 117);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile String sessionPassphrase;
    private boolean dataReceiverRegistered;
    private volatile boolean activityStarted;
    private volatile long phraseOperationToken;
    private final BroadcastReceiver dataChangedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            renderChat();
        }
    };

    private EditText peerPhoneInput;
    private TextView contactPicker;
    private boolean updatingPhoneField;
    private EditText passphraseInput;
    private EditText messageInput;
    private TextView sessionText;
    private LinearLayout messagesContainer;
    private ScrollView chatScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.createChannel(this);
        buildUi();
        loadPeerSettings();
    }

    private void buildUi() {
        Window window = getWindow();
        window.setStatusBarColor(NAVY);
        window.setNavigationBarColor(NAVY);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));
        header.setBackgroundColor(NAVY);
        TextView settings = text("⚙", 24, Color.WHITE, true);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("تنظیمات");
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text("🔐 پیام‌نگار امن", 19, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatScroll.setBackgroundColor(PAGE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(12));
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        chatScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content.addView(buildPeerCard(), marginParams());
        content.addView(buildSecurityCard(), marginParams());
        content.addView(buildChatCard(), marginParams());

        root.addView(buildComposer(), new LinearLayout.LayoutParams(-1, -2));

        // Keep content clear of system bars on recent Android versions.
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(root);
    }

    private View buildPeerCard() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(round(Color.WHITE, 12, 0xffdedbd3, 1));

        peerPhoneInput = edit("شماره موبایل مخاطب", InputType.TYPE_CLASS_PHONE);
        peerPhoneInput.setSingleLine(true);
        peerPhoneInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        peerPhoneInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        row.addView(peerPhoneInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        peerPhoneInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) savePeerSettings(false);
        });
        peerPhoneInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                if (updatingPhoneField) return;
                String canonical = PhoneNumbers.toIranInternational(value.toString());
                if (!canonical.isEmpty()) AppPrefs.savePeer(MainActivity.this, AppPrefs.peerName(MainActivity.this), canonical);
            }
        });

        contactPicker = text("👤", 23, NAVY, true);
        contactPicker.setGravity(Gravity.CENTER);
        contactPicker.setContentDescription("انتخاب مخاطب از دفترچه تلفن");
        contactPicker.setBackground(round(0xffeeeae2, 8, 0xffdedbd3, 1));
        contactPicker.setOnClickListener(v -> openContactPicker());
        LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(dp(46), dp(44));
        contactParams.setMargins(dp(7), 0, 0, 0);
        row.addView(contactPicker, contactParams);
        return row;
    }

    private View buildSecurityCard() {
        LinearLayout card = card();
        card.addView(text("بازکردن گفتگوی رمزنگاری‌شده", 14, NAVY, true));
        card.addView(text("عبارت محرمانه باید جداگانه و خارج از پیام‌نگار تبادل شود.", 11, MUTED, false));
        passphraseInput = edit("عبارت محرمانهٔ مشترک · حداقل ۱۲ نویسه",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passphraseInput.setSingleLine(true);
        passphraseInput.setSaveEnabled(false);
        passphraseInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        card.addView(passphraseInput, topParams());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button unlock = button("بازکردن گفتگو", true);
        unlock.setOnClickListener(v -> unlockChat());
        actions.addView(unlock, new LinearLayout.LayoutParams(-1, dp(46)));
        card.addView(actions, topParams());
        sessionText = text("گفتگو قفل است.", 10, MUTED, false);
        card.addView(sessionText, topParams());
        return card;
    }

    private View buildChatCard() {
        LinearLayout card = card();
        card.addView(text("گفتگو", 14, NAVY, true));
        messagesContainer = new LinearLayout(this);
        messagesContainer.setOrientation(LinearLayout.VERTICAL);
        messagesContainer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.addView(messagesContainer, fullWidthParams());
        return card;
    }

    private View buildComposer() {
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        composer.setPadding(dp(10), dp(7), dp(10), dp(7));
        composer.setBackgroundColor(Color.WHITE);
        messageInput = edit("پیام خودت را بنویس…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        messageInput.setSaveEnabled(false);
        messageInput.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        inputParams.setMarginEnd(dp(8));
        composer.addView(messageInput, inputParams);
        Button send = button("ارسال", true);
        composer.addView(send, new LinearLayout.LayoutParams(dp(84), dp(52)));
        send.setOnClickListener(v -> prepareAndSend());
        return composer;
    }

    private void savePeerSettings() { savePeerSettings(true); }

    private void savePeerSettings(boolean showError) {
        String raw = peerPhoneInput.getText().toString();
        String phone = PhoneNumbers.toIranInternational(raw);
        if (!PhoneNumbers.isValidIranNumber(phone)) {
            if (showError && !raw.trim().isEmpty()) toast("شمارهٔ موبایل ایران معتبر نیست.");
            return;
        }
        AppPrefs.savePeer(this, AppPrefs.peerName(this), phone);
        if (!phone.equals(raw)) {
            updatingPhoneField = true;
            peerPhoneInput.setText(phone);
            peerPhoneInput.setSelection(peerPhoneInput.length());
            updatingPhoneField = false;
        }
    }

    private void loadPeerSettings() {
        updatingPhoneField = true;
        peerPhoneInput.setText(AppPrefs.peerPhone(this));
        updatingPhoneField = false;
    }

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try { startActivityForResult(intent, REQUEST_PICK_CONTACT); }
        catch (RuntimeException ex) { toast("دفترچهٔ مخاطبین در دسترس نیست."); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_CONTACT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(data.getData(),
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String phone = PhoneNumbers.toIranInternational(cursor.getString(0));
                String name = cursor.getString(1);
                if (!PhoneNumbers.isValidIranNumber(phone)) {
                    toast("شمارهٔ انتخاب‌شده، شمارهٔ موبایل معتبر ایران نیست.");
                    return;
                }
                AppPrefs.savePeer(this, name, phone);
                updatingPhoneField = true;
                peerPhoneInput.setText(phone);
                updatingPhoneField = false;
            }
        } catch (RuntimeException ex) {
            toast("خواندن شمارهٔ مخاطب ممکن نشد.");
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void showSettings() {
        new AlertDialog.Builder(this).setTitle("تنظیمات")
                .setItems(new String[]{"بستر ارتباطی", "تم"}, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(this).setTitle("بستر ارتباطی")
                                .setSingleChoiceItems(new String[]{"SMS  ·  فعال", "اینترنت  ·  به‌زودی"}, 0,
                                        (d, selected) -> {
                                            if (selected == 1) toast("ارتباط اینترنتی در نسخهٔ فعلی فعال نیست.");
                                            d.dismiss();
                                        }).setNegativeButton("بستن", null).show();
                    } else {
                        new AlertDialog.Builder(this).setTitle("تم")
                                .setSingleChoiceItems(new String[]{"سیستم / پیش‌فرض  ·  به‌زودی", "روشن  ·  فعلی", "تاریک  ·  به‌زودی"}, 1,
                                        (d, selected) -> {
                                            if (selected != 1) toast("انتخاب تم در نسخهٔ فعلی هنوز فعال نیست.");
                                            d.dismiss();
                                        }).setNegativeButton("بستن", null).show();
                    }
                }).show();
    }

    private boolean hasSmsPermissions() {
        return checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SEND_SMS);
        }
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECEIVE_SMS);
        }
        if (needed.isEmpty()) {
            toast("مجوزهای ارسال و دریافت پیامک فعال هستند.");
            return;
        }
        requestPermissions(needed.toArray(new String[0]), REQUEST_APP_PERMISSIONS);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean(PREF_NOTIFICATION_PROMPTED, false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_NOTIFICATION_PROMPTED, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    private void showSmsPermissionHelp() {
        new AlertDialog.Builder(this)
                .setTitle("دسترسی پیامک فعال نیست")
                .setMessage("بدون مجوز ارسال و دریافت پیامک، ارسال و دریافت پیام‌های رمزنگاری‌شده ممکن نیست. می‌توانی فعلاً از برنامه خارج شوی یا هر زمان خواستی وضعیت مجوزها را از تنظیمات خود Android تغییر بدهی.")
                .setPositiveButton("متوجه شدم", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_APP_PERMISSIONS) {
            if (hasSmsPermissions()) {
                toast("مجوزهای ارسال و دریافت پیامک فعال شدند.");
                requestNotificationPermissionIfNeeded();
            } else {
                toast("مجوزهای پیامک داده نشدند؛ قابلیت ارسال و دریافت SMS غیرفعال است.");
                showSmsPermissionHelp();
            }
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast("اعلان‌های دریافت پیام فعال شدند.");
            } else {
                toast("اعلان‌ها غیرفعال‌اند؛ دریافت SMS همچنان می‌تواند انجام شود، اما اعلان نمایش داده نمی‌شود.");
            }
        }
    }

    private void unlockChat() {
        String candidate = passphraseInput.getText().toString();
        if (candidate.length() < 12) {
            toast("عبارت محرمانه باید حداقل ۱۲ نویسه داشته باشد.");
            return;
        }
        passphraseInput.setText("");
        sessionText.setText("در حال بررسی عبارت محرمانه…");
        final long requestToken = ++phraseOperationToken;
        worker.execute(() -> {
            try {
                MessageStore store = new MessageStore(this);
                List<ChatMessage> messages;
                try {
                    messages = store.getMessages();
                } finally {
                    store.close();
                }
                boolean verifiedResult = false;
                for (ChatMessage message : messages) {
                    CryptoEngine.decrypt(message.encryptedPacket, candidate);
                    verifiedResult = true;
                    break;
                }
                final boolean verified = verifiedResult;
                runOnUiThread(() -> {
                    if (!activityStarted || requestToken != phraseOperationToken || isFinishing()) return;
                    sessionPassphrase = candidate;
                    sessionText.setText(verified
                            ? "گفتگو باز است؛ عبارت فقط تا زمان فعال‌بودن این صفحه در حافظه می‌ماند."
                            : "گفتگو باز شد؛ هنوز پیامی برای آزمایش عبارت وجود ندارد.");
                    renderChat();
                    if (!hasSmsPermissions()) requestAppPermissions();
                    else requestNotificationPermissionIfNeeded();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (!activityStarted || requestToken != phraseOperationToken || isFinishing()) return;
                    sessionPassphrase = null;
                    sessionText.setText("عبارت درست نیست یا یکی از بسته‌های پیام قابل رمزگشایی نیست.");
                    toast("بررسی عبارت ناموفق بود.");
                });
            }
        });
    }

    private void prepareAndSend() {
        final String key = sessionPassphrase;
        final String plaintext = messageInput.getText().toString();
        final String phone = AppPrefs.peerPhone(this);
        if (key == null) {
            toast("اول گفتگو را با عبارت محرمانه باز کن.");
            return;
        }
        if (plaintext.trim().isEmpty()) {
            toast("اول متن پیام را بنویس.");
            return;
        }
        String canonicalPhone = PhoneNumbers.toIranInternational(phone);
        if (!PhoneNumbers.isValidIranNumber(canonicalPhone)) {
            toast("شمارهٔ موبایل ایران را وارد یا از مخاطبین انتخاب کن.");
            return;
        }
        AppPrefs.savePeer(this, AppPrefs.peerName(this), canonicalPhone);
        if (!hasSmsPermissions()) {
            toast("برای استفادهٔ کامل، مجوز ارسال و دریافت پیامک لازم است.");
            requestAppPermissions();
            return;
        }

        messageInput.setEnabled(false);
        worker.execute(() -> {
            try {
                String packet = CryptoEngine.encrypt(plaintext, key);
                String messageId = SmsPacketProtocol.newMessageId();
                List<String> frames = SmsPacketProtocol.split(packet, messageId);
                runOnUiThread(() -> {
                    if (!activityStarted || isFinishing() || !key.equals(sessionPassphrase)) {
                        messageInput.setEnabled(true);
                        return;
                    }
                    sendEncryptedPacket(canonicalPhone, messageId, packet, frames, plaintext);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    messageInput.setEnabled(true);
                    toast(ex.getMessage() == null ? "رمزنگاری پیام ناموفق بود." : ex.getMessage());
                });
            }
        });
    }

    private void sendEncryptedPacket(String phone, String messageId, String packet,
                                     List<String> frames, String plaintext) {
        messageInput.setText("");
        messageInput.setEnabled(false);
        worker.execute(() -> {
            MessageStore store = new MessageStore(this);
            try {
                store.addOutgoing(messageId, PhoneNumbers.normalize(phone), packet, frames.size(), System.currentTimeMillis());
                SmsSender.send(this, phone, messageId, frames, store);
            } catch (Exception ex) {
                store.failQueuedParts(messageId, -100);
                runOnUiThread(() -> toast(ex.getMessage() == null ? "ارسال پیامک ناموفق بود." : ex.getMessage()));
            } finally {
                store.close();
            }
            runOnUiThread(() -> {
                messageInput.setEnabled(true);
                renderChat();
            });
        });
    }

    private void renderChat() {
        if (messagesContainer == null) return;
        messagesContainer.removeAllViews();
        String key = sessionPassphrase;
        if (key == null) {
            messagesContainer.addView(text("پیام‌ها روی این گوشی فقط به‌شکل بستهٔ رمز‌شده نگهداری می‌شوند. برای خواندن متن، گفتگو را باز کن.",
                    12, MUTED, false), fullWidthParams());
            loadMessageCountOnly();
            return;
        }
        messagesContainer.addView(text("در حال آماده‌سازی پیام‌ها…", 12, MUTED, false));
        worker.execute(() -> {
            final List<RenderedMessage> rendered = new ArrayList<>();
            try {
                MessageStore store = new MessageStore(this);
                List<ChatMessage> messages;
                try { messages = store.getMessages(); } finally { store.close(); }
                for (ChatMessage message : messages) {
                    String plain;
                    try {
                        plain = CryptoEngine.decrypt(message.encryptedPacket, key);
                    } catch (Exception ex) {
                        plain = "[رمزگشایی ناموفق؛ عبارت یا بستهٔ پیام را بررسی کن]";
                    }
                    rendered.add(new RenderedMessage(message, plain));
                }
            } catch (RuntimeException ex) {
                runOnUiThread(() -> {
                    if (sessionPassphrase != null && sessionPassphrase.equals(key)) {
                        messagesContainer.removeAllViews();
                        messagesContainer.addView(text("خواندن تاریخچهٔ محلی ناموفق بود.", 12, MUTED, false));
                    }
                });
                return;
            }
            runOnUiThread(() -> {
                if (sessionPassphrase == null || !sessionPassphrase.equals(key)) return;
                messagesContainer.removeAllViews();
                if (rendered.isEmpty()) {
                    messagesContainer.addView(text("هنوز پیامی ردوبدل نشده است.", 12, MUTED, false));
                }
                for (RenderedMessage item : rendered) addMessageBubble(item);
                chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
            });
        });
    }

    private void loadMessageCountOnly() {
        worker.execute(() -> {
            List<ChatMessage> messages;
            try {
                MessageStore store = new MessageStore(this);
                try { messages = store.getMessages(); } finally { store.close(); }
            } catch (RuntimeException ex) {
                return;
            }
            runOnUiThread(() -> {
                if (sessionPassphrase != null || messagesContainer == null) return;
                TextView count = text("تعداد بسته‌های ذخیره‌شده: " + messages.size(), 11, MUTED, false);
                messagesContainer.addView(count);
            });
        });
    }

    private void addMessageBubble(RenderedMessage item) {
        boolean outgoing = "OUT".equals(item.message.direction);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(outgoing ? Gravity.LEFT : Gravity.RIGHT);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(7));
        bubble.setBackground(round(outgoing ? OUTGOING : INCOMING, 14, outgoing ? 0xffc3d3e8 : 0xffdbe2ec, 1));
        bubble.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView body = text(item.plaintext, 14, TEXT, false);
        body.setTextDirection(View.TEXT_DIRECTION_RTL);
        body.setGravity(Gravity.RIGHT);
        bubble.addView(body);
        String status = outgoing ? outgoingStatus(item.message.status) : "دریافت شد";
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(item.message.createdAt));
        TextView meta = text(time + "  ·  " + status + "  ·  " + item.message.totalParts + " SMS",
                10, MUTED, false);
        meta.setGravity(Gravity.LEFT);
        bubble.addView(meta);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(0, -2, 0.88f);
        bubbleParams.setMargins(dp(2), dp(5), dp(2), dp(5));
        row.addView(bubble, bubbleParams);
        messagesContainer.addView(row, fullWidthParams());
    }

    private String outgoingStatus(String status) {
        if ("SENT".equals(status)) return "از گوشی ارسال شد؛ تحویل تأیید نشده";
        if ("PARTIAL".equals(status)) return "ارسال ناقص";
        if ("FAILED".equals(status)) return "ارسال ناموفق";
        return "در حال ارسال";
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityStarted = true;
        if (!dataReceiverRegistered) {
            IntentFilter filter = new IntentFilter(AppEvents.ACTION_DATA_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(dataChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(dataChangedReceiver, filter);
            dataReceiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderChat();
    }

    @Override
    protected void onStop() {
        // Automatic security lock: when the app leaves the foreground, invalidate pending
        // unlock work and discard the active session key; no manual lock control is exposed.
        activityStarted = false;
        phraseOperationToken++;
        if (dataReceiverRegistered) {
            unregisterReceiver(dataChangedReceiver);
            dataReceiverRegistered = false;
        }
        sessionPassphrase = null;
        if (passphraseInput != null) passphraseInput.setText("");
        // Discard any unsent plaintext draft as well as the active session key.
        if (messageInput != null) messageInput.setText("");
        if (sessionText != null) sessionText.setText("گفتگو قفل شد؛ برای خواندن پیام‌ها دوباره عبارت محرمانه را وارد کن.");
        if (messagesContainer != null) {
            messagesContainer.removeAllViews();
            messagesContainer.addView(text("گفتگو قفل است.", 12, MUTED, false));
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(9), dp(11), dp(9));
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setBackground(round(Color.WHITE, 14, 0xffdbe2ec, 1));
        return card;
    }

    private EditText edit(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextSize(14);
        input.setTextColor(TEXT);
        input.setHintTextColor(0xff91a0b5);
        input.setPadding(dp(10), dp(7), dp(10), dp(7));
        input.setBackground(round(0xfff8fafd, 9, 0xffccd6e4, 1));
        input.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return input;
    }

    private TextView label(String value) {
        TextView view = text(value, 12, TEXT, true);
        view.setPadding(0, dp(8), 0, dp(4));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.RIGHT);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return view;
    }

    private Button button(String title, boolean primary) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : NAVY);
        button.setBackground(round(primary ? NAVY : 0xffedf1f7, 10,
                primary ? NAVY : 0xffdbe2ec, 1));
        return button;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams marginParams() {
        LinearLayout.LayoutParams params = fullWidthParams();
        params.bottomMargin = dp(7);
        return params;
    }

    private LinearLayout.LayoutParams topParams() {
        LinearLayout.LayoutParams params = fullWidthParams();
        params.topMargin = dp(8);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static final class RenderedMessage {
        final ChatMessage message;
        final String plaintext;
        RenderedMessage(ChatMessage message, String plaintext) {
            this.message = message;
            this.plaintext = plaintext;
        }
    }
}
