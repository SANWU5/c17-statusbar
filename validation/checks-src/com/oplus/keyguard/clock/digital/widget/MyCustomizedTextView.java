package com.oplus.keyguard.clock.digital.widget;
/** Native caller shape: tryUpdateText ultimately invokes TextView.setText(CharSequence). */
public class MyCustomizedTextView extends android.widget.TextView {
    public MyCustomizedTextView(android.content.Context context){super(context);}
    public void tryUpdateText(CharSequence input){
        CharSequence text=input==null?"":input;
        if(!getText().toString().contentEquals(text))setText(text);
    }
}
