package com.boveda.archivos;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.IOException;
import java.io.OutputStream;

/** Arma un PDF de verdad (tamaño carta, márgenes, título y texto acomodado en varias páginas). */
final class PdfMaker {
    private static final int W = 612, H = 792, MARGIN = 56;   // carta, en puntos (1/72")

    private PdfMaker() {}

    static void write(String title, String body, OutputStream out) throws IOException {
        PdfDocument doc = new PdfDocument();
        int width = W - 2 * MARGIN;
        TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.rgb(20, 24, 36));
        titlePaint.setTextSize(20);
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(30, 34, 44));
        textPaint.setTextSize(11.5f);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));

        StaticLayout titleLayout = title == null || title.isEmpty() ? null
                : StaticLayout.Builder.obtain(title, 0, title.length(), titlePaint, width).build();
        String text = body == null ? "" : body;
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(3, 1f)
                .build();

        int line = 0, pageNo = 1;
        int lines = layout.getLineCount();
        do {
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
            Canvas c = page.getCanvas();
            float y = MARGIN;
            if (pageNo == 1 && titleLayout != null) {
                c.save();
                c.translate(MARGIN, y);
                titleLayout.draw(c);
                c.restore();
                y += titleLayout.getHeight() + 18;
            }
            float available = H - MARGIN - 24 - y;
            // cuántas líneas caben en esta página
            int startLine = line;
            float startTop = lines > 0 ? layout.getLineTop(startLine) : 0;
            while (line < lines && layout.getLineBottom(line) - startTop <= available) line++;
            if (line == startLine && line < lines) line++;   // una línea enorme: igual se pone
            if (lines > 0 && line > startLine) {
                float endBottom = layout.getLineBottom(line - 1);
                c.save();
                c.translate(MARGIN, y - startTop);
                c.clipRect(0, startTop, width, endBottom);
                layout.draw(c);
                c.restore();
            }
            // número de página
            TextPaint foot = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            foot.setColor(Color.rgb(140, 146, 160));
            foot.setTextSize(9);
            String n = String.valueOf(pageNo);
            c.drawText(n, (W - foot.measureText(n)) / 2f, H - MARGIN / 2f, foot);
            doc.finishPage(page);
            pageNo++;
        } while (line < lines);
        try {
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }
}
