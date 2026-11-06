package co.ritmo.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** La tarjeta morada de Ritmo, igual a las ventanas del PC. */
final class Card {
    interface OnAct { void act(String act); }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static int[] colors(String kind) {
        if ("wind".equals(kind) || "bed".equals(kind)) return new int[]{0xFF25235E, 0xFF4C43A8};
        if ("go".equals(kind)) return new int[]{0xFFEC7A32, 0xFFF5B44E};
        if ("ping".equals(kind)) return new int[]{0xFF2F7FD8, 0xFF4FB3E6};
        if ("soft".equals(kind)) return new int[]{0xFF169F78, 0xFF4CCB9C};
        return new int[]{0xFF5B4FE0, 0xFF8F56EE, 0xFFC065E2};
    }

    static View build(Context c, JSONObject a, OnAct cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 22);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors(a.optString("kind")));
        bg.setCornerRadius(dp(c, 28));
        box.setBackground(bg);
        box.setElevation(dp(c, 12));

        TextView kind = new TextView(c);
        kind.setText("RITMO · " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        kind.setTextColor(0xCCFFFFFF);
        kind.setTextSize(12);
        kind.setLetterSpacing(0.08f);
        kind.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(kind);

        TextView title = new TextView(c);
        title.setText(a.optString("title"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(0, dp(c, 6), 0, dp(c, 4));
        box.addView(title);

        TextView body = new TextView(c);
        body.setText(a.optString("body"));
        body.setTextColor(0xE6FFFFFF);
        body.setTextSize(15);
        box.addView(body);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, dp(c, 16), 0, 0);
        JSONArray btns = a.optJSONArray("buttons");
        if (btns == null || btns.length() == 0) { btns = new JSONArray(); btns.put(new JSONArray().put("ok").put("Entendido")); }
        for (int i = 0; i < btns.length() && i < 3; i++) {
            JSONArray b = btns.optJSONArray(i);
            if (b == null) continue;
            final String act = b.optString(0);
            if ("shutdown".equals(act)) continue;
            Button bt = new Button(c);
            bt.setAllCaps(false);
            bt.setText(b.optString(1));
            bt.setTextSize(14);
            bt.setTypeface(Typeface.DEFAULT_BOLD);
            bt.setMinHeight(0);
            bt.setMinimumHeight(0);
            bt.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(c, 14));
            if (i == 0) { g.setColor(Color.WHITE); bt.setTextColor(0xFF3D33B8); }
            else { g.setColor(0x33FFFFFF); bt.setTextColor(Color.WHITE); }
            bt.setBackground(g);
            bt.setStateListAnimator(null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(c, 8));
            bt.setLayoutParams(lp);
            bt.setOnClickListener(v -> cb.act(act));
            row.addView(bt);
        }
        box.addView(row);
        return box;
    }
}
