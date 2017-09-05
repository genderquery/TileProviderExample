package example.com.tileproviderexample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, GoogleMap.OnCameraMoveListener, GoogleMap.OnMapClickListener,
        GoogleMap.OnMapLongClickListener {

    private DebugMarkerFactory debugMarkerFactory;
    private GoogleMap googleMap;
    private DrawingState drawingState = DrawingState.DONE;
    private List<LatLng> points = new ArrayList<>();
    private TileOverlay tileOverlay;
    private Marker marker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        debugMarkerFactory = new DebugMarkerFactory(this);

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (drawingState) {
                    case DONE:
                        drawingState = DrawingState.DRAWING;
                        points.clear();
                        tileOverlay.clearTileCache();
                        fab.setImageResource(R.drawable.ic_fab_done);
                        break;
                    case DRAWING:
                        drawingState = DrawingState.DONE;
                        fab.setImageResource(R.drawable.ic_fab_add);
                        break;
                }
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        tileOverlay = googleMap.addTileOverlay(new TileOverlayOptions()
                .tileProvider(new DebugTileProvider(this)));
        googleMap.setOnCameraMoveListener(this);
        googleMap.setOnMapClickListener(this);
        googleMap.setOnMapLongClickListener(this);
    }

    @Override
    public void onCameraMove() {
        if (Float.isNaN(googleMap.getCameraPosition().zoom)) {
            googleMap.moveCamera(CameraUpdateFactory
                    .zoomTo(0));
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (drawingState == DrawingState.DRAWING) {
            points.add(latLng);
            tileOverlay.clearTileCache();
        } else {
            if (marker != null) {
                marker.remove();
            }
            marker = googleMap.addMarker(debugMarkerFactory.createMarker(latLng));
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        if (marker != null) {
            marker.remove();
        }
    }

    private enum DrawingState {
        DONE,
        DRAWING
    }

    private static class DebugMarkerFactory {

        private static final String TAG = "DebugMarkerFactory";
        private static final int WIDTH = 100;
        private static final int HEIGHT = 40;

        private final Paint.FontMetrics fontMetrics;
        private final Paint textPaint;
        private final Paint outlinePaint;
        private final int scaledWidth;
        private final int scaledHeight;
        private final DisplayMetrics displayMetrics;

        DebugMarkerFactory(@NonNull Context context) {
            displayMetrics = context.getResources().getDisplayMetrics();
            scaledWidth = (int) (WIDTH * displayMetrics.density);
            scaledHeight = (int) (HEIGHT * displayMetrics.density);

            textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setHinting(Paint.HINTING_ON);
            textPaint.setTextSize(12 * displayMetrics.scaledDensity);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.BLUE);
            fontMetrics = textPaint.getFontMetrics();

            outlinePaint = new Paint(textPaint);
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(2);
        }

        MarkerOptions createMarker(LatLng latLng) {
            Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            String textLat = String.format(Locale.US, "lat: %+07.4f", latLng.latitude);
            String textLng = String.format(Locale.US, "lng: %+07.4f", latLng.longitude);

            Path latPath = new Path();
            textPaint.getTextPath(textLat, 0, textLat.length(),
                    scaledWidth / 2f, -fontMetrics.top, latPath);
            Path lngPath = new Path();
            textPaint.getTextPath(textLng, 0, textLng.length(),
                    scaledWidth / 2f, -fontMetrics.top * 2f, lngPath);

            canvas.drawPath(latPath, outlinePaint);
            canvas.drawPath(lngPath, outlinePaint);
            canvas.drawPath(latPath, textPaint);
            canvas.drawPath(lngPath, textPaint);

            float radius = 4f * displayMetrics.density;
            canvas.drawCircle(scaledWidth / 2f, scaledHeight - radius, radius, outlinePaint);
            canvas.drawCircle(scaledWidth / 2f, scaledHeight - radius, radius, textPaint);

            return new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    .anchor(0.5f, 1.0f)
                    .position(latLng);
        }
    }

    private class DebugTileProvider implements TileProvider {

        private static final String TAG = "DebugTileProvider";
        private static final int TILE_SIZE = 256;

        private final Paint textPaint;
        private final Paint outlinePaint;
        private final Paint.FontMetrics fontMetrics;
        private final DisplayMetrics displayMetrics;
        private final int tileWidth;
        private final int tileHeight;
        private final Paint linePaint;
        private final float tileWidthHalf;
        private final float tileHeightHalf;

        DebugTileProvider(@NonNull Context context) {
            displayMetrics = context.getResources().getDisplayMetrics();
            tileWidth = (int) (TILE_SIZE * displayMetrics.density);
            tileHeight = (int) (TILE_SIZE * displayMetrics.density);
            tileWidthHalf = tileWidth / 2f;
            tileHeightHalf = tileHeight / 2f;

            Log.d(TAG, "density: " + displayMetrics.density);
            Log.d(TAG, "tileWidth: " + tileWidth);

            linePaint = new Paint();
            linePaint.setAntiAlias(true);
            linePaint.setColor(Color.BLUE);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(2);

            textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setHinting(Paint.HINTING_ON);
            textPaint.setTextSize(12 * displayMetrics.scaledDensity);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.BLUE);
            fontMetrics = textPaint.getFontMetrics();

            outlinePaint = new Paint(textPaint);
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(2);
        }

        @Override
        public Tile getTile(int x, int y, int zoom) {
            Bitmap bitmap = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            canvas.drawLine(0, 0, tileWidth, 0, linePaint);
            canvas.drawLine(0, 0, 0, tileHeight, linePaint);

            String tileCoordinates = "( " + x + ", " + y + ", " + zoom + " )";
            Path tileCoordinateTextPath = new Path();
            textPaint.getTextPath(tileCoordinates, 0, tileCoordinates.length(),
                    tileWidthHalf, tileHeightHalf, tileCoordinateTextPath);
            canvas.drawPath(tileCoordinateTextPath, outlinePaint);
            canvas.drawPath(tileCoordinateTextPath, textPaint);

            LatLng latLng = latLngFromTileCoordinate(x, y, zoom);
            String longitudeText = String.format(Locale.US, "%+.6f°", latLng.longitude);
            Path longitudeTextPath = new Path();
            textPaint.getTextPath(longitudeText, 0, longitudeText.length(),
                    tileWidthHalf, tileHeight + fontMetrics.top, longitudeTextPath);
            canvas.drawPath(longitudeTextPath, outlinePaint);
            canvas.drawPath(longitudeTextPath, textPaint);

            String latitudeText = String.format(Locale.US, "%+.6f°", latLng.latitude);
            Path latitudeTextPath = new Path();
            textPaint.getTextPath(latitudeText, 0, latitudeText.length(),
                    tileWidthHalf, tileHeight + fontMetrics.top, latitudeTextPath);
            canvas.save();
            canvas.rotate(90, tileWidthHalf, tileHeightHalf);
            canvas.drawPath(latitudeTextPath, outlinePaint);
            canvas.drawPath(latitudeTextPath, textPaint);
            canvas.restore();

            // adjust world coordinates to tile-local coordinates
            canvas.translate(-x * tileWidth, -y * tileWidth);

            PointF prevPointF = null;
            for (LatLng point : points) {
                PointF pointF = pointForLatLng(point.latitude, point.longitude, zoom);
                canvas.drawCircle(pointF.x, pointF.y, 4f * displayMetrics.density, textPaint);
                String pointFText = pointF.toString();
                Path pointFPath = new Path();
                textPaint.getTextPath(pointFText, 0, pointFText.length(),
                        pointF.x, pointF.y, pointFPath);
                canvas.drawPath(pointFPath, outlinePaint);
                canvas.drawPath(pointFPath, textPaint);
                if (prevPointF != null) {
                    canvas.drawLine(prevPointF.x, prevPointF.y, pointF.x, pointF.y,
                            textPaint);
                }
                prevPointF = pointF;
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            return new Tile(tileWidth, tileHeight, byteArrayOutputStream.toByteArray());
        }

        // http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#Tile_numbers_to_lon..2Flat.
        private LatLng latLngFromTileCoordinate(int x, int y, int zoom) {
            double n = Math.pow(2, zoom);
            double lon_deg = x / n * 360.0 - 180.0;
            double lat_rad = Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n)));
            double lat_deg = lat_rad * 180.0 / Math.PI;
            return new LatLng(lat_deg, lon_deg);
        }

        /**
         * Returns world pixel coordinates from a given latitude, longitude, and zoom level
         * https://en.wikipedia.org/wiki/Web_Mercator#Formulas
         */
        private PointF pointForLatLng(double lat, double lng, int zoom) {
            double lng_rad = Math.toRadians(lng);
            double lat_rad = Math.toRadians(lat);
            double n = (tileWidth / 2.0 / Math.PI) * Math.pow(2.0, zoom);
            double x = n * (lng_rad + Math.PI);
            double y = n * (Math.PI - Math.log(Math.tan(Math.PI / 4.0 + lat_rad / 2.0)));
            return new PointF((float) x, (float) y);
        }
    }
}
