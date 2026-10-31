package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** The list owns the remaining space; crowded controls compact without shrinking file rows. */
final class FileBrowserLayout extends LinearLayout {
    private final Map<TextView,Float> requested=new LinkedHashMap<>();
    private float controlScale=1;
    FileBrowserLayout(Context c){super(c);setOrientation(VERTICAL);}
    float controlScale(){return controlScale;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void remember(View v){
        if(v instanceof ListView)return;
        if(v instanceof TextView)requested.put((TextView)v,((TextView)v).getTextSize());
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)remember(((ViewGroup)v).getChildAt(i));
    }
    private boolean navigationFits(View v){
        if(v.getVisibility()==GONE)return true;
        if("file_navigation".equals(v.getTag())){
            ViewGroup group=(ViewGroup)v;
            return group.getChildAt(0).getMeasuredWidth()<=group.getMeasuredWidth()/3;
        }
        if(v instanceof ViewGroup&&!(v instanceof ListView))for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(!navigationFits(((ViewGroup)v).getChildAt(i)))return false;
        return true;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(requested.isEmpty())remember(this);
        float font=Math.max(1,getResources().getDisplayMetrics().scaledDensity/getResources().getDisplayMetrics().density);
        float minimum=1/font;controlScale=1;
        boolean shortWindow=MeasureSpec.getSize(heightSpec)<dp(400);
        setPadding(getPaddingLeft(),shortWindow?0:dp(4),getPaddingRight(),shortWindow?0:dp(4));
        int listMinimum=dp(MeasureSpec.getSize(heightSpec)>=dp(500)?160:80);
        while(true){
            for(Map.Entry<TextView,Float> item:requested.entrySet()){
                TextView text=item.getKey();text.setTextSize(TypedValue.COMPLEX_UNIT_PX,item.getValue()*controlScale);
                // IconButtons may decorate an existing screen again after a style change.
                if(text instanceof Button){text.setMinLines(1);text.setMinHeight(dp(48));text.setMinimumHeight(dp(48));if(shortWindow)text.setPadding(text.getPaddingLeft(),dp(2),text.getPaddingRight(),dp(2));}
            }
            TextView status=findViewById(R.id.transferStatusText);if(status!=null){status.setPadding(dp(8),dp(shortWindow?2:4),dp(8),dp(shortWindow?2:4));((LayoutParams)status.getLayoutParams()).topMargin=shortWindow?0:dp(4);}
            for(TextView text:requested.keySet())if("file_path".equals(text.getTag())){text.setMaxLines(shortWindow?1:2);text.setMinLines(!shortWindow&&getResources().getConfiguration().screenWidthDp>=600?2:1);View pane=(View)text.getParent();pane.setPadding(dp(4),shortWindow?0:dp(4),dp(4),shortWindow?0:dp(4));View panes=(View)pane.getParent();((LayoutParams)panes.getLayoutParams()).topMargin=shortWindow?0:dp(6);}
            super.onMeasure(widthSpec,heightSpec);
            if(!shortWindow&&getResources().getConfiguration().screenWidthDp>=600){
                int headerHeight=0;boolean differs=false;java.util.ArrayList<TextView> headers=new java.util.ArrayList<>();
                for(TextView text:requested.keySet())if("file_path".equals(text.getTag())){headers.add(text);headerHeight=Math.max(headerHeight,text.getMeasuredHeight());}
                for(TextView text:headers)differs|=text.getMeasuredHeight()!=headerHeight;
                if(differs){for(TextView text:headers)text.setMinHeight(headerHeight);super.onMeasure(widthSpec,heightSpec);}
            }
            boolean fits=navigationFits(this);
            for(int id:new int[]{R.id.phoneFileList,R.id.remoteFileList}){
                View list=findViewById(id);View frame=(View)list.getParent();View pane=(View)frame.getParent();
                if(pane.getVisibility()!=GONE&&frame.getMeasuredHeight()<listMinimum)fits=false;
            }
            if(fits||controlScale<=minimum)break;
            controlScale=Math.max(minimum,controlScale-.05f);
        }
    }
}
