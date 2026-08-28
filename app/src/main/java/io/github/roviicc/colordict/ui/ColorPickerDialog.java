package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Palette;

/** A simple grid of the palette colors for tagging a dictionary. */
public final class ColorPickerDialog {

    public interface OnColorPicked {
        void onColorPicked(int color);
    }

    private ColorPickerDialog() {
    }

    public static void show(Context context, int currentColor, OnColorPicked listener) {
        GridView grid = new GridView(context);
        grid.setNumColumns(4);
        int pad = dp(context, 16);
        grid.setPadding(pad, pad, pad, pad);
        grid.setVerticalSpacing(dp(context, 12));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.pick_color)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        grid.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return Palette.COLORS.length;
            }

            @Override
            public Integer getItem(int position) {
                return Palette.COLORS[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                int color = Palette.COLORS[position];
                TextView swatch = convertView instanceof TextView
                        ? (TextView) convertView : new TextView(context);
                int size = dp(context, 44);
                swatch.setLayoutParams(new FrameLayout.LayoutParams(size, size));
                swatch.setGravity(Gravity.CENTER);
                swatch.setTextColor(0xFFFFFFFF);
                swatch.setTextSize(18);
                swatch.setText(color == currentColor ? "✓" : "");
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(color);
                swatch.setBackground(bg);
                return swatch;
            }
        });
        grid.setOnItemClickListener((parent, view, position, id) -> {
            listener.onColorPicked(Palette.COLORS[position]);
            dialog.dismiss();
        });
        dialog.show();
    }

    private static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
