package de.telekom.messepresenterdev.widgets;

import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.widget.Scroller;
import common.LengthUnit;
import jxl.*;
import jxl.format.*;
import jxl.format.CellFormat;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Katz on 14.07.2016.
 */
@SuppressWarnings("WeakerAccess")
public class XlsSheetView extends TextureView {
    private static final float H_SCALE_FACTOR = 14.75f;
    private static final float W_SCALE_FACTOR = 36.40f;
    private static final float FONT_SCALE_FACTOR = 1.25f;
    private static final float IMAGE_W_SCALE_FACTOR = 1.19f;
    private static final float IMAGE_H_SCALE_FACTOR = 1.29f;
    private static final float MAX_ZOOM = 4f;
    private static final float FIXED_CELL_WIDTH = 45;
    private static final float FIXED_CELL_HEIGHT = 25;
    private static final float DEFAULT_TEXT_PADDING = 2;
    private static final float DEFAULT_TEXT_SIZE = 12;
    private static final int DEFAULT_TEXT_COLOR = Color.BLACK;

    //values
    private float dp = 0f;
    private int cellTextPadding;
    private int fixedHeight;
    private int fixedWidth;

    //interactions
    private GestureDetector moveDetector;
    private ScaleGestureDetector scaleDetector;
    private Scroller scroller;

    //sheet data
    private int sheetWidth, sheetHeight;

    //display data
    private Sheet sheet;
    private Paint paint;
    private TextPaint textPaint;
    private boolean sheetReady = false;
    private float previousZoom = 1f, zoom = 1f;
    private int rowSizes[];
    private int columnSizes[];

    //optimizations
    private HashMap<Integer, Bitmap> imagesCache = new HashMap<>();
    private Rect tmpRect = new Rect();
    private RectF tmpRectF = new RectF();
    private HashMap<String, Layout> layoutsMap = new HashMap<>();
    private HashSet<String> unusedLayoutsMap = new HashSet<>();
    private AtomicBoolean needRedraw = new AtomicBoolean(true);

    //init surface
    {
        setSurfaceTextureListener(new SurfaceTextureListener() {
            private Thread renderThread;
            private AtomicBoolean surfaceReady;

            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surfaceTexture, int i, int i1) {
                surfaceReady = new AtomicBoolean(true);
                renderThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (surfaceReady.get()) {
                            if (needRedraw.get()) {
                                needRedraw.set(false);
                                boolean repeat = true;
                                while (repeat) {
                                    Canvas canvas = lockCanvas();
                                    if (canvas != null) {
                                        repeat = drawSheet(canvas);
                                        unlockCanvasAndPost(canvas);
                                    } else repeat = false;
                                }
                            }
                        }
                    }
                });
                renderThread.start();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                surfaceReady.set(false);
                boolean retry = true;
                while (retry) {
                    try {
                        renderThread.join();
                        retry = false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        });
    }

    public XlsSheetView(Context context) {
        super(context);
    }

    public XlsSheetView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public XlsSheetView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSheet(Sheet sheet) {
        dp = getContext().getResources().getDisplayMetrics().density;
        fixedWidth = (int) (dp * FIXED_CELL_WIDTH);
        fixedHeight = (int) (dp * FIXED_CELL_HEIGHT);
        cellTextPadding = (int) (dp * DEFAULT_TEXT_PADDING);

        this.sheet = sheet;
        float hScale = dp / H_SCALE_FACTOR;
        float wScale = dp / W_SCALE_FACTOR;
        int columnOffset = fixedWidth;
        int rowOffset = fixedHeight;

        columnSizes = new int[sheet.getColumns() + 1];
        for (int i = 0; i < sheet.getColumns(); i++) {
            columnSizes[i] = columnOffset;
            CellView columnView = sheet.getColumnView(i);
            if (!columnView.isHidden()) columnOffset += (int) (columnView.getSize() * wScale);
        }
        columnSizes[sheet.getColumns()] = columnOffset;

        rowSizes = new int[sheet.getRows() + 1];
        for (int i = 0; i < sheet.getRows(); i++) {
            rowSizes[i] = rowOffset;
            CellView rowView = sheet.getRowView(i);
            if (!rowView.isHidden()) rowOffset += (int) (rowView.getSize() * hScale);
        }
        rowSizes[sheet.getRows()] = rowOffset;

        sheetWidth = columnOffset;
        sheetHeight = rowOffset;
        paint = new Paint();
        paint.setAntiAlias(true);
        textPaint = new TextPaint(paint);
        textPaint.setTextSize(dp * 18);
        paint.setTextSize(dp * 18);
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return super.onScale(detector);
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                previousZoom = zoom;
                super.onScaleEnd(detector);
            }
        });
        moveDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                scrollBy((int) (distanceX / zoom), (int) (distanceY / zoom));
                return super.onScroll(e1, e2, distanceX, distanceY);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                flingBy((int) velocityX, (int) velocityY);
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });
        scroller = new Scroller(getContext());
        sheetReady = true;
    }

    private float clamp(float val, float min, float max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }

    private int getMaxScrollX() {
        return (int) Math.max(0, (sheetWidth - getMeasuredWidth() / zoom));
    }

    private int getMaxScrollY() {
        return (int) Math.max(0, (sheetHeight - getMeasuredHeight() / zoom));
    }

    @Override
    public void scrollBy(int distanceX, int distanceY) {
        scroller.startScroll(0, 0, (int) clamp(scroller.getCurrX() + distanceX, 0, getMaxScrollX()), (int) clamp(scroller.getCurrY() + distanceY, 0, getMaxScrollY()), 0);
        scroller.computeScrollOffset();
        redraw();
    }

    public void flingBy(int velocityX, int velocityY) {
        scroller.fling(scroller.getCurrX(), scroller.getCurrY(), -velocityX, -velocityY, 0, getMaxScrollX(), 0, getMaxScrollY());
        redraw();
    }

    public void zoomBy(float dz, float fx, float fy) {
        float tz = zoom;
        zoom = clamp(previousZoom * dz, 1, MAX_ZOOM);
        float ddx = (fx / tz) * (zoom / tz - 1);
        float ddy = (fy / tz) * (zoom / tz - 1);
        scrollBy((int) ddx, (int) ddy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) scroller.forceFinished(true);
        moveDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) redraw();
    }

    public void redraw() {
        needRedraw.set(true);
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    private String intToColumnName(int column) {
        String sChars = "0ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String sCol = "";
        while (column > 26) {
            int nChar = column % 26;
            if (nChar == 0) nChar = 26;
            column = (column - nChar) / 26;
            sCol = sChars.charAt(nChar) + sCol;
        }
        if (column != 0) sCol = sChars.charAt(column) + sCol;
        return sCol;
    }

    private boolean isOnScreen(int pos, boolean fixed, int small, int big, int fixedSize) {
        return small + (fixed ? 0 : fixedSize) <= pos && pos <= big;
    }

    private boolean isOnScreen(int pos, int size, boolean fixed, int small, int big, int fixedSize) {
        return small + (fixed ? 0 : fixedSize) <= pos + size && pos <= big;
    }

    private boolean isNeedClip(int x, int y, int left, int top, boolean fixedX, boolean fixedY, int fw, int fh) {
        return (!fixedX && x - left < fw) || (!fixedY && y - top < fh);
    }

    /**
     * @param canvas - drawing canvas
     * @return true, if need redraw (in scroll)
     */
    @SuppressWarnings("ConstantConditions")
    private boolean drawSheet(Canvas canvas) {
        if (!sheetReady) return false;
        //limits
        int fixedColumns = sheet.getSettings().getHorizontalFreeze();
        int fixedRows = sheet.getSettings().getVerticalFreeze();
        scroller.computeScrollOffset();
        int fixedColumnSize = columnSizes[fixedColumns];
        int fixedRowSize = rowSizes[fixedRows];
        int fixColumnOffset = fixedColumns == 0 ? 0 : (int) (3 * dp);
        int fixRowOffset = fixedRows == 0 ? 0 : (int) (3 * dp);
        int l = scroller.getCurrX();
        int t = scroller.getCurrY();
        int r = l + getMeasuredWidth();
        int b = t + getMeasuredHeight();
        //prepare
        canvas.drawColor(Color.WHITE);
        canvas.scale(zoom, zoom);
        canvas.translate(-l, -t);
        Range[] mergedCells = sheet.getMergedCells();
        unusedLayoutsMap.addAll(layoutsMap.keySet());
        //draw lines
        paint.setColor(0xffcccccc);
        for (int i = 0; i < columnSizes.length; i++) {
            int value = columnSizes[i];
            if (i <= fixedColumns) value += l;
            else value += fixColumnOffset;
            if (isOnScreen(value, i <= fixedColumns, l, r, fixedColumnSize))
                canvas.drawRect(value - dp, 0, value, sheetHeight, paint);
        }
        for (int i = 0; i < rowSizes.length; i++) {
            int value = rowSizes[i];
            if (i <= fixedRows) value += t;
            else value += fixRowOffset;
            if (isOnScreen(value, i <= fixedRows, t, b, fixedRowSize))
                canvas.drawRect(0, value - dp, sheetWidth, value, paint);
        }
        //draw table
        for (int i = 0; i < sheet.getColumns(); i++) {
            for (int j = 0; j < sheet.getRows(); j++) {
                Cell cell = sheet.getCell(i, j);
                CellFormat cellFormat = cell.getCellFormat();
                int x = columnSizes[i];
                int y = rowSizes[j];
                int w = columnSizes[i + 1] - x;
                int h = rowSizes[j + 1] - y;
                if (w == 0 || h == 0) continue;
                if (i < fixedColumns) x += l;
                else x += fixColumnOffset;
                if (j < fixedRows) y += t;
                else y += fixRowOffset;
                if (!isOnScreen(x, w, i < fixedColumns, l, r, fixedColumnSize) || !isOnScreen(y, h, j < fixedRows, t, b, fixedRowSize)) continue;
                //check for merged
                boolean proceed = true;
                boolean mergedCellRoot = false;
                for (Range mc : mergedCells) {
                    Cell tl = mc.getTopLeft();
                    Cell br = mc.getBottomRight();
                    if (cell == tl) {
                        w = columnSizes[br.getColumn() + 1] - x;
                        h = rowSizes[br.getRow() + 1] - y;
                        mergedCellRoot = true;
                    } else if (i >= tl.getColumn() && i <= br.getColumn() && j >= tl.getRow() && j <= br.getRow())
                        proceed = false;
                }
                if (!proceed) continue;
                int save = 0;
                boolean clipped = false;
                //clip cells that intercept freezed cells
                if (isNeedClip(x, y, l, t, i < fixedColumns, j < fixedRows, fixedColumnSize, fixedRowSize)) {
                    save = canvas.save();
                    clipped = true;
                    tmpRect.set(i < fixedColumns ? 0 : fixedColumnSize, j < fixedRows ? 0 : fixedRowSize, getMeasuredWidth(), getMeasuredHeight());
                    tmpRect.offset(l, t);
                    canvas.clipRect(tmpRect);
                }
                if (mergedCellRoot) {
                    paint.setColor(Color.WHITE);
                    canvas.drawRect(x, y, x + w - dp, y + h - dp, paint);
                }
                if (cellFormat != null) {
                    RGB rgb = cellFormat.getBackgroundColour().getDefaultRGB();
                    int bgColor = Color.rgb(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
                    if (bgColor != Color.WHITE) {
                        paint.setColor(bgColor);
                        canvas.drawRect(x - dp, y - dp, x + w, y + h, paint);
                    }
                }
                if (cell.getContents() != null)
                    drawText("" + i + "-" + j, x, y, w, h, cell.getContents(), cellFormat, canvas);
                if (clipped) canvas.restoreToCount(save);
            }
        }
        //draw borders
        for (int i = 0; i < sheet.getColumns(); i++) {
            for (int j = 0; j < sheet.getRows(); j++) {
                int x = columnSizes[i];
                int y = rowSizes[j];
                int w = columnSizes[i + 1] - x;
                int h = rowSizes[j + 1] - y;
                if (w == 0 || h == 0) continue;
                if (i < fixedColumns) x += l;
                else x += fixColumnOffset;
                if (j < fixedRows) y += t;
                else y += fixRowOffset;
                if (!isOnScreen(x, w, i < fixedColumns, l, r, fixedColumnSize) || !isOnScreen(y, h, j < fixedRows, t, b, fixedRowSize)) continue;
                Cell cell = sheet.getCell(i, j);
                CellFormat cellFormat = cell.getCellFormat();
                if (cellFormat != null) {
                    int save = 0;
                    boolean clipped = false;
                    //clip cells that intercept freezed cells
                    if (isNeedClip(x, y, l, t, i < fixedColumns, j < fixedRows, fixedColumnSize, fixedRowSize)) {
                        save = canvas.save();
                        clipped = true;
                        tmpRect.set(i < fixedColumns ? 0 : fixedColumnSize, j < fixedRows ? 0 : fixedRowSize, getMeasuredWidth(), getMeasuredHeight());
                        tmpRect.offset(l, t);
                        canvas.clipRect(tmpRect);
                    }
                    drawBorder(x - dp, y - dp, dp, h + dp, cellFormat.getBorderLine(Border.LEFT), cellFormat.getBorderColour(Border.LEFT), canvas);
                    drawBorder(x - dp, y - dp, w + dp, dp, cellFormat.getBorderLine(Border.TOP), cellFormat.getBorderColour(Border.TOP), canvas);
                    drawBorder(x - dp + w, y - dp, dp, h + dp, cellFormat.getBorderLine(Border.RIGHT), cellFormat.getBorderColour(Border.RIGHT), canvas);
                    drawBorder(x - dp, y - dp + h, w + dp, dp, cellFormat.getBorderLine(Border.BOTTOM), cellFormat.getBorderColour(Border.BOTTOM), canvas);
                    if (clipped) canvas.restoreToCount(save);
                }
            }
        }
        //draw images
        for (int k = 0; k < sheet.getNumberOfImages(); k++) {
            Image drawing = sheet.getDrawing(k);
            double rawX = drawing.getColumn();
            double rawY = drawing.getRow();
            int i = (int) rawX;
            int j = (int) rawY;
            int x = (int) (columnSizes[i] + (rawX % 1) * (columnSizes[i + 1] - columnSizes[i]));
            int y = (int) (rowSizes[j] + (rawY % 1) * (rowSizes[j + 1] - rowSizes[j]));
            int w = (int) (drawing.getWidth(LengthUnit.POINTS) * IMAGE_W_SCALE_FACTOR * dp);
            int h = (int) (drawing.getHeight(LengthUnit.POINTS) * IMAGE_H_SCALE_FACTOR * dp);
            if (i < fixedColumns) x += l;
            else x += fixColumnOffset;
            if (j < fixedRows) y += t;
            else y += fixRowOffset;
            //if not on screen, recycle(if necessary) and ignore
            if (!isOnScreen(x, w, i < fixedColumns, l, r, fixedColumnSize) || !isOnScreen(y, h, j < fixedRows, t, b, fixedRowSize)) {
                if (imagesCache.containsKey(k)) {
                    imagesCache.get(k).recycle();
                    imagesCache.remove(k);
                }
                continue;
            }
            //else draw (with cache)
            Bitmap bitmap;
            if (imagesCache.containsKey(k)) bitmap = imagesCache.get(k);
            else {
                byte[] imageData = drawing.getImageData();
                bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                imagesCache.put(k, bitmap);
            }
            //clip canvas before draw, tmpRectF avoid draw out of bounds for freezed cells
            tmpRect.set(i < fixedColumns ? 0 : fixedColumnSize, j < fixedRows ? 0 : fixedRowSize, getMeasuredWidth(), getMeasuredHeight());
            tmpRect.offset(l, t);
            int save = canvas.save();
            canvas.clipRect(tmpRect);
            tmpRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            tmpRectF.set(x, y, x + w, y + h);
            canvas.drawBitmap(bitmap, tmpRect, tmpRectF, paint);
            canvas.restoreToCount(save);
        }
        //draw col/row names
        for (int i = sheet.getColumns() - 1; i >= 0; i--) {
            int x = columnSizes[i];
            int w = columnSizes[i + 1] - x;
            if (i < fixedColumns) x += l;
            else x += fixColumnOffset;
            if (!isOnScreen(x, w, i < fixedColumns, l, r, fixedColumnSize)) continue;
            paint.setColor(0xffcccccc);
            canvas.drawRect(x - dp, t, x + w, t + fixedHeight, paint);
            paint.setColor(0xfff3f3f3);
            canvas.drawRect(x, t + dp, x + w - dp, t + fixedHeight - dp, paint);
            paint.setColor(Color.BLACK);
            drawText("c" + i, x, t, w, fixedHeight, intToColumnName(i + 1), null, canvas);
        }
        for (int i = sheet.getRows() - 1; i >= 0; i--) {
            int y = rowSizes[i];
            int h = rowSizes[i + 1] - y;
            if (i < fixedRows) y += t;
            else y += fixRowOffset;
            if (!isOnScreen(y, h, i < fixedRows, t, b, fixedRowSize)) continue;
            paint.setColor(0xffcccccc);
            canvas.drawRect(l, y - dp, l + fixedWidth, y + h, paint);
            paint.setColor(0xfff3f3f3);
            canvas.drawRect(l + dp, y, l + fixedWidth - dp, y + h - dp, paint);
            paint.setColor(Color.BLACK);
            drawText("r" + i, l, y, fixedWidth, h, "" + (i + 1), null, canvas);
        }
        paint.setColor(0xffcccccc);
        canvas.drawRect(l, t, l + fixedWidth, t + fixedHeight, paint);
        paint.setColor(0xfff3f3f3);
        canvas.drawRect(l + dp, t + dp, l + fixedWidth - dp, t + fixedHeight - dp, paint);
        //draw freezed zones borders
        paint.setColor(0xffcccccc);
        if (fixedColumns > 0) canvas.drawRect(fixedColumnSize + l - dp, t, fixedColumnSize + fixColumnOffset + l, b, paint);
        if (fixedRows > 0) canvas.drawRect(l, fixedRowSize + t - dp, r, fixedRowSize + fixRowOffset + t, paint);
        //clear unused layouts
        for (String i : unusedLayoutsMap) layoutsMap.remove(i);
        unusedLayoutsMap.clear();
        System.gc();
        //redraw in case we are in fling
        return !scroller.isFinished();
    }

    private void drawBorder(float x, float y, float w, float h, BorderLineStyle style, Colour color, Canvas canvas) {
        if (style == BorderLineStyle.NONE) return;
        float size;
        if (style == BorderLineStyle.THIN) size = 0;
        else size = dp / 2;
        if (color.getValue() == 64) paint.setColor(Color.BLACK); //set automatic color tmpRectF black, default is white
        else {
            RGB rgb = color.getDefaultRGB();
            paint.setColor(Color.rgb(rgb.getRed(), rgb.getGreen(), rgb.getBlue()));
        }
        canvas.drawRect(x - size, y - size, x + w + size, y + h + size, paint);
    }

    private void drawText(String id, int x, int y, int w, int h, String text, CellFormat cellFormat, Canvas canvas) {
        int fw = w - 2 * cellTextPadding;
        int fh = h - 2 * cellTextPadding;
        if (fw <= 0 || fh <= 0) return;
        canvas.save();
        canvas.translate(x + cellTextPadding, y + cellTextPadding);
        if (canvas.clipRect(0, 0, fw, fh)) {
            Layout.Alignment hAlign;
            int vAlign; //0 - top, 1-mid, 2-bot
            if (cellFormat != null) {
                Font font = cellFormat.getFont();
                RGB rgb = font.getColour().getDefaultRGB();
                textPaint.setColor(Color.rgb(rgb.getRed(), rgb.getGreen(), rgb.getBlue()));
                textPaint.setTextSize(font.getPointSize() * FONT_SCALE_FACTOR * dp);
                textPaint.setTypeface(font.getBoldWeight() > 400 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                textPaint.setUnderlineText(font.getUnderlineStyle() != UnderlineStyle.NO_UNDERLINE);
                if (cellFormat.getAlignment() == Alignment.RIGHT || (cellFormat.getAlignment() == Alignment.GENERAL && isRightAlignDefault(text, cellFormat))) hAlign = Layout.Alignment.ALIGN_OPPOSITE;
                else if (cellFormat.getAlignment() == Alignment.CENTRE) hAlign = Layout.Alignment.ALIGN_CENTER;
                else hAlign = Layout.Alignment.ALIGN_NORMAL;
                if (cellFormat.getVerticalAlignment() == VerticalAlignment.BOTTOM) vAlign = 2;
                else if (cellFormat.getVerticalAlignment() == VerticalAlignment.CENTRE) vAlign = 1;
                else vAlign = 0;
            } else {
                hAlign = Layout.Alignment.ALIGN_CENTER;
                vAlign = 1;
                textPaint.setColor(DEFAULT_TEXT_COLOR);
                textPaint.setTextSize(DEFAULT_TEXT_SIZE * dp);
                textPaint.setTypeface(Typeface.DEFAULT);
                textPaint.setUnderlineText(false);
            }

            Layout layout;
            if (layoutsMap.containsKey(id)) layout = layoutsMap.get(id);
            else {
                layout = new StaticLayout(text, textPaint, fw, hAlign, 1, 0, false);
                layoutsMap.put(id, layout);
            }
            unusedLayoutsMap.remove(id);
            //no need adjust for vAlign ==0 (top)
            if (vAlign == 1) canvas.translate(0, (fh - layout.getHeight()) / 2);
            else if (vAlign == 2) canvas.translate(0, fh - layout.getHeight());
            layout.draw(canvas);
        }
        canvas.restore();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean isRightAlignDefault(String text, CellFormat cellFormat) {
        try {
            Double.valueOf(text);
            return true;
        } catch (NumberFormatException e) {
            try {
                if (!StringUtils.isEmpty(cellFormat.getFormat().getFormatString())) return true;
            } catch (Exception ignored) {
            }
            return false;
        }
    }
}
