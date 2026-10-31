package com.bitpoint.homeservercontrol;

import android.content.Context;import android.util.AttributeSet;import android.view.*;import android.widget.TextView;

/** Role header stays above the phone, and above the host's left column on tablets. */
public final class HomeShellLayout extends ViewGroup {
 private boolean hostColumns;private int headerHeight,headerWidth;
 public HomeShellLayout(Context c,AttributeSet a){super(c,a);}
 @Override protected void onMeasure(int ws,int hs){
  int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);View tabs=findViewById(R.id.homeRoleTabs),content=findViewById(R.id.homeRoleContent);ServerHomeLayout server=findViewById(R.id.serverHome);
  hostColumns=server.getVisibility()==VISIBLE&&findViewById(R.id.homeClientTab).getVisibility()==GONE&&server.hasHeaderColumn(w,h);
  boolean onlyServer=server.getVisibility()==VISIBLE&&findViewById(R.id.homeClientTab).getVisibility()==GONE;
  if(onlyServer){TextView title=findViewById(R.id.homeServerTab);title.setTextColor(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));title.setBackgroundTintList(null);if(!(title.getBackground() instanceof android.graphics.drawable.GradientDrawable))title.setBackground(SurfaceStyle.panel(getContext()));String value="Сервер\n"+(hostColumns?"Это устройство":"Этот телефон");android.text.SpannableString label=new android.text.SpannableString(value);label.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,6,0);label.setSpan(new android.text.style.RelativeSizeSpan(.75f),7,value.length(),0);if(!(title.getText() instanceof android.text.Spanned)||!value.contentEquals(title.getText()))title.setText(label);}
  headerWidth=hostColumns?server.headerColumnWidth(w,h):w;
  for(int id:new int[]{R.id.homeServerTab,R.id.homeClientTab}){TextView tab=findViewById(id);tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(w>h*1.2f&&h>=dp(500)?18:h>=dp(500)?16:14));tab.setPaddingRelative(dp(hostColumns?12:8),dp(2),dp(8),dp(2));tab.setGravity(hostColumns?Gravity.START|Gravity.CENTER_VERTICAL:Gravity.CENTER);}
  tabs.measure(exact(headerWidth),MeasureSpec.makeMeasureSpec(h,MeasureSpec.AT_MOST));headerHeight=tabs.getMeasuredHeight();server.setHeaderInset(hostColumns?headerHeight:0,h);
  content.measure(exact(w),exact(hostColumns?h:Math.max(0,h-headerHeight)));setMeasuredDimension(w,h);
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){View tabs=findViewById(R.id.homeRoleTabs),content=findViewById(R.id.homeRoleContent);content.layout(0,hostColumns?0:headerHeight,getWidth(),getHeight());tabs.layout(0,0,headerWidth,headerHeight);}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private static int exact(int n){return MeasureSpec.makeMeasureSpec(n,MeasureSpec.EXACTLY);}
}
