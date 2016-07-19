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
import android.view.View;
import android.widget.Scroller;
import common.LengthUnit;
import jxl.Cell;
import jxl.Image;
import jxl.Range;
import jxl.Sheet;
import jxl.format.*;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;

/**
 * Created by Katz on 14.07.2016.
 */
@SuppressWarnings("WeakerAccess")
public class XlsSheetView extends View {
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
    private boolean ready = false;
    private float previousZoom = 1f, zoom = 1f;
    private int hSizes[];
    private int wSizes[];

    //images optimizations
    private HashMap<Integer, Bitmap> imagesCache;
    private Rect from = new Rect();
    private RectF to = new RectF();

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
        imagesCache = new HashMap<>();

        this.sheet = sheet;
        float hScale = dp / H_SCALE_FACTOR;
        float wScale = dp / W_SCALE_FACTOR;
        int columnOffset = fixedWidth;
        int rowOffset = fixedHeight;

        wSizes = new int[sheet.getColumns() + 1];
        for (int i = 0; i < sheet.getColumns(); i++) {
            wSizes[i] = columnOffset;
            columnOffset += (int) (sheet.getColumnView(i).getSize() * wScale);
        }
        wSizes[sheet.getColumns()] = columnOffset;

        hSizes = new int[sheet.getRows() + 1];
        for (int i = 0; i < sheet.getRows(); i++) {
            hSizes[i] = rowOffset;
            rowOffset += (int) (sheet.getRowView(i).getSize() * hScale);
        }
        hSizes[sheet.getRows()] = rowOffset;

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
        ready = true;
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
        invalidate();
    }

    public void flingBy(int velocityX, int velocityY) {
        scroller.fling(scroller.getCurrX(), scroller.getCurrY(), -velocityX, -velocityY, 0, getMaxScrollX(), 0, getMaxScrollY());
        invalidate();
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (ready) drawSheet(canvas);
    }

    private String intToCollumName(int column) {
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

    private void drawSheet(Canvas canvas) {
        scroller.computeScrollOffset();
        canvas.drawColor(Color.WHITE);
        canvas.scale(zoom, zoom);
        canvas.translate(-scroller.getCurrX(), -scroller.getCurrY());
        Range[] mergedCells = sheet.getMergedCells();
        //limits
        float l = scroller.getCurrX();
        float t = scroller.getCurrY();
        float r = l + getMeasuredWidth();
        float b = t + getMeasuredHeight();
        //draw lines
        paint.setColor(0xffcccccc);
        for (int i : wSizes)
            if (i >= l && i <= r)
                canvas.drawRect(i - dp, 0, i, sheetHeight, paint);
        for (int i : hSizes)
            if (i >= t && i <= b)
                canvas.drawRect(0, i - dp, sheetWidth, i, paint);
        //draw table
        paint.setColor(Color.BLACK);
        for (int i = 0; i < sheet.getColumns(); i++) {
            for (int j = 0; j < sheet.getRows(); j++) {
                if (wSizes[i + 1] < l || hSizes[j + 1] < t || wSizes[i] > r || hSizes[j] > b) continue;
                Cell cell = sheet.getCell(i, j);
                CellFormat cellFormat = cell.getCellFormat();
                int x = wSizes[i];
                int y = hSizes[j];
                int w = wSizes[i + 1] - x;
                int h = hSizes[j + 1] - y;
                //check for merged
                boolean proceed = true;
                for (Range mc : mergedCells) {
                    Cell tl = mc.getTopLeft();
                    Cell br = mc.getBottomRight();
                    if (cell == tl) {
                        w = wSizes[br.getColumn() + 1] - x;
                        h = hSizes[br.getRow() + 1] - y;
                        paint.setColor(Color.WHITE);
                        canvas.drawRect(x, y, x + w - dp, y + h - dp, paint);
                    } else if (i >= tl.getColumn() && i <= br.getColumn() && j >= tl.getRow() && j <= br.getRow())
                        proceed = false;
                }
                if (!proceed) continue;
                if (cellFormat != null) {
                    RGB rgb = cellFormat.getBackgroundColour().getDefaultRGB();
                    int bgColor = Color.rgb(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
                    if (bgColor != Color.WHITE) {
                        paint.setColor(bgColor);
                        canvas.drawRect(x - dp, y - dp, x + w, y + h, paint);
                    }
                }
                if (cell.getContents() != null)
                    drawText(x, y, w, h, cell.getContents(), cellFormat, canvas);
            }
        }
        //draw borders
        for (int i = 0; i < sheet.getColumns(); i++) {
            for (int j = 0; j < sheet.getRows(); j++) {
                if (wSizes[i + 1] < l || hSizes[j + 1] < t || wSizes[i] > r || hSizes[j] > b) continue;
                Cell cell = sheet.getCell(i, j);
                CellFormat cellFormat = cell.getCellFormat();
                int x = wSizes[i];
                int y = hSizes[j];
                int w = wSizes[i + 1] - x;
                int h = hSizes[j + 1] - y;
                if (cellFormat != null) {
                    drawBorder(x - dp, y - dp, dp, h + dp, cellFormat.getBorderLine(Border.LEFT), cellFormat.getBorderColour(Border.LEFT), canvas);
                    drawBorder(x - dp + w, y - dp, dp, h + dp, cellFormat.getBorderLine(Border.RIGHT), cellFormat.getBorderColour(Border.RIGHT), canvas);
                    drawBorder(x - dp, y - dp, w + dp, dp, cellFormat.getBorderLine(Border.TOP), cellFormat.getBorderColour(Border.TOP), canvas);
                    drawBorder(x - dp, y - dp + h, w + dp, dp, cellFormat.getBorderLine(Border.BOTTOM), cellFormat.getBorderColour(Border.BOTTOM), canvas);
                }
            }
        }
        //draw images
        for (int i = 0; i < sheet.getNumberOfImages(); i++) {
            Image drawing = sheet.getDrawing(i);
            double rawX = drawing.getColumn();
            double rawY = drawing.getRow();
            float x = (float) (wSizes[(int) rawX] + (rawX % 1) * (wSizes[(int) rawX + 1] - wSizes[(int) rawX]));
            float y = (float) (hSizes[(int) rawY] + (rawY % 1) * (hSizes[(int) rawY + 1] - hSizes[(int) rawY]));
            float w = (float) drawing.getWidth(LengthUnit.POINTS) * IMAGE_W_SCALE_FACTOR * dp;
            float h = (float) drawing.getHeight(LengthUnit.POINTS) * IMAGE_H_SCALE_FACTOR * dp;
            if (x > r || y > b || x + w < l || y + h < t) {
                if (imagesCache.containsKey(i)) {
                    imagesCache.get(i).recycle();
                    imagesCache.remove(i);
                    continue;
                }
            }
            Bitmap bitmap;
            if (imagesCache.containsKey(i)) bitmap = imagesCache.get(i);
            else {
                byte[] imageData = drawing.getImageData();
                bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                imagesCache.put(i, bitmap);
            }
            from.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            to.set(x, y, x + w, y + h);
            canvas.drawBitmap(bitmap, from, to, paint);
        }
        //draw col/row names
        for (int i = 0; i < sheet.getColumns(); i++) {
            if (wSizes[i + 1] < l || wSizes[i] > r) continue;
            paint.setColor(0xffcccccc);
            canvas.drawRect(wSizes[i] - dp, scroller.getCurrY(), wSizes[i + 1] + dp, scroller.getCurrY() + fixedHeight + dp, paint);
            paint.setColor(0xfff3f3f3);
            canvas.drawRect(wSizes[i], scroller.getCurrY() + dp, wSizes[i + 1], scroller.getCurrY() + fixedHeight, paint);
            paint.setColor(Color.BLACK);
            drawText(wSizes[i], scroller.getCurrY(), wSizes[i + 1] - wSizes[i], fixedHeight, intToCollumName(i + 1), null, canvas);
        }
        for (int i = 0; i < sheet.getRows(); i++) {
            if (hSizes[i + 1] < t || hSizes[i] > b) continue;
            paint.setColor(0xffcccccc);
            canvas.drawRect(scroller.getCurrX(), hSizes[i] - dp, scroller.getCurrX() + fixedWidth + dp, hSizes[i + 1] + dp, paint);
            paint.setColor(0xfff3f3f3);
            canvas.drawRect(scroller.getCurrX() + dp, hSizes[i], scroller.getCurrX() + fixedWidth, hSizes[i + 1], paint);
            paint.setColor(Color.BLACK);
            drawText(scroller.getCurrX(), hSizes[i], fixedWidth, hSizes[i + 1] - hSizes[i], "" + (i + 1), null, canvas);
        }
        paint.setColor(0xffcccccc);
        canvas.drawRect(scroller.getCurrX(), scroller.getCurrY(), scroller.getCurrX() + fixedWidth + dp, scroller.getCurrY() + fixedHeight + dp, paint);
        paint.setColor(0xfff3f3f3);
        canvas.drawRect(scroller.getCurrX() + dp, scroller.getCurrY() + dp, scroller.getCurrX() + fixedWidth, scroller.getCurrY() + fixedHeight, paint);
        if (!scroller.isFinished()) postInvalidate();
    }

    private void drawBorder(float x, float y, float w, float h, BorderLineStyle style, Colour color, Canvas canvas) {
        if (style == BorderLineStyle.NONE) return;
        float size;
        if (style == BorderLineStyle.THIN) size = 0;
        else size = dp;
        RGB rgb = color.getDefaultRGB();
        paint.setColor(Color.rgb(rgb.getRed(), rgb.getGreen(), rgb.getBlue()));
        canvas.drawRect(x - size, y - size, x + w + size, y + h + size, paint);
    }

    private void drawText(int x, int y, int w, int h, String text, CellFormat cellFormat, Canvas canvas) {
        canvas.save();
        canvas.translate(x + cellTextPadding, y + cellTextPadding);
        int fw = w - 2 * cellTextPadding;
        int fh = h - 2 * cellTextPadding;
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
            StaticLayout layout = new StaticLayout(text, textPaint, fw, hAlign, 1, 0, false);
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