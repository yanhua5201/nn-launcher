package com.nn.launcher;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final String SCRIPT = "/data/adb/nn_launcher.sh";
    private static final String LOG_FILE = "/data/adb/nn_launcher.log";
    private static final String HEREDOC = "__NN_LAUNCHER_EOF__";

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView output;
    private ScrollView scroller;
    private EditText soPathInput;
    private EditText keyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        soPathInput = new EditText(this);
        soPathInput.setHint("so 完整路径，留空则自动在 /data/adb 查找");
        soPathInput.setSingleLine(true);
        root.addView(soPathInput, wide());

        keyInput = new EditText(this);
        keyInput.setHint("卡密，可留空");
        keyInput.setSingleLine(true);
        root.addView(keyInput, wide());

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(btn("启动", new View.OnClickListener() {
            public void onClick(View v) { onStart(); }
        }), weight());
        row1.addView(btn("停止", new View.OnClickListener() {
            public void onClick(View v) { onStop(); }
        }), weight());
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(btn("状态", new View.OnClickListener() {
            public void onClick(View v) { onStatus(); }
        }), weight());
        row2.addView(btn("日志", new View.OnClickListener() {
            public void onClick(View v) { onLog(); }
        }), weight());
        row2.addView(btn("清屏", new View.OnClickListener() {
            public void onClick(View v) { output.setText(""); }
        }), weight());
        root.addView(row2);

        scroller = new ScrollView(this);
        output = new TextView(this);
        output.setTextSize(11);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        scroller.addView(output);
        root.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        append("就绪。需要 root，首次「启动」会弹授权。");
    }

    private Button btn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void onStart() {
        String so = soPathInput.getText().toString().trim();
        String key = keyInput.getText().toString().trim();
        String cmd = writeScriptCmd()
                + "sh " + SCRIPT + " start " + q(so) + " " + q(key) + "\n";
        runAsRoot(cmd, "启动");
    }

    private void onStop() {
        String cmd = writeScriptCmd() + "sh " + SCRIPT + " stop\n";
        runAsRoot(cmd, "停止");
    }

    private void onStatus() {
        String cmd = writeScriptCmd() + "sh " + SCRIPT + " status\n";
        runAsRoot(cmd, "状态");
    }

    private void onLog() {
        String cmd =
            "if [ -f " + LOG_FILE + " ]; then\n"
          + "  echo \"==== " + LOG_FILE + " 最后200行 ====\"\n"
          + "  tail -n 200 " +
