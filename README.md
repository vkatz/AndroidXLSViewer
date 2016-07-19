# Android XLS Viewer library

Simple Android widget wich allow to render and look *.xls files (excel / spreadsheet)
Allow to draw only( but easy to add edit functionality)
Zoom/move are in.

Imlpemented elements:
font bold/normal + color
borders - only sides (bold and normal) + colors
images + cache optimization

Use as u want. Provided as it is.

Examples: http://imgur.com/a/pqfZX

##Layout
```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:orientation="vertical"
        android:layout_width="fill_parent"
        android:layout_height="fill_parent"
        android:background="#fff">
    <TabHost android:layout_width="match_parent"
             android:layout_height="match_parent"
             android:id="@+id/sheets">
        <LinearLayout android:layout_width="match_parent"
                      android:layout_height="match_parent"
                      android:orientation="vertical">
            <HorizontalScrollView android:layout_width="match_parent"
                                  android:layout_height="wrap_content">
                <TabWidget android:id="@android:id/tabs"
                           android:layout_width="match_parent"
                           android:layout_height="wrap_content">
                </TabWidget>
            </HorizontalScrollView>
            <FrameLayout android:id="@android:id/tabcontent"
                         android:layout_width="match_parent"
                         android:layout_height="match_parent">
            </FrameLayout>
        </LinearLayout>
    </TabHost>
</RelativeLayout>
```

##How to add tabs
```java
 InputStream xlsStream = new FileInputStream(file);
 Workbook xl = Workbook.getWorkbook(xlsStream);
 TabHost tabHost = (TabHost) findViewById(R.id.sheets);
 tabHost.setup();
 for (final Sheet sheet : xl.getSheets()) {
     TabHost.TabSpec tabSpec = tabHost.newTabSpec(sheet.getName());
     tabSpec.setContent(new TabHost.TabContentFactory() {
         @Override
         public View createTabContent(String s) {
             XlsSheetView view = new XlsSheetView(XLSActivity.this);
             view.setSheet(sheet);
             return view;
         }
     });
     tabSpec.setIndicator("    " + sheet.getName() + "    ");
     tabHost.addTab(tabSpec);
 }
```

##Say to for me:
WMR R109844875467
WMZ Z424415381288
