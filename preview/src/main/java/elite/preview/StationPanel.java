package elite.preview;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Shared station presentation; state/transactions belong to the caller. */
final class StationPanel extends ScrollView {
    interface Host {
        byte[] state();
        void trade(int item, boolean buy);
        void reset();
        void primary();
        default String navigation() { return null; }
        default void selectTarget(int delta) {}
        default void selectReachable() {}
    }
    private static final String[] GOODS = {
        "Food", "Textiles", "Radioactives", "Slaves", "Liquor/Wines", "Luxuries",
        "Narcotics", "Computers", "Machinery", "Alloys", "Firearms", "Furs",
        "Minerals", "Gold", "Platinum", "Gem-stones", "Alien items"
    };
    private static final String[] RESULTS = {
        "Transaction completed", "Invalid input/state", "Not docked", "Station has no stock",
        "Cargo hold full", "Insufficient credits", "No cargo to sell", "Overflow prevented"
    };
    private final Host host;
    private final TextView summary, receipt;
    private final TextView[] prices = new TextView[17], stock = new TextView[17], cargo = new TextView[17];

    StationPanel(Context context, Host host, String warning, String hint, String primary) {
        super(context);
        this.host = host;
        LinearLayout panel = new LinearLayout(context);
        panel.setPadding(4, 4, 4, 4); panel.setOrientation(LinearLayout.VERTICAL);
        addView(panel);
        TextView notice = label(warning); notice.setTextColor(0xffffd86b); panel.addView(notice);
        summary = label(""); panel.addView(summary);
        if (host.navigation() != null) {
            LinearLayout navigation = new LinearLayout(context);
            navigation.setOrientation(LinearLayout.VERTICAL);
            TextView target = label(""); navigation.addView(target);
            LinearLayout navigationControls = new LinearLayout(context);
            Button previous = button("Target −"); previous.setOnClickListener(v -> { host.selectTarget(-1); refresh(); });
            Button next = button("Target +"); next.setOnClickListener(v -> { host.selectTarget(1); refresh(); });
            Button reachable = button("Reachable");
            reachable.setOnClickListener(v -> { host.selectReachable(); refresh(); });
            navigationControls.addView(previous); navigationControls.addView(next); navigationControls.addView(reachable);
            navigation.addView(navigationControls); panel.addView(navigation);
            target.setTag("navigation-label");
        }
        receipt = label(hint);
        LinearLayout actions = new LinearLayout(context);
        Button reset = button("New test commander");
        reset.setOnClickListener(v -> new AlertDialog.Builder(context)
            .setTitle("Reset test session?")
            .setMessage("Discard this test commander's credits, cargo and scene? Original Elite saves are not affected.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset", (dialog, which) -> {
                host.reset(); refresh(); receipt.setText("Reset to default test commander");
            }).show());
        actions.addView(reset);
        Button next = button(primary); next.setOnClickListener(v -> host.primary()); actions.addView(next);
        panel.addView(actions);
        panel.addView(receipt);
        TableLayout table = new TableLayout(context); table.setStretchAllColumns(true);
        TableRow header = new TableRow(context);
        for (String column : new String[]{"Product", "Price (Cr)", "Stock", "Hold", "Buy", "Sell"})
            header.addView(label(column));
        table.addView(header);
        for (int i = 0; i < 17; ++i) {
            TableRow row = new TableRow(context);
            String unit = i == 13 || i == 14 ? "kg" : i == 15 ? "g" : "t";
            row.addView(label(GOODS[i] + " (" + unit + ")"));
            prices[i] = label(""); row.addView(prices[i]);
            stock[i] = label(""); row.addView(stock[i]);
            cargo[i] = label(""); row.addView(cargo[i]);
            final int item = i;
            Button buy = button("+1"); buy.setOnClickListener(v -> transact(item, true)); row.addView(buy);
            Button sell = button("−1"); sell.setOnClickListener(v -> transact(item, false)); row.addView(sell);
            table.addView(row);
        }
        for (int i = 0; i < ((LinearLayout)getChildAt(0)).getChildCount(); ++i) {
            android.view.View child = ((LinearLayout)getChildAt(0)).getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout)child;
                for (int j = 0; j < row.getChildCount(); ++j) {
                    android.view.View item = row.getChildAt(j);
                    if ("navigation-label".equals(item.getTag())) ((TextView)item).setText(host.navigation());
                }
            }
        }
        panel.addView(table);
        refresh();
    }

    private void transact(int item, boolean buy) {
        host.trade(item, buy);
        int result = bytes(host.state()).getInt(200);
        receipt.setText((buy ? "Buy " : "Sell ") + GOODS[item] + ": "
            + (result >= 0 && result < RESULTS.length ? RESULTS[result] : "Unknown result " + result));
        refresh();
    }

    private void refresh() {
        byte[] state = host.state(); ByteBuffer data = bytes(state);
        summary.setText(ascii(state, 184, 12) + " · system #" + (data.getShort(114) & 65535)
            + " · economy " + (state[106] & 255) + " · government " + (state[107] & 255)
            + " · tech " + ((state[108] & 255) + 1) + "\nCommander " + ascii(state, 8, 8)
            + " · credits " + money(data.getInt(16) & 0xffffffffL)
            + " · cargo " + used(state) + "/" + (data.getShort(56) & 65535) + " t"
            + " · fuel " + money(state[20] & 255) + " ly");
        for (int i = 0; i < 17; ++i) {
            prices[i].setText(money(data.getInt(116 + i * 4) & 0xffffffffL));
            stock[i].setText(String.valueOf(state[75 + i] & 255));
            cargo[i].setText(String.valueOf(state[58 + i] & 255));
        }
    }

    static String flightSummary(byte[] state) {
        ByteBuffer data = bytes(state);
        return ascii(state, 184, 12) + " · " + money(data.getInt(16) & 0xffffffffL)
            + " Cr · cargo " + used(state) + "/" + (data.getShort(56) & 65535) + " t";
    }
    private static int used(byte[] state) {
        int used = state[74] & 255;
        for (int i = 0; i < 13; ++i) used += state[58 + i] & 255;
        return used;
    }
    private TextView label(String text) {
        TextView view = new TextView(getContext()); view.setText(text);
        view.setTextSize(11); view.setPadding(4, 4, 4, 4); return view;
    }
    private Button button(String text) {
        Button view = new Button(getContext()); view.setText(text);
        view.setTextSize(10); view.setAllCaps(false);
        view.setMinimumWidth(0); view.setMinWidth(0);
        int height = (int)(32 * getResources().getDisplayMetrics().density);
        view.setMinimumHeight(height); view.setMinHeight(height); view.setPadding(6, 0, 6, 0);
        return view;
    }
    private static ByteBuffer bytes(byte[] state) { return ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN); }
    private static String ascii(byte[] bytes, int start, int count) {
        int end = start; while (end < start + count && bytes[end] != 0) ++end;
        return new String(bytes, start, end - start, StandardCharsets.US_ASCII);
    }
    private static String money(long tenths) { return String.format(Locale.ROOT, "%d.%d", tenths / 10, tenths % 10); }
}
