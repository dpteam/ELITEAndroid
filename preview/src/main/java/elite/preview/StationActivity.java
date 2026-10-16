package elite.preview;

import android.app.Activity;
import android.os.Bundle;
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

/** New station test UI over actual ported state rules; no persistence/jumps. */
public final class StationActivity extends Activity {
    private static final String[] GOODS = {
        "Food", "Textiles", "Radioactives", "Slaves", "Liquor/Wines", "Luxuries",
        "Narcotics", "Computers", "Machinery", "Alloys", "Firearms", "Furs",
        "Minerals", "Gold", "Platinum", "Gem-stones", "Alien items"
    };
    private static final String[] RESULTS = {
        "Transaction completed", "Invalid input/state", "Not docked", "Station has no stock",
        "Cargo hold full", "Insufficient credits", "No cargo to sell", "Overflow prevented"
    };
    private byte[] state;
    private TextView summary, receipt;
    private final TextView[] prices = new TextView[17];
    private final TextView[] stock = new TextView[17];
    private final TextView[] cargo = new TextView[17];

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            System.loadLibrary("elite_preview");
            byte[] restored = savedInstanceState == null ? null : savedInstanceState.getByteArray("station-test-state");
            state = valid(restored) ? restored.clone() : NativeStation.reset();
            if (!valid(state)) throw new IllegalStateException("Invalid native station format");
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            TextView message = new TextView(this);
            message.setText("Station core test could not start:\n" + error);
            setContentView(message);
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(16, 16, 16, 16);
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);
        TextView warning = label("STATION CORE TEST — NOT THE COMPLETE GAME\n"
            + "New UI; native market/commander rules. No launch, hyperspace, missions or disk saves.");
        warning.setTextColor(0xffffd86b);
        panel.addView(warning);
        summary = label(""); panel.addView(summary);
        LinearLayout actions = new LinearLayout(this);
        Button reset = new Button(this); reset.setText("New test commander");
        reset.setOnClickListener(v -> { state = NativeStation.reset(); refresh(); receipt.setText("Reset to default commander"); });
        actions.addView(reset);
        Button close = new Button(this); close.setText("Back to flight/model test");
        close.setOnClickListener(v -> finish()); actions.addView(close);
        panel.addView(actions);
        receipt = label("One unit per transaction. Flight test has independent state.");
        panel.addView(receipt);
        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(true);
        TableRow header = new TableRow(this);
        for (String column : new String[]{"Product", "Price (Cr)", "Stock", "Hold", "Buy", "Sell"}) header.addView(label(column));
        table.addView(header);
        for (int i = 0; i < 17; ++i) {
            TableRow row = new TableRow(this);
            String unit = i == 13 || i == 14 ? "kg" : i == 15 ? "g" : "t";
            row.addView(label(GOODS[i] + " (" + unit + ")"));
            prices[i] = label(""); row.addView(prices[i]);
            stock[i] = label(""); row.addView(stock[i]);
            cargo[i] = label(""); row.addView(cargo[i]);
            final int item = i;
            Button buy = new Button(this); buy.setText("+1");
            buy.setOnClickListener(v -> transact(item, true)); row.addView(buy);
            Button sell = new Button(this); sell.setText("−1");
            sell.setOnClickListener(v -> transact(item, false)); row.addView(sell);
            table.addView(row);
        }
        panel.addView(table);
        setContentView(scroll);
        refresh();
    }

    private void transact(int item, boolean buy) {
        byte[] changed = NativeStation.trade(state, item, buy);
        if (!valid(changed)) throw new IllegalStateException("Invalid transaction response");
        state = changed;
        int result = buffer().getInt(200);
        receipt.setText((buy ? "Buy " : "Sell ") + GOODS[item] + ": "
            + (result >= 0 && result < RESULTS.length ? RESULTS[result] : "Unknown result " + result));
        refresh();
    }
    private void refresh() {
        ByteBuffer bytes = buffer();
        int used = 0;
        for (int i = 0; i < 13; ++i) used += state[58 + i] & 255;
        used += state[74] & 255;
        String name = ascii(state, 184, 12);
        String commander = ascii(state, 8, 8);
        int capacity = bytes.getShort(56) & 65535;
        summary.setText(name + " · system #" + (bytes.getShort(114) & 65535)
            + " · economy " + (state[106] & 255) + " · government " + (state[107] & 255)
            + " · tech " + ((state[108] & 255) + 1) + "\nCommander " + commander
            + " · credits " + money(bytes.getInt(16) & 0xffffffffL)
            + " · cargo " + used + "/" + capacity + " t"
            + " · fuel " + money(state[20] & 255) + " ly");
        for (int i = 0; i < 17; ++i) {
            prices[i].setText(money(bytes.getInt(116 + i * 4) & 0xffffffffL));
            stock[i].setText(String.valueOf(state[75 + i] & 255));
            cargo[i].setText(String.valueOf(state[58 + i] & 255));
        }
    }
    private TextView label(String text) {
        TextView view = new TextView(this); view.setText(text);
        view.setPadding(8, 8, 8, 8); return view;
    }
    private ByteBuffer buffer() { return ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN); }
    private static boolean valid(byte[] state) {
        if (state == null || state.length != 204) return false;
        ByteBuffer bytes = ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN);
        return bytes.getInt(0) == 0x4553544e && bytes.getInt(4) == 1;
    }
    private static String ascii(byte[] bytes, int start, int count) {
        int end = start;
        while (end < start + count && bytes[end] != 0) ++end;
        return new String(bytes, start, end - start, StandardCharsets.US_ASCII);
    }
    private static String money(long tenths) { return String.format(Locale.ROOT, "%d.%d", tenths / 10, tenths % 10); }
    @Override public void onSaveInstanceState(Bundle out) {
        if (valid(state)) out.putByteArray("station-test-state", state.clone());
        super.onSaveInstanceState(out);
    }
}
