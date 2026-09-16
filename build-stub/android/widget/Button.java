package android.widget;

import android.content.Context;

/** 桩：AOSP 继承链 Button→TextView→View。SettingsActivity 手动同步按钮用。
 *  setText 继承自 TextView，setOnClickListener 继承自 View。 */
public class Button extends TextView {
    public Button(Context context) { super(context); }
}
