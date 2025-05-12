package com.example.cameraapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

public class OverlayView extends View {
    private List<NormalizedLandmark> landmarks;

    public OverlayView(Context context) {
        super(context);
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setLandmarks(List<NormalizedLandmark> landmarks) {
        this.landmarks = landmarks;
        invalidate(); // 다시 그리기 요청
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (landmarks == null) return;

        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(8f);

        // 랜드마크를 그린다.
        for (NormalizedLandmark landmark : landmarks) {
            float x = landmark.x() * getWidth();
            float y = landmark.y() * getHeight();
            canvas.drawCircle(x, y, 10, paint);  // 각 랜드마크를 원으로 그린다.
        }

        // 스켈레톤 그리기: 각 부위별 랜드마크를 연결
        drawSkeleton(canvas, paint);
    }

    private void drawSkeleton(Canvas canvas, Paint paint) {
        // 스켈레톤을 구성하는 랜드마크 인덱스 쌍 (연결해야 할 랜드마크들)
        int[][] connections = {
                {11, 13}, {13, 15},  // 왼쪽 어깨 -> 팔꿈치 -> 손목
                {12, 14}, {14, 16},  // 오른쪽 어깨 -> 팔꿈치 -> 손목
                {11, 12},             // 왼쪽 어깨 -> 오른쪽 어깨
                {23, 25}, {25, 27},  // 왼쪽 엉덩이 -> 무릎 -> 발목
                {24, 26}, {26, 28},  // 오른쪽 엉덩이 -> 무릎 -> 발목
                {23, 24},             // 왼쪽 엉덩이 -> 오른쪽 엉덩이
                {11, 23}, {12, 24},  // 왼쪽 어깨 -> 왼쪽 엉덩이, 오른쪽 어깨 -> 오른쪽 엉덩이
                {27, 29}, {28, 30}   // 왼쪽 발목 -> 왼쪽 발끝, 오른쪽 발목 -> 오른쪽 발끝
        };

        // 연결된 랜드마크들 간에 선을 그린다.
        for (int[] connection : connections) {
            NormalizedLandmark start = landmarks.get(connection[0]);
            NormalizedLandmark end = landmarks.get(connection[1]);

            // 랜드마크의 (x, y) 좌표를 화면 좌표로 변환
            float startX = start.x() * getWidth();
            float startY = start.y() * getHeight();
            float endX = end.x() * getWidth();
            float endY = end.y() * getHeight();

            // 선을 그린다
            canvas.drawLine(startX, startY, endX, endY, paint);
        }
    }
}
